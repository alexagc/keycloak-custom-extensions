package io.github.alexc.keycloak.authenticator.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

  @Test
  @DisplayName("SHA-1 is a 40-character valid hexadecimal string")
  void testSha1Validity() {
    String sha1 = SlidingWindowScript.getSha1();

    assertNotNull(sha1);
    assertEquals(40, sha1.length(), "SHA-1 hex string must be 40 chars long");
    assertTrue(sha1.matches("^[0-9a-f]{40}$"), "SHA-1 must match lowercase hex format");
    assertEquals(SlidingWindowScript.getSha1(), sha1, "SHA-1 should be deterministic");
  }
}
