package io.github.alexc.keycloak.events.kafka.mapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility for masking sensitive credentials, secrets, and authentication tokens
 * from event details and JSON representations prior to Kafka transmission.
 */
public final class CredentialSanitizer {

    public static final String MASK = "******";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "password-confirm",
            "secret",
            "client_secret",
            "token",
            "code",
            "refresh_token",
            "access_token",
            "id_token",
            "credential",
            "private_key",
            "assertion",
            "totp"
    );

    private static final Pattern SENSITIVE_JSON_PATTERN = Pattern.compile(
            "(?i)\"(password|secret|client_secret|code|token|refresh_token|credential|private_key|totp)\"\\s*:\\s*\"[^\"]*\""
    );

    private CredentialSanitizer() {
        // Private constructor for utility class
    }

    /**
     * Sanitizes an event detail map by masking sensitive parameter values.
     *
     * @param details raw event details map
     * @return a new map containing sanitized entries
     */
    public static Map<String, String> sanitizeDetails(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : details.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (isSensitiveKey(key)) {
                sanitized.put(key, MASK);
            } else {
                sanitized.put(key, value);
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }

    /**
     * Sanitizes a JSON representation string from an admin event.
     *
     * @param representation raw JSON string
     * @return sanitized string with masked secrets
     */
    public static String sanitizeRepresentation(String representation) {
        if (representation == null || representation.isBlank()) {
            return representation;
        }

        return SENSITIVE_JSON_PATTERN.matcher(representation)
                .replaceAll("\"$1\":\"" + MASK + "\"");
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (String sensitive : SENSITIVE_KEYS) {
            if (normalized.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }
}
