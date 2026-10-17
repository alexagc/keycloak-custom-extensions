package io.github.alexc.keycloak.risk.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.models.UserModel;

class DeviceFingerprintEvaluatorTest {

  @Test
  @DisplayName("Should generate deterministic SHA-256 fingerprint hash")
  void testDeterministicFingerprint() {
    String fp1 = DeviceFingerprintEvaluator.computeFingerprint(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "en-US,en;q=0.9", "198.51.100.25");
    String fp2 = DeviceFingerprintEvaluator.computeFingerprint(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "en-US,en;q=0.9", "198.51.100.50");

    assertNotNull(fp1);
    assertEquals(64, fp1.length(), "SHA-256 hex string must be 64 characters");
    // Since both IPs share the 198.51.100.0/24 subnet and same UA/lang, hashes match
    assertEquals(fp1, fp2);
  }

  @Test
  @DisplayName("Should flag first device on empty user profile as new device")
  void testFirstDeviceForUser() {
    UserModel user = mock(UserModel.class);
    when(user.getAttributeStream(DeviceFingerprintEvaluator.ATTR_KNOWN_FINGERPRINTS)).thenReturn(Stream.empty());

    DeviceFingerprintEvaluator.DeviceResult result = DeviceFingerprintEvaluator.evaluateDevice(user, "dummy-hash-1", 15);

    assertTrue(result.isNewDevice());
    assertEquals(15, result.riskScore());
  }

  @Test
  @DisplayName("Should recognise known device without penalty")
  void testRecognisedDevice() {
    UserModel user = mock(UserModel.class);
    when(user.getAttributeStream(DeviceFingerprintEvaluator.ATTR_KNOWN_FINGERPRINTS))
        .thenReturn(Stream.of("saved-hash-abc,saved-hash-def"));

    DeviceFingerprintEvaluator.DeviceResult result = DeviceFingerprintEvaluator.evaluateDevice(user, "saved-hash-abc", 15);

    assertFalse(result.isNewDevice());
    assertEquals(0, result.riskScore());
  }

  @Test
  @DisplayName("Should flag unrecognised device when known devices exist")
  void testUnrecognisedDevice() {
    UserModel user = mock(UserModel.class);
    when(user.getAttributeStream(DeviceFingerprintEvaluator.ATTR_KNOWN_FINGERPRINTS))
        .thenReturn(Stream.of("saved-hash-abc"));

    DeviceFingerprintEvaluator.DeviceResult result = DeviceFingerprintEvaluator.evaluateDevice(user, "different-hash-xyz", 15);

    assertTrue(result.isNewDevice());
    assertEquals(15, result.riskScore());
  }

  @Test
  @DisplayName("Should record new device fingerprint onto user model")
  void testRecordDeviceFingerprint() {
    UserModel user = mock(UserModel.class);
    when(user.getAttributeStream(DeviceFingerprintEvaluator.ATTR_KNOWN_FINGERPRINTS))
        .thenReturn(Stream.of("hash-1"));

    DeviceFingerprintEvaluator.recordDeviceFingerprint(user, "hash-2");

    verify(user).setAttribute(
        eq(DeviceFingerprintEvaluator.ATTR_KNOWN_FINGERPRINTS),
        argThat(list -> list.contains("hash-1") && list.contains("hash-2") && list.size() == 2));
  }

  @Test
  @DisplayName("Should evict oldest device fingerprint (FIFO) when capacity exceeds 10")
  void testFifoEvictionOfOldestFingerprint() {
    UserModel user = mock(UserModel.class);
    // Already has 10 fingerprints: hash-1 through hash-10
    List<String> existing = java.util.stream.IntStream.rangeClosed(1, 10)
        .mapToObj(i -> "hash-" + i)
        .collect(java.util.stream.Collectors.toList());
    when(user.getAttributeStream(DeviceFingerprintEvaluator.ATTR_KNOWN_FINGERPRINTS))
        .thenReturn(existing.stream());

    // Record the 11th fingerprint
    DeviceFingerprintEvaluator.recordDeviceFingerprint(user, "hash-11");

    verify(user).setAttribute(
        eq(DeviceFingerprintEvaluator.ATTR_KNOWN_FINGERPRINTS),
        argThat(list ->
            list.size() == 10
            && !list.contains("hash-1") // Oldest evicted
            && list.contains("hash-2")
            && list.contains("hash-11") // Newest retained
        ));
  }
}
