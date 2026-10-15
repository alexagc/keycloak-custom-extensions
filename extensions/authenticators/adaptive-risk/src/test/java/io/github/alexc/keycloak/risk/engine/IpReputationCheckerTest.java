package io.github.alexc.keycloak.risk.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IpReputationCheckerTest {

  @Test
  @DisplayName("Should detect known TOR exit node from baseline list")
  void testDetectTorExitNode() {
    IpReputationChecker checker = new IpReputationChecker();
    IpReputationChecker.ReputationResult result = checker.checkReputation("185.220.101.5", true, 50);

    assertTrue(result.isTorExitNode());
    assertEquals(50, result.riskScore());
    assertFalse(result.isPrivateOrLocal());
  }

  @Test
  @DisplayName("Should accept clean public IP without penalty")
  void testCleanPublicIp() {
    IpReputationChecker checker = new IpReputationChecker();
    IpReputationChecker.ReputationResult result = checker.checkReputation("8.8.8.8", true, 50);

    assertFalse(result.isTorExitNode());
    assertEquals(0, result.riskScore());
  }

  @Test
  @DisplayName("Should classify loopback and private IPv4 ranges as local with 0 risk")
  void testPrivateIps() {
    IpReputationChecker checker = new IpReputationChecker();

    assertTrue(checker.checkReputation("127.0.0.1", true, 50).isPrivateOrLocal());
    assertEquals(0, checker.checkReputation("127.0.0.1", true, 50).riskScore());

    assertTrue(checker.checkReputation("10.0.0.1", true, 50).isPrivateOrLocal());
    assertTrue(checker.checkReputation("172.16.5.10", true, 50).isPrivateOrLocal());
    assertTrue(checker.checkReputation("192.168.1.100", true, 50).isPrivateOrLocal());
  }

  @Test
  @DisplayName("Should bypass TOR check when checkTor flag is false")
  void testBypassTorWhenDisabled() {
    IpReputationChecker checker = new IpReputationChecker();
    IpReputationChecker.ReputationResult result = checker.checkReputation("185.220.101.5", false, 50);

    assertFalse(result.isTorExitNode());
    assertEquals(0, result.riskScore());
  }

  @Test
  @DisplayName("Should support custom in-memory TOR set")
  void testCustomTorSet() {
    IpReputationChecker checker = new IpReputationChecker(Set.of("203.0.113.42"));

    assertTrue(checker.checkReputation("203.0.113.42", true, 60).isTorExitNode());
    assertEquals(60, checker.checkReputation("203.0.113.42", true, 60).riskScore());
    assertFalse(checker.checkReputation("185.220.101.5", true, 60).isTorExitNode());
  }

  @Test
  @DisplayName("Should handle null or empty IP gracefully")
  void testNullOrEmptyIp() {
    IpReputationChecker checker = new IpReputationChecker();

    assertEquals(0, checker.checkReputation(null, true, 50).riskScore());
    assertEquals(0, checker.checkReputation("", true, 50).riskScore());
    assertEquals(0, checker.checkReputation("   ", true, 50).riskScore());
  }
}
