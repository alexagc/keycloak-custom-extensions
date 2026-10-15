package io.github.alexc.keycloak.authenticator.ratelimit;

import java.util.Map;
import org.keycloak.Config;
import org.keycloak.models.AuthenticatorConfigModel;

/**
 * Immutable configuration for the Distributed Rate Limiter.
 *
 * <p>Supports hierarchical resolution from Keycloak execution config (Admin Console),
 * global SPI configuration (environment variables / standalone.xml), and safe defaults.
 *
 * @param redisUri Valkey/Redis connection URI (supports valkey://, valkeys://, redis://, rediss://)
 * @param ipLimit maximum requests allowed per IP within the sliding window
 * @param ipWindowSeconds duration in seconds for the IP sliding window
 * @param userLimit maximum requests allowed per username within the sliding window
 * @param userWindowSeconds duration in seconds for the user sliding window
 * @param failOpen whether to allow requests through if Valkey/Redis is unreachable
 * @param connectTimeoutMs Valkey/Redis socket connection timeout in milliseconds
 * @param socketTimeoutMs Valkey/Redis socket read timeout in milliseconds
 * @param maxPoolSize maximum connection pool size
 */
public record RateLimiterConfig(
    String redisUri,
    int ipLimit,
    int ipWindowSeconds,
    int userLimit,
    int userWindowSeconds,
    boolean failOpen,
    int connectTimeoutMs,
    int socketTimeoutMs,
    int maxPoolSize) {

  public static final String CONF_REDIS_URI = "redis-uri";
  public static final String CONF_IP_LIMIT = "ip-limit";
  public static final String CONF_IP_WINDOW = "ip-window-seconds";
  public static final String CONF_USER_LIMIT = "user-limit";
  public static final String CONF_USER_WINDOW = "user-window-seconds";
  public static final String CONF_FAIL_OPEN = "fail-open";
  public static final String CONF_CONNECT_TIMEOUT = "connect-timeout-ms";
  public static final String CONF_SOCKET_TIMEOUT = "socket-timeout-ms";
  public static final String CONF_MAX_POOL_SIZE = "max-pool-size";

  public static final String DEFAULT_REDIS_URI = "valkey://localhost:6379";
  public static final int DEFAULT_IP_LIMIT = 30;
  public static final int DEFAULT_IP_WINDOW = 60;
  public static final int DEFAULT_USER_LIMIT = 5;
  public static final int DEFAULT_USER_WINDOW = 120;
  public static final boolean DEFAULT_FAIL_OPEN = true;
  public static final int DEFAULT_CONNECT_TIMEOUT = 500;
  public static final int DEFAULT_SOCKET_TIMEOUT = 500;
  public static final int DEFAULT_MAX_POOL_SIZE = 32;

  /**
   * Builds configuration from global Keycloak SPI configuration scope.
   *
   * @param scope the SPI configuration scope
   * @return initialized configuration
   */
  public static RateLimiterConfig fromScope(Config.Scope scope) {
    if (scope == null) {
      return defaultConfiguration();
    }
    String uri = scope.get(CONF_REDIS_URI, DEFAULT_REDIS_URI);
    int ipLim = scope.getInt(CONF_IP_LIMIT, DEFAULT_IP_LIMIT);
    int ipWin = scope.getInt(CONF_IP_WINDOW, DEFAULT_IP_WINDOW);
    int usrLim = scope.getInt(CONF_USER_LIMIT, DEFAULT_USER_LIMIT);
    int usrWin = scope.getInt(CONF_USER_WINDOW, DEFAULT_USER_WINDOW);
    boolean fo = scope.getBoolean(CONF_FAIL_OPEN, DEFAULT_FAIL_OPEN);
    int connTo = scope.getInt(CONF_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT);
    int sockTo = scope.getInt(CONF_SOCKET_TIMEOUT, DEFAULT_SOCKET_TIMEOUT);
    int poolSz = scope.getInt(CONF_MAX_POOL_SIZE, DEFAULT_MAX_POOL_SIZE);
    return new RateLimiterConfig(uri, ipLim, ipWin, usrLim, usrWin, fo, connTo, sockTo, poolSz);
  }

  /**
   * Merges per-execution configuration from AuthenticatorConfigModel over global SPI settings.
   *
   * @param model the authenticator execution config model from Keycloak admin console
   * @return merged configuration
   */
  public RateLimiterConfig merge(AuthenticatorConfigModel model) {
    if (model == null || model.getConfig() == null) {
      return this;
    }
    Map<String, String> cfg = model.getConfig();
    String uri = cfg.getOrDefault(CONF_REDIS_URI, this.redisUri);
    int ipLim = parseInt(cfg.get(CONF_IP_LIMIT), this.ipLimit);
    int ipWin = parseInt(cfg.get(CONF_IP_WINDOW), this.ipWindowSeconds);
    int usrLim = parseInt(cfg.get(CONF_USER_LIMIT), this.userLimit);
    int usrWin = parseInt(cfg.get(CONF_USER_WINDOW), this.userWindowSeconds);
    boolean fo = parseBool(cfg.get(CONF_FAIL_OPEN), this.failOpen);
    int connTo = parseInt(cfg.get(CONF_CONNECT_TIMEOUT), this.connectTimeoutMs);
    int sockTo = parseInt(cfg.get(CONF_SOCKET_TIMEOUT), this.socketTimeoutMs);
    int poolSz = parseInt(cfg.get(CONF_MAX_POOL_SIZE), this.maxPoolSize);
    return new RateLimiterConfig(uri, ipLim, ipWin, usrLim, usrWin, fo, connTo, sockTo, poolSz);
  }

  /**
   * Default configuration.
   *
   * @return configuration with standard default values
   */
  public static RateLimiterConfig defaultConfiguration() {
    return new RateLimiterConfig(
        DEFAULT_REDIS_URI,
        DEFAULT_IP_LIMIT,
        DEFAULT_IP_WINDOW,
        DEFAULT_USER_LIMIT,
        DEFAULT_USER_WINDOW,
        DEFAULT_FAIL_OPEN,
        DEFAULT_CONNECT_TIMEOUT,
        DEFAULT_SOCKET_TIMEOUT,
        DEFAULT_MAX_POOL_SIZE);
  }

  private static int parseInt(String val, int defaultVal) {
    if (val == null || val.isBlank()) {
      return defaultVal;
    }
    try {
      return Integer.parseInt(val.trim());
    } catch (NumberFormatException e) {
      return defaultVal;
    }
  }

  private static boolean parseBool(String val, boolean defaultVal) {
    if (val == null || val.isBlank()) {
      return defaultVal;
    }
    return Boolean.parseBoolean(val.trim());
  }
}
