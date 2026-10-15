package io.github.alexc.keycloak.events.kafka.config;

import java.util.Collections;
import java.util.Set;

/**
 * Immutable configuration record for the Apache Kafka Enterprise Audit Streamer SPI.
 *
 * @param bootstrapServers        Comma-separated list of Kafka broker endpoints.
 * @param eventsTopicPattern      Topic pattern for user events (supports {realm} placeholder).
 * @param adminEventsTopicPattern Topic pattern for admin events (supports {realm} placeholder).
 * @param partitionStrategy       Partitioning strategy (USER, REALM, NONE).
 * @param acks                    Producer acknowledgment setting (0, 1, all).
 * @param compressionType         Compression algorithm (none, snappy, gzip, zstd, lz4).
 * @param securityProtocol        Kafka security protocol (PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL).
 * @param saslMechanism           SASL mechanism (e.g., PLAIN, SCRAM-SHA-256, SCRAM-SHA-512).
 * @param saslJaasConfig          JAAS configuration string for SASL auth.
 * @param maxBlockMs              Max blocking milliseconds on KafkaProducer.send before buffering/dropping.
 * @param bufferCapacity          Capacity of the in-memory RingBuffer for retrying failed sends.
 * @param includedEventTypes      Set of user event types to dispatch (empty set indicates all events).
 * @param includeAdminEvents      Whether to stream administrative events.
 */
public record KafkaEventListenerConfig(
        String bootstrapServers,
        String eventsTopicPattern,
        String adminEventsTopicPattern,
        PartitionStrategy partitionStrategy,
        String acks,
        String compressionType,
        String securityProtocol,
        String saslMechanism,
        String saslJaasConfig,
        long maxBlockMs,
        int bufferCapacity,
        Set<String> includedEventTypes,
        boolean includeAdminEvents
) {

    public static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
    public static final String DEFAULT_EVENTS_TOPIC = "keycloak.events.{realm}";
    public static final String DEFAULT_ADMIN_EVENTS_TOPIC = "keycloak.admin-events.{realm}";
    public static final PartitionStrategy DEFAULT_PARTITION_STRATEGY = PartitionStrategy.USER;
    public static final String DEFAULT_ACKS = "1";
    public static final String DEFAULT_COMPRESSION = "snappy";
    public static final String DEFAULT_SECURITY_PROTOCOL = "PLAINTEXT";
    public static final String DEFAULT_SASL_MECHANISM = "PLAIN";
    public static final long DEFAULT_MAX_BLOCK_MS = 500L;
    public static final int DEFAULT_BUFFER_CAPACITY = 5000;

    public KafkaEventListenerConfig {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            bootstrapServers = DEFAULT_BOOTSTRAP_SERVERS;
        }
        if (eventsTopicPattern == null || eventsTopicPattern.isBlank()) {
            eventsTopicPattern = DEFAULT_EVENTS_TOPIC;
        }
        if (adminEventsTopicPattern == null || adminEventsTopicPattern.isBlank()) {
            adminEventsTopicPattern = DEFAULT_ADMIN_EVENTS_TOPIC;
        }
        if (partitionStrategy == null) {
            partitionStrategy = DEFAULT_PARTITION_STRATEGY;
        }
        if (acks == null || acks.isBlank()) {
            acks = DEFAULT_ACKS;
        }
        if (compressionType == null || compressionType.isBlank()) {
            compressionType = DEFAULT_COMPRESSION;
        }
        if (securityProtocol == null || securityProtocol.isBlank()) {
            securityProtocol = DEFAULT_SECURITY_PROTOCOL;
        }
        if (saslMechanism == null || saslMechanism.isBlank()) {
            saslMechanism = DEFAULT_SASL_MECHANISM;
        }
        if (maxBlockMs <= 0) {
            maxBlockMs = DEFAULT_MAX_BLOCK_MS;
        }
        if (bufferCapacity <= 0) {
            bufferCapacity = DEFAULT_BUFFER_CAPACITY;
        }
        if (includedEventTypes == null) {
            includedEventTypes = Collections.emptySet();
        }
    }

    /**
     * Determines whether a given user event type should be dispatched.
     *
     * @param eventType uppercase string of the Keycloak event type.
     * @return true if the event should be dispatched.
     */
    public boolean shouldDispatchUserEvent(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return false;
        }
        return includedEventTypes.isEmpty() || includedEventTypes.contains(eventType.trim().toUpperCase());
    }

    /**
     * Determines whether administrative events should be dispatched.
     *
     * @return true if admin events are enabled.
     */
    public boolean shouldDispatchAdminEvent() {
        return includeAdminEvents;
    }
}
