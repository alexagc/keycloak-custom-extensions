package io.github.alexc.keycloak.authenticator.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlidingWindowScriptTest {

  @Test
  @DisplayName("Lua script contains all atomic sliding window commands")
  void testLuaScriptContent() {
    String script = SlidingWindowScript.getScript();

    assertNotNull(script);
    assertFalse(script.isBlank());
    assertTrue(script.contains("ZREMRANGEBYSCORE"), "Must prune expired timestamps");
    assertTrue(script.contains("ZCARD"), "Must count active timestamps");
    assertTrue(script.contains("ZADD"), "Must register new timestamp");
    assertTrue(script.contains("PEXPIRE"), "Must refresh key TTL");
    assertTrue(script.contains("ZRANGE"), "Must retrieve oldest score for retry-after calculation");
  }
}
