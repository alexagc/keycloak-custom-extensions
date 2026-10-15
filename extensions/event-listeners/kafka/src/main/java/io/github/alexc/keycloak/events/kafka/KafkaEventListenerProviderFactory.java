package io.github.alexc.keycloak.events.kafka;

import com.google.auto.service.AutoService;
import io.github.alexc.keycloak.events.kafka.config.KafkaEventListenerConfig;
import io.github.alexc.keycloak.events.kafka.config.PartitionStrategy;
import io.github.alexc.keycloak.events.kafka.producer.KafkaProducerManager;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Singleton factory responsible for configuration parsing, connection pool initialization,
 * and lifecycle management for the {@link KafkaEventListenerProvider}.
 */
@AutoService(EventListenerProviderFactory.class)
public class KafkaEventListenerProviderFactory implements EventListenerProviderFactory {

    private static final Logger LOG = Logger.getLogger(KafkaEventListenerProviderFactory.class);

    public static final String PROVIDER_ID = "kafka-audit-streamer";

    public static final String CONFIG_BOOTSTRAP_SERVERS = "bootstrap-servers";
    public static final String CONFIG_EVENTS_TOPIC = "events-topic";
    public static final String CONFIG_ADMIN_EVENTS_TOPIC = "admin-events-topic";
    public static final String CONFIG_PARTITION_STRATEGY = "partition-strategy";
    public static final String CONFIG_ACKS = "acks";
    public static final String CONFIG_COMPRESSION = "compression-type";
    public static final String CONFIG_SECURITY_PROTOCOL = "security-protocol";
    public static final String CONFIG_SASL_MECHANISM = "sasl-mechanism";
    public static final String CONFIG_SASL_JAAS_CONFIG = "sasl-jaas-config";
    public static final String CONFIG_MAX_BLOCK_MS = "max-block-ms";
    public static final String CONFIG_BUFFER_CAPACITY = "buffer-capacity";
    public static final String CONFIG_INCLUDE_EVENTS = "include-events";
    public static final String CONFIG_INCLUDE_ADMIN_EVENTS = "include-admin-events";

    private KafkaEventListenerConfig config;
    private KafkaProducerManager producerManager;

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new KafkaEventListenerProvider(session, producerManager, config);
    }

    @Override
    public void init(Config.Scope configScope) {
        String bootstrapServers = resolveProperty(configScope, CONFIG_BOOTSTRAP_SERVERS, "KC_SPI_EVENT_LISTENER_KAFKA_BOOTSTRAP_SERVERS");
        String eventsTopic = resolveProperty(configScope, CONFIG_EVENTS_TOPIC, "KC_SPI_EVENT_LISTENER_KAFKA_EVENTS_TOPIC");
        String adminTopic = resolveProperty(configScope, CONFIG_ADMIN_EVENTS_TOPIC, "KC_SPI_EVENT_LISTENER_KAFKA_ADMIN_TOPIC");
        String partitionStrategyStr = resolveProperty(configScope, CONFIG_PARTITION_STRATEGY, "KC_SPI_EVENT_LISTENER_KAFKA_PARTITION_STRATEGY");
        String acks = resolveProperty(configScope, CONFIG_ACKS, "KC_SPI_EVENT_LISTENER_KAFKA_ACKS");
        String compression = resolveProperty(configScope, CONFIG_COMPRESSION, "KC_SPI_EVENT_LISTENER_KAFKA_COMPRESSION");
        String securityProtocol = resolveProperty(configScope, CONFIG_SECURITY_PROTOCOL, "KC_SPI_EVENT_LISTENER_KAFKA_SECURITY_PROTOCOL");
        String saslMechanism = resolveProperty(configScope, CONFIG_SASL_MECHANISM, "KC_SPI_EVENT_LISTENER_KAFKA_SASL_MECHANISM");
        String saslJaasConfig = resolveProperty(configScope, CONFIG_SASL_JAAS_CONFIG, "KC_SPI_EVENT_LISTENER_KAFKA_SASL_JAAS_CONFIG");

        long maxBlockMs = parseLong(resolveProperty(configScope, CONFIG_MAX_BLOCK_MS, "KC_SPI_EVENT_LISTENER_KAFKA_MAX_BLOCK_MS"),
                KafkaEventListenerConfig.DEFAULT_MAX_BLOCK_MS);
        int bufferCapacity = parseInt(resolveProperty(configScope, CONFIG_BUFFER_CAPACITY, "KC_SPI_EVENT_LISTENER_KAFKA_BUFFER_CAPACITY"),
                KafkaEventListenerConfig.DEFAULT_BUFFER_CAPACITY);

        String includedEventsStr = resolveProperty(configScope, CONFIG_INCLUDE_EVENTS, "KC_SPI_EVENT_LISTENER_KAFKA_INCLUDE_EVENTS");
        Set<String> includedEvents = Collections.emptySet();
        if (includedEventsStr != null && !includedEventsStr.isBlank() && !includedEventsStr.trim().equals("*")) {
            includedEvents = Arrays.stream(includedEventsStr.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }

        String adminEventsStr = resolveProperty(configScope, CONFIG_INCLUDE_ADMIN_EVENTS, "KC_SPI_EVENT_LISTENER_KAFKA_INCLUDE_ADMIN_EVENTS");
        boolean includeAdminEvents = adminEventsStr == null || Boolean.parseBoolean(adminEventsStr.trim());

        this.config = new KafkaEventListenerConfig(
                bootstrapServers,
                eventsTopic,
                adminTopic,
                PartitionStrategy.fromString(partitionStrategyStr),
                acks,
                compression,
                securityProtocol,
                saslMechanism,
                saslJaasConfig,
                maxBlockMs,
                bufferCapacity,
                includedEvents,
                includeAdminEvents
        );

        LOG.infov("Kafka Audit Streamer SPI initialized: bootstrap-servers={0}, userTopicPattern={1}, adminTopicPattern={2}, partitionStrategy={3}",
                this.config.bootstrapServers(), this.config.eventsTopicPattern(), this.config.adminEventsTopicPattern(), this.config.partitionStrategy());

        if (this.producerManager == null) {
            this.producerManager = new KafkaProducerManager(this.config);
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization required
    }

    @Override
    public void close() {
        if (producerManager != null) {
            producerManager.close();
            producerManager = null;
        }
    }

    public KafkaEventListenerConfig getConfig() {
        return config;
    }

    public KafkaProducerManager getProducerManager() {
        return producerManager;
    }

    // Visible for testing
    void setProducerManager(KafkaProducerManager producerManager) {
        this.producerManager = producerManager;
    }

    private String resolveProperty(Config.Scope scope, String propertyName, String envVarName) {
        if (scope != null) {
            String val = scope.get(propertyName);
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        String envVal = System.getenv(envVarName);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        String sysVal = System.getProperty(envVarName);
        if (sysVal != null && !sysVal.isBlank()) {
            return sysVal.trim();
        }
        return null;
    }

    private long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LOG.warnv("Invalid long configuration value ''{0}'', falling back to default: {1}", value, defaultValue);
            return defaultValue;
        }
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOG.warnv("Invalid integer configuration value ''{0}'', falling back to default: {1}", value, defaultValue);
            return defaultValue;
        }
    }
}
