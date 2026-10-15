package io.github.alexc.keycloak.authenticator.ratelimit;

/**
 * Immutable decision result from the sliding window rate limit evaluation.
 *
 * @param allowed true if the request is within quota, false if rate limited
 * @param remaining number of requests remaining in current window
 * @param retryAfterSeconds seconds to wait until quota resets (0 if allowed)
 * @param keyType type of rate limit evaluated (e.g. "IP" or "USER")
 */
public record RateLimitDecision(
    boolean allowed, long remaining, long retryAfterSeconds, String keyType) {

  /**
   * Creates an allowed decision.
   *
   * @param remaining remaining requests in current window
   * @param keyType evaluation type
   * @return decision indicating request is allowed
   */
  public static RateLimitDecision allow(long remaining, String keyType) {
    return new RateLimitDecision(true, Math.max(0, remaining), 0, keyType);
  }

  /**
   * Creates a rejected decision with required retry-after seconds.
   *
   * @param retryAfterSeconds seconds to wait before retrying
   * @param keyType evaluation type
   * @return decision indicating request is blocked
   */
  public static RateLimitDecision reject(long retryAfterSeconds, String keyType) {
    return new RateLimitDecision(false, 0, Math.max(1, retryAfterSeconds), keyType);
  }
}
