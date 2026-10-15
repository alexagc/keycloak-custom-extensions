package io.github.alexc.keycloak.ageproofing.crypto;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jwk.JWKParser;
import org.keycloak.util.JsonSerialization;

/**
 * Thread-safe resolver and in-memory cache for public verification keys fetched from external
 * JSON Web Key Sets (JWKS) published by trusted identity authorities.
 */
public class PublicTrustProviderResolver {

    private static final Logger LOG = Logger.getLogger(PublicTrustProviderResolver.class);

    private final HttpClient httpClient;
    private final Map<URI, CachedJwks> cache = new ConcurrentHashMap<>();

    private record CachedJwks(
            Map<String, PublicKey> keysByKid,
            PublicKey defaultKey,
            Instant expiresAt
    ) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Creates a resolver using default native Java 21 HttpClient.
     */
    public PublicTrustProviderResolver() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /**
     * Creates a resolver with a custom HttpClient (useful for unit testing).
     *
     * @param httpClient custom HTTP client instance
     */
    public PublicTrustProviderResolver(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Resolves a public key for a given JWKS endpoint and key ID (kid).
     *
     * @param jwksUri         URI of the trusted JWKS endpoint
     * @param kid             optional key identifier (may be null)
     * @param timeoutMs       request timeout in milliseconds
     * @param cacheTtlMinutes cache TTL in minutes
     * @return resolved PublicKey, or null if unresolvable
     */
    public PublicKey resolvePublicKey(URI jwksUri, String kid, int timeoutMs, int cacheTtlMinutes) {
        if (jwksUri == null) {
            LOG.warn("Cannot resolve public key: jwksUri is null");
            return null;
        }

        CachedJwks cached = cache.get(jwksUri);
        if (cached != null && !cached.isExpired()) {
            PublicKey key = findKey(cached, kid);
            if (key != null) {
                return key;
            }
        }

        // Cache miss or expired: refresh from endpoint
        try {
            CachedJwks refreshed = fetchJwks(jwksUri, timeoutMs, cacheTtlMinutes);
            cache.put(jwksUri, refreshed);
            return findKey(refreshed, kid);
        } catch (Exception e) {
            LOG.errorv("Failed to fetch or parse JWKS from {0}: {1}", jwksUri, e.getMessage());
            // If expired cache exists, return as stale fallback in degraded mode
            if (cached != null) {
                LOG.warnv("Falling back to stale cached JWKS for {0}", jwksUri);
                return findKey(cached, kid);
            }
            return null;
        }
    }

    private PublicKey findKey(CachedJwks cached, String kid) {
        if (kid != null && !kid.isBlank()) {
            return cached.keysByKid().get(kid);
        }
        return cached.defaultKey();
    }

    private CachedJwks fetchJwks(URI uri, int timeoutMs, int cacheTtlMinutes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json, application/jwk-set+json")
                .header("User-Agent", "Keycloak-Age-Proofing-SPI/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP JWKS request failed with status code " + response.statusCode());
        }

        String body = response.body();
        JSONWebKeySet keySet = JsonSerialization.readValue(body, JSONWebKeySet.class);
        if (keySet == null || keySet.getKeys() == null || keySet.getKeys().length == 0) {
            throw new IOException("Empty or invalid JWKS response payload from " + uri);
        }

        Map<String, PublicKey> keys = new ConcurrentHashMap<>();
        PublicKey firstKey = null;

        for (JWK jwk : keySet.getKeys()) {
            try {
                PublicKey pk = JWKParser.create(jwk).toPublicKey();
                if (pk != null) {
                    if (jwk.getKeyId() != null) {
                        keys.put(jwk.getKeyId(), pk);
                    }
                    if (firstKey == null) {
                        firstKey = pk;
                    }
                }
            } catch (Exception e) {
                LOG.warnv("Skipping unparseable JWK key id {0}: {1}", jwk.getKeyId(), e.getMessage());
            }
        }

        if (keys.isEmpty() && firstKey == null) {
            throw new IOException("No valid RSA or EC public keys could be parsed from JWKS at " + uri);
        }

        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(cacheTtlMinutes));
        return new CachedJwks(Map.copyOf(keys), firstKey, expiresAt);
    }

    /**
     * Clears all cached keys (primarily for testing purposes).
     */
    public void clearCache() {
        cache.clear();
    }
}
