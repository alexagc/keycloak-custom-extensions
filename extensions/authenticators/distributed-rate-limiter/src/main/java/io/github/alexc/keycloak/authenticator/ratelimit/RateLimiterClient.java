package io.github.alexc.keycloak.authenticator.ratelimit;

/**
 * Service contract for evaluating distributed sliding window rate limits.
 */
public interface RateLimiterClient extends AutoCloseable {

  /**
   * Evaluates rate limiting for a specific key within a sliding window.
   *
   * @param key the unique rate limiting key
   * @param windowSeconds sliding window duration in seconds
   * @param limit maximum permitted requests within the window
   * @param keyType classification of the rate limit (e.g. "IP" or "USER")
   * @param failOpen whether to allow requests through when the store fails
   * @return immutable decision result
   * @throws RateLimiterUnavailableException if store is unreachable and failOpen is false
   */
  RateLimitDecision evaluate(
      String key, int windowSeconds, int limit, String keyType, boolean failOpen);

  @Override
  void close();
}
