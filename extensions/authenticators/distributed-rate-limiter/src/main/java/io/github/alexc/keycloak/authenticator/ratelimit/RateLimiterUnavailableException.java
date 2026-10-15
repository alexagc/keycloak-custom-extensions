package io.github.alexc.keycloak.authenticator.ratelimit;

/**
 * Exception thrown when the Valkey/Redis rate limiter service is unreachable
 * and fail-open resilience is disabled.
 */
public class RateLimiterUnavailableException extends RuntimeException {

  public RateLimiterUnavailableException(String message) {
    super(message);
  }

  public RateLimiterUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
