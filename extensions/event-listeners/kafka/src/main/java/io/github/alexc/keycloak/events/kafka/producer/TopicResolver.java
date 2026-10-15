package io.github.alexc.keycloak.events.kafka.producer;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility for resolving Kafka topic names with dynamic realm pattern interpolation.
 */
public final class TopicResolver {

    public static final String REALM_PLACEHOLDER = "{realm}";
    private static final Pattern DISALLOWED_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");
    private static final int MAX_TOPIC_LENGTH = 249;

    private TopicResolver() {
        // Private constructor for utility class
    }

    /**
     * Resolves a topic pattern by substituting the `{realm}` placeholder with a sanitized realm name.
     *
     * @param pattern         topic pattern (e.g. "keycloak.events.{realm}")
     * @param realmNameOrId   the realm identifier or name
     * @return sanitized, resolved Kafka topic name
     */
    public static String resolve(String pattern, String realmNameOrId) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("Topic pattern cannot be null or blank");
        }

        if (!pattern.contains(REALM_PLACEHOLDER)) {
            return pattern.trim();
        }

        String sanitizedRealm = sanitizeRealm(realmNameOrId);
        String resolved = pattern.replace(REALM_PLACEHOLDER, sanitizedRealm).trim();

        if (resolved.length() > MAX_TOPIC_LENGTH) {
            resolved = resolved.substring(0, MAX_TOPIC_LENGTH);
        }

        return resolved;
    }

    /**
     * Sanitizes a realm string for safe use in Kafka topic names.
     *
     * @param realm the raw realm name or ID
     * @return sanitized realm string conforming to Kafka topic naming rules
     */
    public static String sanitizeRealm(String realm) {
        if (realm == null || realm.isBlank()) {
            return "default";
        }

        String cleaned = DISALLOWED_CHARS.matcher(realm.trim()).replaceAll("-")
                .toLowerCase(Locale.ROOT);

        // Disallow leading with dots or reserved Kafka characters
        while (cleaned.startsWith(".") || cleaned.startsWith("-")) {
            cleaned = cleaned.substring(1);
        }

        return cleaned.isEmpty() ? "default" : cleaned;
    }
}
