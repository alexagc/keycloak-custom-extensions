package io.github.alexc.keycloak.events.kafka.producer;

import io.github.alexc.keycloak.events.kafka.config.KafkaEventListenerConfig;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe singleton managing the Apache Kafka producer connection pool,
 * asynchronous dispatch, and background retry buffer draining.
 */
public class KafkaProducerManager {

    private static final Logger LOG = Logger.getLogger(KafkaProducerManager.class);

    private final KafkaEventListenerConfig config;
    private final Producer<String, String> producer;
    private final BoundedRetryRingBuffer retryBuffer;
    private final ScheduledExecutorService drainScheduler;
    private volatile boolean closed = false;

    /**
     * Production constructor initializing the native KafkaProducer.
     *
     * @param config configuration parameters
     */
    public KafkaProducerManager(KafkaEventListenerConfig config) {
        this.config = config;
        this.retryBuffer = new BoundedRetryRingBuffer(config.bufferCapacity());
        this.producer = createKafkaProducer(config);
        this.drainScheduler = startDrainScheduler();
    }

    /**
     * Testing constructor allowing injection of mock producer and buffer.
     *
     * @param config      configuration parameters
     * @param producer    mock or custom producer
     * @param retryBuffer custom retry buffer
     */
    public KafkaProducerManager(KafkaEventListenerConfig config,
                                Producer<String, String> producer,
                                BoundedRetryRingBuffer retryBuffer) {
        this.config = config;
        this.producer = producer;
        this.retryBuffer = retryBuffer != null ? retryBuffer : new BoundedRetryRingBuffer(config.bufferCapacity());
        this.drainScheduler = null;
    }

    /**
     * Dispatches an audit record asynchronously to the specified Kafka topic.
     *
     * @param topic   target Kafka topic
     * @param key     message key (can be null)
     * @param payload serialized CloudEvents JSON payload
     */
    public void sendAsync(String topic, String key, String payload) {
        if (closed || producer == null) {
            LOG.warnv("KafkaProducerManager is closed or uninitialized; queuing message to retry buffer for topic: {0}", topic);
            retryBuffer.enqueue(topic, key, payload);
            return;
        }

        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    LOG.warnv("Asynchronous Kafka send failed for topic {0} (key: {1}): {2}. Offloading to retry buffer.",
                            topic, key, exception.getMessage());
                    retryBuffer.enqueue(topic, key, payload);
                } else if (LOG.isTraceEnabled()) {
                    LOG.tracev("Event published to Kafka: topic={0}, partition={1}, offset={2}",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
        } catch (Exception e) {
            LOG.warnv("Immediate exception during Kafka send for topic {0}: {1}. Offloading to retry buffer.",
                    topic, e.getMessage());
            retryBuffer.enqueue(topic, key, payload);
        }
    }

    private Producer<String, String> createKafkaProducer(KafkaEventListenerConfig config) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, config.acks());
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, config.compressionType());
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, config.maxBlockMs());

        // Security Protocol
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, config.securityProtocol());

        if (config.securityProtocol().toUpperCase().contains("SASL")) {
            props.put(SaslConfigs.SASL_MECHANISM, config.saslMechanism());
            if (config.saslJaasConfig() != null && !config.saslJaasConfig().isBlank()) {
                props.put(SaslConfigs.SASL_JAAS_CONFIG, config.saslJaasConfig());
            }
        }

        try {
            return new KafkaProducer<>(props);
        } catch (Exception e) {
            LOG.errorv(e, "Failed to initialize KafkaProducer with bootstrap-servers: {0}", config.bootstrapServers());
            return null;
        }
    }

    private ScheduledExecutorService startDrainScheduler() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "keycloak-kafka-drain-daemon");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleWithFixedDelay(() -> {
            if (!closed && producer != null && retryBuffer.size() > 0) {
                int drained = retryBuffer.drain(producer, 50);
                if (drained > 0 && LOG.isDebugEnabled()) {
                    LOG.debugv("Successfully drained {0} buffered audit events to Kafka. Remaining: {1}",
                            drained, retryBuffer.size());
                }
            }
        }, 5, 5, TimeUnit.SECONDS);

        return scheduler;
    }

    public BoundedRetryRingBuffer getRetryBuffer() {
        return retryBuffer;
    }

    public Producer<String, String> getProducer() {
        return producer;
    }

    /**
     * Gracefully flushes and closes producer resources.
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        if (drainScheduler != null) {
            drainScheduler.shutdownNow();
        }

        if (producer != null) {
            try {
                producer.flush();
                producer.close(Duration.ofSeconds(3));
                LOG.info("KafkaProducerManager gracefully closed");
            } catch (Exception e) {
                LOG.warnv("Exception while closing KafkaProducer: {0}", e.getMessage());
            }
        }
    }
}
