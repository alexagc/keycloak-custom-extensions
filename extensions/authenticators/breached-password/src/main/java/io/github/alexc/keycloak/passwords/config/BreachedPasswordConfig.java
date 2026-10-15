package io.github.alexc.keycloak.passwords.config;

import java.net.URI;
import org.keycloak.Config;

/**
 * Immutable configuration record for the Breached Password Detector SPI.
 *
 * @param mode             enforcement mode: "strict" (block or require password update) or "audit-only"
 * @param breachThreshold  minimum breach count to trigger action (default: 1)
 * @param apiTimeoutMs     maximum HTTP connect and read timeout in milliseconds (default: 1500)
 * @param failOpen         whether to allow access if the external HIBP API fails (default: true)
 * @param apiUrl           base URL for the HIBP k-Anonymity range API
 * @param cacheMaxEntries  maximum number of range prefixes cached in memory (default: 2048)
 * @param cacheTtlMinutes  time to live in minutes for cached range entries (default: 360 = 6 hours)
 */
public record BreachedPasswordConfig(
        String mode,
        int breachThreshold,
        int apiTimeoutMs,
        boolean failOpen,
        URI apiUrl,
        int cacheMaxEntries,
        int cacheTtlMinutes
) {

    public static final String DEFAULT_MODE = "strict";
    public static final int DEFAULT_THRESHOLD = 1;
    public static final int DEFAULT_TIMEOUT_MS = 1500;
    public static final boolean DEFAULT_FAIL_OPEN = true;
    public static final String DEFAULT_API_URL = "https://api.pwnedpasswords.com/range/";
    public static final int DEFAULT_CACHE_MAX_ENTRIES = 2048;
    public static final int DEFAULT_CACHE_TTL_MINUTES = 360;

    /**
     * Resolves configuration from Keycloak's Config.Scope and system environment variables.
     *
     * @param scope Keycloak configuration scope (may be null)
     * @return populated BreachedPasswordConfig instance
     */
    public static BreachedPasswordConfig fromScope(Config.Scope scope) {
        String mode = resolveProperty(scope, "mode", "KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_MODE", DEFAULT_MODE);
        int threshold = parseInt(resolveProperty(scope, "breach-threshold", "KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_THRESHOLD", null), DEFAULT_THRESHOLD);
        int timeoutMs = parseInt(resolveProperty(scope, "api-timeout-ms", "KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_TIMEOUT_MS", null), DEFAULT_TIMEOUT_MS);
        boolean failOpen = parseBoolean(resolveProperty(scope, "fail-open", "KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_FAIL_OPEN", null), DEFAULT_FAIL_OPEN);
        String rawApiUrl = resolveProperty(scope, "api-url", "KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_API_URL", DEFAULT_API_URL);
        int cacheMaxEntries = parseInt(resolveProperty(scope, "cache-max-entries", "KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_CACHE_MAX_ENTRIES", null), DEFAULT_CACHE_MAX_ENTRIES);
        int cacheTtlMinutes = parseInt(resolveProperty(scope, "cache-ttl-minutes", "KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_CACHE_TTL_MINUTES", null), DEFAULT_CACHE_TTL_MINUTES);

        if (!rawApiUrl.endsWith("/")) {
            rawApiUrl = rawApiUrl + "/";
        }
        URI parsedUri = URI.create(rawApiUrl);

        return new BreachedPasswordConfig(
                mode.toLowerCase().trim(),
                Math.max(1, threshold),
                Math.max(100, timeoutMs),
                failOpen,
                parsedUri,
                Math.max(10, cacheMaxEntries),
                Math.max(1, cacheTtlMinutes)
        );
    }

    /**
     * Checks if the detector is running in strict mode.
     *
     * @return true if strict mode is active
     */
    public boolean isStrict() {
        return "strict".equalsIgnoreCase(mode);
    }

    private static String resolveProperty(Config.Scope scope, String key, String envVar, String defaultValue) {
        if (scope != null) {
            String val = scope.get(key);
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        String envVal = System.getenv(envVar);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        return defaultValue;
    }

    private static int parseInt(String val, int defaultValue) {
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean parseBoolean(String val, boolean defaultValue) {
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(val.trim());
    }
}
