package io.github.alexc.keycloak.events.kafka.config;

import java.util.Locale;

/**
 * Strategy determining how Kafka message keys are generated for partition routing.
 */
public enum PartitionStrategy {

    /**
     * Partitions messages by user ID (with fallback to realm or session ID).
     * Guarantees strict chronological event ordering for any specific user.
     */
    USER,

    /**
     * Partitions messages by realm ID.
     * Useful when multiple realms share a static topic and ordering within each realm is desired.
     */
    REALM,

    /**
     * No message key is assigned (round-robin / uniform distribution).
     * Maximizes raw write throughput across all partitions when downstream SIEM
     * does not require strict per-user sequence preservation.
     */
    NONE;

    /**
     * Parses a string into a {@link PartitionStrategy}, defaulting to {@link #USER} if unrecognized.
     *
     * @param value configuration string
     * @return resolved {@link PartitionStrategy}
     */
    public static PartitionStrategy fromString(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        try {
            return PartitionStrategy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return USER;
        }
    }
}
