package io.github.alexc.keycloak.events.kafka.producer;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jboss.logging.Logger;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resilient, thread-safe, bounded in-memory buffer that stores audit records
 * during Kafka broker outages or latency spikes, draining them asynchronously
 * once connectivity is re-established.
 */
public class BoundedRetryRingBuffer {

    private static final Logger LOG = Logger.getLogger(BoundedRetryRingBuffer.class);

    /**
     * Immutable item stored in the buffer.
     */
    public record BufferedRecord(String topic, String key, String payload, long timestamp) {}

    private final int capacity;
    private final ConcurrentLinkedQueue<BufferedRecord> queue;
    private final AtomicInteger currentSize;
    private final AtomicInteger droppedCount;

    public BoundedRetryRingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.queue = new ConcurrentLinkedQueue<>();
        this.currentSize = new AtomicInteger(0);
        this.droppedCount = new AtomicInteger(0);
    }

    /**
     * Enqueues a record. If the buffer is full, the oldest record is dropped (FIFO)
     * to prevent unbounded memory growth while keeping the newest audit events.
     *
     * @param topic   Kafka topic
     * @param key     Message key
     * @param payload CloudEvents JSON payload
     * @return true if enqueued successfully
     */
    public boolean enqueue(String topic, String key, String payload) {
        if (topic == null || payload == null) {
            return false;
        }

        while (currentSize.get() >= capacity) {
            BufferedRecord dropped = queue.poll();
            if (dropped != null) {
                currentSize.decrementAndGet();
                int drops = droppedCount.incrementAndGet();
                if (drops % 100 == 1) {
                    LOG.warnv("Kafka retry buffer capacity reached ({0}). Dropped oldest audit records (total dropped: {1})",
                            capacity, drops);
                }
            }
        }

        BufferedRecord record = new BufferedRecord(topic, key, payload, System.currentTimeMillis());
        queue.offer(record);
        currentSize.incrementAndGet();
        return true;
    }

    /**
     * Drains pending buffered records by attempting to send them via the provided producer.
     *
     * @param producer active Kafka producer
     * @param batchSize maximum number of records to drain in one pass
     * @return count of successfully dispatched records
     */
    public int drain(Producer<String, String> producer, int batchSize) {
        if (producer == null || queue.isEmpty() || batchSize <= 0) {
            return 0;
        }

        int dispatched = 0;
        for (int i = 0; i < batchSize && !queue.isEmpty(); i++) {
            BufferedRecord record = queue.poll();
            if (record == null) {
                break;
            }
            currentSize.decrementAndGet();

            try {
                producer.send(new ProducerRecord<>(record.topic(), record.key(), record.payload()),
                        (metadata, exception) -> {
                            if (exception != null) {
                                LOG.debugv("Failed redelivery of buffered record to {0}: {1}",
                                        record.topic(), exception.getMessage());
                                // Re-enqueue on failure if still under capacity
                                enqueue(record.topic(), record.key(), record.payload());
                            }
                        });
                dispatched++;
            } catch (Exception e) {
                LOG.warnv("Error during buffered record drain to {0}: {1}", record.topic(), e.getMessage());
                enqueue(record.topic(), record.key(), record.payload());
                break; // Stop draining this cycle on broker communication error
            }
        }

        return dispatched;
    }

    public int size() {
        return currentSize.get();
    }

    public int getDroppedCount() {
        return droppedCount.get();
    }

    public void clear() {
        queue.clear();
        currentSize.set(0);
    }
}
