package io.github.alexc.keycloak.passwords.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * High-performance parser for HaveIBeenPwned k-Anonymity range query responses.
 * Parses lines formatted as {@code <35-char-suffix>:<count>}.
 */
public final class HibpResponseParser {

    private HibpResponseParser() {
        // Utility class
    }

    /**
     * Parses the HIBP range response body into an unmodifiable map of uppercase suffix to count.
     *
     * @param responseBody raw HTTP response body from HIBP range query
     * @return unmodifiable map of suffix to breach count
     */
    public static Map<String, Integer> parse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> map = new HashMap<>();
        responseBody.lines().forEach(line -> {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0 && colonIndex < line.length() - 1) {
                String suffix = line.substring(0, colonIndex).trim().toUpperCase(Locale.ROOT);
                String countStr = line.substring(colonIndex + 1).trim();
                try {
                    int count = Integer.parseInt(countStr);
                    map.put(suffix, count);
                } catch (NumberFormatException ignored) {
                    // Ignore malformed line gracefully
                }
            }
        });

        return Collections.unmodifiableMap(map);
    }

    /**
     * Finds the breach count for a specific suffix directly within the response body.
     *
     * @param responseBody raw HTTP response body
     * @param suffix       35-character uppercase hash suffix
     * @return breach count if found, or 0
     */
    public static int findCount(String responseBody, String suffix) {
        if (responseBody == null || suffix == null || suffix.isBlank()) {
            return 0;
        }
        Map<String, Integer> map = parse(responseBody);
        return map.getOrDefault(suffix.toUpperCase(Locale.ROOT), 0);
    }
}
