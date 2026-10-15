package io.github.alexc.keycloak.authenticator.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.Config;
import org.keycloak.models.AuthenticatorConfigModel;

class RateLimiterConfigTest {

  @Test
  @DisplayName("Default configuration provides secure, sensible defaults")
  void testDefaultConfiguration() {
    RateLimiterConfig config = RateLimiterConfig.defaultConfiguration();

    assertNotNull(config);
    assertEquals("valkey://localhost:6379", config.redisUri());
    assertEquals(30, config.ipLimit());
    assertEquals(60, config.ipWindowSeconds());
    assertEquals(5, config.userLimit());
    assertEquals(120, config.userWindowSeconds());
    assertTrue(config.failOpen());
    assertEquals(500, config.connectTimeoutMs());
    assertEquals(500, config.socketTimeoutMs());
    assertEquals(32, config.maxPoolSize());
  }

  @Test
  @DisplayName("fromScope correctly parses SPI scope values")
  void testFromScope() {
    Config.Scope scope = mock(Config.Scope.class);
    when(scope.get(RateLimiterConfig.CONF_REDIS_URI, RateLimiterConfig.DEFAULT_REDIS_URI))
        .thenReturn("valkey://my-cluster:6379");
    when(scope.getInt(RateLimiterConfig.CONF_IP_LIMIT, RateLimiterConfig.DEFAULT_IP_LIMIT))
        .thenReturn(100);
    when(scope.getInt(RateLimiterConfig.CONF_IP_WINDOW, RateLimiterConfig.DEFAULT_IP_WINDOW))
        .thenReturn(30);
    when(scope.getInt(RateLimiterConfig.CONF_USER_LIMIT, RateLimiterConfig.DEFAULT_USER_LIMIT))
        .thenReturn(10);
    when(scope.getInt(RateLimiterConfig.CONF_USER_WINDOW, RateLimiterConfig.DEFAULT_USER_WINDOW))
        .thenReturn(300);
    when(scope.getBoolean(RateLimiterConfig.CONF_FAIL_OPEN, RateLimiterConfig.DEFAULT_FAIL_OPEN))
        .thenReturn(false);
    when(scope.getInt(
            RateLimiterConfig.CONF_CONNECT_TIMEOUT, RateLimiterConfig.DEFAULT_CONNECT_TIMEOUT))
        .thenReturn(200);
    when(scope.getInt(
            RateLimiterConfig.CONF_SOCKET_TIMEOUT, RateLimiterConfig.DEFAULT_SOCKET_TIMEOUT))
        .thenReturn(250);
    when(scope.getInt(RateLimiterConfig.CONF_MAX_POOL_SIZE, RateLimiterConfig.DEFAULT_MAX_POOL_SIZE))
        .thenReturn(64);

    RateLimiterConfig config = RateLimiterConfig.fromScope(scope);

    assertEquals("valkey://my-cluster:6379", config.redisUri());
    assertEquals(100, config.ipLimit());
    assertEquals(30, config.ipWindowSeconds());
    assertEquals(10, config.userLimit());
    assertEquals(300, config.userWindowSeconds());
    assertFalse(config.failOpen());
    assertEquals(200, config.connectTimeoutMs());
    assertEquals(250, config.socketTimeoutMs());
    assertEquals(64, config.maxPoolSize());
  }

  @Test
  @DisplayName("fromScope with null scope returns defaults")
  void testFromScopeNull() {
    RateLimiterConfig config = RateLimiterConfig.fromScope(null);
    assertEquals(RateLimiterConfig.DEFAULT_REDIS_URI, config.redisUri());
  }

  @Test
  @DisplayName("merge overrides global settings with AuthenticatorConfigModel values")
  void testMerge() {
    RateLimiterConfig base = RateLimiterConfig.defaultConfiguration();
    AuthenticatorConfigModel model = mock(AuthenticatorConfigModel.class);
    when(model.getConfig())
        .thenReturn(
            Map.of(
                RateLimiterConfig.CONF_REDIS_URI, "valkeys://secure-host:6380",
                RateLimiterConfig.CONF_IP_LIMIT, "50",
                RateLimiterConfig.CONF_IP_WINDOW, "45",
                RateLimiterConfig.CONF_USER_LIMIT, "3",
                RateLimiterConfig.CONF_USER_WINDOW, "180",
                RateLimiterConfig.CONF_FAIL_OPEN, "false",
                RateLimiterConfig.CONF_CONNECT_TIMEOUT, "300",
                RateLimiterConfig.CONF_SOCKET_TIMEOUT, "400",
                RateLimiterConfig.CONF_MAX_POOL_SIZE, "16"));

    RateLimiterConfig merged = base.merge(model);

    assertEquals("valkeys://secure-host:6380", merged.redisUri());
    assertEquals(50, merged.ipLimit());
    assertEquals(45, merged.ipWindowSeconds());
    assertEquals(3, merged.userLimit());
    assertEquals(180, merged.userWindowSeconds());
    assertFalse(merged.failOpen());
    assertEquals(300, merged.connectTimeoutMs());
    assertEquals(400, merged.socketTimeoutMs());
    assertEquals(16, merged.maxPoolSize());
  }

  @Test
  @DisplayName("merge with null or empty config returns base instance")
  void testMergeEmpty() {
    RateLimiterConfig base = RateLimiterConfig.defaultConfiguration();
    assertEquals(base, base.merge(null));

    AuthenticatorConfigModel model = mock(AuthenticatorConfigModel.class);
    when(model.getConfig()).thenReturn(null);
    assertEquals(base, base.merge(model));
  }

  @Test
  @DisplayName("merge with invalid integer or boolean strings safely falls back to base values")
  void testMergeInvalidStrings() {
    RateLimiterConfig base = RateLimiterConfig.defaultConfiguration();
    AuthenticatorConfigModel model = mock(AuthenticatorConfigModel.class);
    when(model.getConfig())
        .thenReturn(
            Map.of(
                RateLimiterConfig.CONF_IP_LIMIT, "not-a-number",
                RateLimiterConfig.CONF_FAIL_OPEN, "   "));

    RateLimiterConfig merged = base.merge(model);

    assertEquals(base.ipLimit(), merged.ipLimit());
    assertEquals(base.failOpen(), merged.failOpen());
  }
}
