package io.github.alexc.keycloak.ageproofing.model;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.Config;
import org.keycloak.models.AuthenticatorConfigModel;

/**
 * Immutable configuration record for the Age &amp; Identity Proofing SPI.
 *
 * @param minAge          minimum required legal age (default: 18)
 * @param jwksUrl         URL to the public JSON Web Key Set (JWKS) of the trusted issuer/gateway
 * @param validityDays    number of days the verification remains valid before reverification (default: 365)
 * @param allowedIssuers  set of trusted issuer identifiers (if empty, allows any issuer valid under JWKS)
 * @param mode            interaction mode: "hybrid", "qr-wallet", or "redirect" (default: "hybrid")
 * @param failOpen        whether to allow login if the JWKS endpoint is unreachable (default: false)
 * @param eidGatewayUrl   optional direct URL for redirecting to a public eID portal (e.g. Cl@ve, FranceConnect)
 * @param apiTimeoutMs    HTTP connect and read timeout in milliseconds (default: 3000)
 * @param cacheTtlMinutes TTL for cached JWKS public keys in minutes (default: 60)
 */
public record AgeVerificationConfig(
        int minAge,
        URI jwksUrl,
        int validityDays,
        Set<String> allowedIssuers,
        String mode,
        boolean failOpen,
        URI eidGatewayUrl,
        int apiTimeoutMs,
        int cacheTtlMinutes
) {

    public static final int DEFAULT_MIN_AGE = 18;
    public static final int DEFAULT_VALIDITY_DAYS = 365;
    public static final String DEFAULT_MODE = "hybrid";
    public static final boolean DEFAULT_FAIL_OPEN = false;
    public static final int DEFAULT_TIMEOUT_MS = 3000;
    public static final int DEFAULT_CACHE_TTL_MINUTES = 60;

    /**
     * Resolves configuration from Keycloak's global Config.Scope and system environment variables.
     *
     * @param scope Keycloak global configuration scope (may be null)
     * @return populated AgeVerificationConfig instance
     */
    public static AgeVerificationConfig fromScope(Config.Scope scope) {
        int minAge = parseInt(resolveProperty(scope, "min-age", "KC_SPI_AUTHENTICATOR_AGE_PROOFING_MIN_AGE", null), DEFAULT_MIN_AGE);
        int validityDays = parseInt(resolveProperty(scope, "validity-days", "KC_SPI_AUTHENTICATOR_AGE_PROOFING_VALIDITY_DAYS", null), DEFAULT_VALIDITY_DAYS);
        String rawJwksUrl = resolveProperty(scope, "jwks-url", "KC_SPI_AUTHENTICATOR_AGE_PROOFING_JWKS_URL", null);
        String rawIssuers = resolveProperty(scope, "allowed-issuers", "KC_SPI_AUTHENTICATOR_AGE_PROOFING_ALLOWED_ISSUERS", null);
        String mode = resolveProperty(scope, "mode", "KC_SPI_AUTHENTICATOR_AGE_PROOFING_MODE", DEFAULT_MODE);
        boolean failOpen = parseBoolean(resolveProperty(scope, "fail-open", "KC_SPI_AUTHENTICATOR_AGE_PROOFING_FAIL_OPEN", null), DEFAULT_FAIL_OPEN);
        String rawEidGatewayUrl = resolveProperty(scope, "eid-gateway-url", "KC_SPI_AUTHENTICATOR_AGE_PROOFING_EID_GATEWAY_URL", null);
        int timeoutMs = parseInt(resolveProperty(scope, "api-timeout-ms", "KC_SPI_AUTHENTICATOR_AGE_PROOFING_TIMEOUT_MS", null), DEFAULT_TIMEOUT_MS);
        int cacheTtlMinutes = parseInt(resolveProperty(scope, "cache-ttl-minutes", "KC_SPI_AUTHENTICATOR_AGE_PROOFING_CACHE_TTL_MINUTES", null), DEFAULT_CACHE_TTL_MINUTES);

        URI jwksUri = (rawJwksUrl != null && !rawJwksUrl.isBlank()) ? URI.create(rawJwksUrl.trim()) : null;
        URI eidUri = (rawEidGatewayUrl != null && !rawEidGatewayUrl.isBlank()) ? URI.create(rawEidGatewayUrl.trim()) : null;
        Set<String> issuers = parseIssuers(rawIssuers);

        return new AgeVerificationConfig(
                Math.max(1, minAge),
                jwksUri,
                Math.max(1, validityDays),
                issuers,
                mode.toLowerCase().trim(),
                failOpen,
                eidUri,
                Math.max(500, timeoutMs),
                Math.max(1, cacheTtlMinutes)
        );
    }

    /**
     * Overlays realm execution-level configuration onto the base configuration.
     *
     * @param configModel realm-level execution configuration (may be null)
     * @return customized AgeVerificationConfig instance
     */
    public AgeVerificationConfig withExecutionConfig(AuthenticatorConfigModel configModel) {
        if (configModel == null || configModel.getConfig() == null || configModel.getConfig().isEmpty()) {
            return this;
        }
        Map<String, String> cfg = configModel.getConfig();

        int effectiveMinAge = parseInt(cfg.get("min-age"), this.minAge);
        int effectiveValidityDays = parseInt(cfg.get("validity-days"), this.validityDays);
        String rawJwks = cfg.get("jwks-url");
        URI effectiveJwks = (rawJwks != null && !rawJwks.isBlank()) ? URI.create(rawJwks.trim()) : this.jwksUrl;

        String rawIssuers = cfg.get("allowed-issuers");
        Set<String> effectiveIssuers = (rawIssuers != null && !rawIssuers.isBlank()) ? parseIssuers(rawIssuers) : this.allowedIssuers;

        String effectiveMode = cfg.getOrDefault("mode", this.mode);
        boolean effectiveFailOpen = cfg.containsKey("fail-open") ? Boolean.parseBoolean(cfg.get("fail-open")) : this.failOpen;

        String rawEid = cfg.get("eid-gateway-url");
        URI effectiveEid = (rawEid != null && !rawEid.isBlank()) ? URI.create(rawEid.trim()) : this.eidGatewayUrl;

        int effectiveTimeout = parseInt(cfg.get("api-timeout-ms"), this.apiTimeoutMs);
        int effectiveTtl = parseInt(cfg.get("cache-ttl-minutes"), this.cacheTtlMinutes);

        return new AgeVerificationConfig(
                effectiveMinAge,
                effectiveJwks,
                effectiveValidityDays,
                effectiveIssuers,
                effectiveMode.toLowerCase().trim(),
                effectiveFailOpen,
                effectiveEid,
                effectiveTimeout,
                effectiveTtl
        );
    }

    private static Set<String> parseIssuers(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
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
