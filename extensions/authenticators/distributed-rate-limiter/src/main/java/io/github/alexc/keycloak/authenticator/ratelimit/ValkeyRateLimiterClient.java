package io.github.alexc.keycloak.authenticator.ratelimit;

import io.valkey.Jedis;
import io.valkey.JedisPool;
import io.valkey.JedisPoolConfig;
import io.valkey.exceptions.JedisNoScriptException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Enterprise client for evaluating atomic sliding window rate limits on Valkey and Redis.
 *
 * <p>Backed by a thread-safe {@link JedisPool} from {@code io.valkey:valkey-java},
 * supporting TLS encryption, password authentication, and automatic fail-open resilience.
 */
public class ValkeyRateLimiterClient implements RateLimiterClient {

  private static final Logger LOG = Logger.getLogger(ValkeyRateLimiterClient.class);

  private final JedisPool pool;

  /**
   * Constructs client from configuration.
   *
   * @param config rate limiter configuration
   */
  public ValkeyRateLimiterClient(RateLimiterConfig config) {
    Objects.requireNonNull(config, "config must not be null");
    URI normalizedUri = URI.create(normalizeUri(config.redisUri()));

    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(config.maxPoolSize());
    poolConfig.setMaxIdle(Math.min(16, config.maxPoolSize()));
    poolConfig.setMinIdle(1);
    poolConfig.setTestOnBorrow(false);
    poolConfig.setTestOnReturn(false);
    poolConfig.setBlockWhenExhausted(true);
    poolConfig.setMaxWait(Duration.ofMillis(config.connectTimeoutMs()));

    this.pool =
        new JedisPool(
            poolConfig, normalizedUri, config.connectTimeoutMs(), config.socketTimeoutMs());
    LOG.infov(
        "Initialized Valkey/Redis rate limiter pool (uri={0}, maxPool={1}, timeout={2}ms)",
        maskUri(config.redisUri()), config.maxPoolSize(), config.socketTimeoutMs());
  }

  /**
   * Constructs client with a custom or mocked pool (mainly for testing).
   *
   * @param pool the pre-configured JedisPool
   */
  public ValkeyRateLimiterClient(JedisPool pool) {
    this.pool = Objects.requireNonNull(pool, "pool must not be null");
  }

  @Override
  public RateLimitDecision evaluate(
      String key, int windowSeconds, int limit, String keyType, boolean failOpen) {
    long now = System.currentTimeMillis();
    long windowMs = windowSeconds * 1000L;
    String uniqueMember = now + ":" + UUID.randomUUID();

    List<String> keys = Collections.singletonList(key);
    List<String> args =
        List.of(
            String.valueOf(now),
            String.valueOf(windowMs),
            String.valueOf(limit),
            uniqueMember);

    try (Jedis jedis = pool.getResource()) {
      Object result;
      try {
        result = jedis.evalsha(SlidingWindowScript.getSha1(), keys, args);
      } catch (JedisNoScriptException e) {
        result = jedis.eval(SlidingWindowScript.getScript(), keys, args);
      }

      if (result instanceof List<?> list && list.size() >= 3) {
        long allowed = ((Number) list.get(0)).longValue();
        long remaining = ((Number) list.get(1)).longValue();
        long retryAfter = ((Number) list.get(2)).longValue();

        if (allowed == 1L) {
          return RateLimitDecision.allow(remaining, keyType);
        } else {
          return RateLimitDecision.reject(retryAfter, keyType);
        }
      }

      LOG.warnv("Unexpected Lua script return payload for key {0}: {1}", key, result);
      return RateLimitDecision.allow(limit, keyType);
    } catch (Exception e) {
      if (failOpen) {
        LOG.warnv(
            "Valkey/Redis rate limiter unavailable for key {0} ({1}). Failing open: {2}",
            key, keyType, e.getMessage());
        return RateLimitDecision.allow(limit, keyType);
      } else {
        LOG.errorv(
            e,
            "Valkey/Redis rate limiter error for key {0} ({1}) and fail-open is disabled.",
            key,
            keyType);
        throw new RateLimiterUnavailableException("Valkey rate limiter service error", e);
      }
    }
  }

  @Override
  public void close() {
    if (pool != null && !pool.isClosed()) {
      try {
        pool.close();
        LOG.info("Closed Valkey/Redis rate limiter pool.");
      } catch (Exception e) {
        LOG.warnv("Error closing Valkey/Redis pool: {0}", e.getMessage());
      }
    }
  }

  static String normalizeUri(String uriString) {
    if (uriString == null || uriString.isBlank()) {
      return "redis://localhost:6379";
    }
    String trimmed = uriString.trim();
    if (trimmed.startsWith("valkeys://")) {
      return "rediss://" + trimmed.substring("valkeys://".length());
    } else if (trimmed.startsWith("valkey://")) {
      return "redis://" + trimmed.substring("valkey://".length());
    }
    return trimmed;
  }

  static String maskUri(String uriString) {
    if (uriString == null) {
      return "";
    }
    return uriString.replaceAll("://([^:]+):[^@]+@", "://$1:***@");
  }
}
