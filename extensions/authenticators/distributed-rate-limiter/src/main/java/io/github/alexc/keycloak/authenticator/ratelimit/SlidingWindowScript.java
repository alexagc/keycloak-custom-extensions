package io.github.alexc.keycloak.authenticator.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Atomic sliding window rate limiting Lua script for Valkey and Redis.
 *
 * <p>Executes as a single atomic operation inside Valkey/Redis:
 * <ol>
 *   <li>Prunes requests older than the sliding window using {@code ZREMRANGEBYSCORE}.
 *   <li>Counts current active requests with {@code ZCARD}.
 *   <li>If under quota: registers current request with {@code ZADD}, sets TTL with {@code PEXPIRE},
 *       and returns {@code [1, remaining, 0]}.
 *   <li>If quota exceeded: retrieves oldest request score with {@code ZRANGE ... WITHSCORES},
 *       computes exact retry-after seconds, and returns {@code [0, 0, retryAfterSeconds]}.
 * </ol>
 */
public final class SlidingWindowScript {

  public static final String LUA_SCRIPT =
      """
      local key = KEYS[1]
      local now = tonumber(ARGV[1])
      local windowMs = tonumber(ARGV[2])
      local limit = tonumber(ARGV[3])
      local member = ARGV[4]

      -- 1. Remove expired timestamps outside sliding window
      local clearBefore = now - windowMs
      redis.call('ZREMRANGEBYSCORE', key, '-inf', clearBefore)

      -- 2. Count current elements in window
      local current = redis.call('ZCARD', key)

      -- 3. Evaluate quota
      if current < limit then
          -- Within quota: record current request
          redis.call('ZADD', key, now, member)
          -- Extend key expiration to window duration
          redis.call('PEXPIRE', key, windowMs)
          local remaining = limit - current - 1
          return {1, remaining, 0}
      else
          -- Quota exceeded: compute retry-after based on oldest active item
          local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
          local retryAfter = 1
          if oldest and #oldest >= 2 then
              local oldestScore = tonumber(oldest[2])
              local expiresAt = oldestScore + windowMs
              local diffMs = expiresAt - now
              retryAfter = math.ceil(diffMs / 1000)
              if retryAfter < 1 then
                  retryAfter = 1
              end
          else
              retryAfter = math.ceil(windowMs / 1000)
          end
          return {0, 0, retryAfter}
      end
      """;

  private static final String SHA1 = computeSha1(LUA_SCRIPT);

  private SlidingWindowScript() {}

  /**
   * Gets the Lua script content.
   *
   * @return the raw Lua script
   */
  public static String getScript() {
    return LUA_SCRIPT;
  }

  /**
   * Gets the precomputed SHA-1 hash for {@code EVALSHA}.
   *
   * @return lowercase SHA-1 hex digest
   */
  public static String getSha1() {
    return SHA1;
  }

  private static String computeSha1(String text) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 not available in JVM", e);
    }
  }
}
