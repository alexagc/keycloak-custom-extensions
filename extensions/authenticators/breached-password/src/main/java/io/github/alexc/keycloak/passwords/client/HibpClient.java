package io.github.alexc.keycloak.passwords.client;

import io.github.alexc.keycloak.passwords.config.BreachedPasswordConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;

/**
 * Native Java 21 asynchronous client for HaveIBeenPwned k-Anonymity range queries.
 */
public class HibpClient {

    private static final Logger LOG = Logger.getLogger(HibpClient.class);
    private static final String USER_AGENT = "Keycloak-Breached-Password-Extension/1.0.0";
    private static final String HEADER_PADDING = "Add-Padding";

    private final HttpClient httpClient;
    private final BreachedPasswordConfig config;
    private final HibpPrefixCache cache;

    /**
     * Initializes the HIBP client with shared dependencies.
     *
     * @param httpClient native Java 21 HttpClient singleton
     * @param config     extension configuration record
     * @param cache      in-memory prefix LRU cache
     */
    public HibpClient(HttpClient httpClient, BreachedPasswordConfig config, HibpPrefixCache cache) {
        this.httpClient = httpClient;
        this.config = config;
        this.cache = cache;
    }

    /**
     * Checks if a password is breached asynchronously using k-Anonymity.
     *
     * @param password plaintext password
     * @return CompletableFuture with BreachCheckResult
     */
    public CompletableFuture<BreachCheckResult> checkPasswordAsync(String password) {
        if (password == null || password.isEmpty()) {
            return CompletableFuture.completedFuture(BreachCheckResult.clean());
        }

        String fullHash = sha1Hex(password);
        String prefix = fullHash.substring(0, 5);
        String suffix = fullHash.substring(5);

        // Check local LRU cache first
        Map<String, Integer> cached = cache.get(prefix);
        if (cached != null) {
            int count = cached.getOrDefault(suffix, 0);
            if (count >= config.breachThreshold()) {
                return CompletableFuture.completedFuture(BreachCheckResult.breached(count));
            }
            return CompletableFuture.completedFuture(BreachCheckResult.clean());
        }

        URI targetUri = URI.create(config.apiUrl().toString() + prefix);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(targetUri)
                .header(HEADER_PADDING, "true")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMillis(config.apiTimeoutMs()))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int statusCode = response.statusCode();
                    if (statusCode == 200) {
                        Map<String, Integer> map = HibpResponseParser.parse(response.body());
                        cache.put(prefix, map);
                        int count = map.getOrDefault(suffix, 0);
                        if (count >= config.breachThreshold()) {
                            return BreachCheckResult.breached(count);
                        }
                        return BreachCheckResult.clean();
                    } else if (statusCode == 404) {
                        cache.put(prefix, Collections.emptyMap());
                        return BreachCheckResult.clean();
                    } else {
                        LOG.warnv("HIBP range API returned unexpected HTTP status {0} for prefix {1}", statusCode, prefix);
                        if (config.failOpen()) {
                            return BreachCheckResult.degradedFailOpen();
                        }
                        return BreachCheckResult.breached(1);
                    }
                })
                .exceptionally(throwable -> {
                    LOG.warnv("HIBP range API request failed or timed out for prefix {0}: {1}", prefix, throwable.getMessage());
                    if (config.failOpen()) {
                        return BreachCheckResult.degradedFailOpen();
                    }
                    throw new RuntimeException("HIBP range API unavailable and fail-open=false", throwable);
                });
    }

    /**
     * Synchronous blocking check for password breach, respecting configured timeout.
     *
     * @param password plaintext password
     * @return BreachCheckResult
     */
    public BreachCheckResult checkPassword(String password) {
        try {
            return checkPasswordAsync(password).get(config.apiTimeoutMs() + 500L, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOG.warnv("Synchronous breach check timed out or failed: {0}", e.getMessage());
            if (config.failOpen()) {
                return BreachCheckResult.degradedFailOpen();
            }
            return BreachCheckResult.breached(1);
        }
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 algorithm not available in JVM", e);
        }
    }
}
