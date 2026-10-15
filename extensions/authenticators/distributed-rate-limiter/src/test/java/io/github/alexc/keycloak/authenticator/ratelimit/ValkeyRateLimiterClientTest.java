package io.github.alexc.keycloak.authenticator.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.valkey.Jedis;
import io.valkey.JedisPool;
import io.valkey.exceptions.JedisConnectionException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValkeyRateLimiterClientTest {

  private JedisPool pool;
  private Jedis jedis;
  private ValkeyRateLimiterClient client;

  @BeforeEach
  void setUp() {
    pool = mock(JedisPool.class);
    jedis = mock(Jedis.class);
    client = new ValkeyRateLimiterClient(pool);
  }

  @Test
  @DisplayName("normalizeUri correctly maps valkey schemes to redis wire schemes")
  void testNormalizeUri() {
    assertEquals(
        "redis://localhost:6379", ValkeyRateLimiterClient.normalizeUri("valkey://localhost:6379"));
    assertEquals(
        "rediss://secure-host:6380",
        ValkeyRateLimiterClient.normalizeUri("valkeys://secure-host:6380"));
    assertEquals(
        "redis://my-redis:6379", ValkeyRateLimiterClient.normalizeUri("redis://my-redis:6379"));
    assertEquals(
        "rediss://my-redis:6380", ValkeyRateLimiterClient.normalizeUri("rediss://my-redis:6380"));
    assertEquals("redis://localhost:6379", ValkeyRateLimiterClient.normalizeUri(null));
    assertEquals("redis://localhost:6379", ValkeyRateLimiterClient.normalizeUri("  "));
  }

  @Test
  @DisplayName("maskUri sanitizes password in URI strings for logs")
  void testMaskUri() {
    String masked =
        ValkeyRateLimiterClient.maskUri("valkey://default:SuperSecret123@localhost:6379");
    assertEquals("valkey://default:***@localhost:6379", masked);
    assertEquals("", ValkeyRateLimiterClient.maskUri(null));
  }

  @Test
  @DisplayName("evaluate returns allowed when Lua script reports under quota")
  void testEvaluateAllowed() {
    when(pool.getResource()).thenReturn(jedis);
    when(jedis.eval(eq(SlidingWindowScript.getScript()), anyList(), anyList()))
        .thenReturn(List.of(1L, 25L, 0L));

    RateLimitDecision decision = client.evaluate("rl:ip:master:1.2.3.4", 60, 30, "IP", true);

    assertTrue(decision.allowed());
    assertEquals(25, decision.remaining());
    assertEquals(0, decision.retryAfterSeconds());
    assertEquals("IP", decision.keyType());
    verify(jedis).close();
  }

  @Test
  @DisplayName("evaluate returns rejected with retryAfter when quota is exceeded")
  void testEvaluateRejected() {
    when(pool.getResource()).thenReturn(jedis);
    when(jedis.eval(eq(SlidingWindowScript.getScript()), anyList(), anyList()))
        .thenReturn(List.of(0L, 0L, 45L));

    RateLimitDecision decision = client.evaluate("rl:usr:master:alice", 120, 5, "USER", true);

    assertFalse(decision.allowed());
    assertEquals(0, decision.remaining());
    assertEquals(45, decision.retryAfterSeconds());
    assertEquals("USER", decision.keyType());
  }

  @Test
  @DisplayName("evaluate with fail-open allows request when Valkey encounters connection failure")
  void testEvaluateFailOpen() {
    when(pool.getResource()).thenThrow(new JedisConnectionException("Connection refused"));

    RateLimitDecision decision = client.evaluate("rl:ip:master:1.2.3.4", 60, 30, "IP", true);

    assertTrue(decision.allowed(), "Must allow request when fail-open is enabled");
    assertEquals(30, decision.remaining());
  }

  @Test
  @DisplayName("evaluate without fail-open throws RateLimiterUnavailableException on error")
  void testEvaluateFailClose() {
    when(pool.getResource()).thenThrow(new JedisConnectionException("Connection timeout"));

    assertThrows(
        RateLimiterUnavailableException.class,
        () -> client.evaluate("rl:ip:master:1.2.3.4", 60, 30, "IP", false));
  }

  @Test
  @DisplayName("close shuts down underlying pool cleanly")
  void testClose() {
    when(pool.isClosed()).thenReturn(false);
    client.close();
    verify(pool).close();
  }
}
