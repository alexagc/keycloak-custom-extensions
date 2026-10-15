package io.github.alexc.keycloak.risk.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.models.UserModel;

/**
 * Computes privacy-preserving client device fingerprints and detects new or anomalous devices
 * compared to the user's historical login profile.
 */
public final class DeviceFingerprintEvaluator {

  public static final String ATTR_KNOWN_FINGERPRINTS = "known_device_fingerprints";
  private static final int MAX_SAVED_FINGERPRINTS = 10;

  private DeviceFingerprintEvaluator() {
    // Utility class
  }

  /**
   * Device evaluation result.
   *
   * @param fingerprint Computed SHA-256 fingerprint hash
   * @param isNewDevice Whether the device is unrecognised for the user
   * @param riskScore Risk points contribution
   * @param description Explanatory rationale
   */
  public record DeviceResult(
      String fingerprint,
      boolean isNewDevice,
      int riskScore,
      String description) {}

  /**
   * Computes a deterministic SHA-256 fingerprint from browser and client metadata.
   *
   * @param userAgent HTTP User-Agent string
   * @param acceptLanguage HTTP Accept-Language string
   * @param ipAddress Client IP address
   * @return Hex-encoded 64-character SHA-256 hash
   */
  public static String computeFingerprint(String userAgent, String acceptLanguage, String ipAddress) {
    String ua = (userAgent != null) ? userAgent.trim() : "Unknown";
    String lang = (acceptLanguage != null) ? acceptLanguage.trim() : "None";
    String subnet = extractSubnet(ipAddress);

    String raw = ua + "|" + lang + "|" + subnet;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(64);
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 message digest must be available", e);
    }
  }

  /**
   * Evaluates if a device fingerprint is known for the given user.
   *
   * @param user Keycloak user model
   * @param fingerprint Current computed device fingerprint
   * @param newDevicePenalty Risk points if device is new
   * @return DeviceResult evaluation
   */
  public static DeviceResult evaluateDevice(UserModel user, String fingerprint, int newDevicePenalty) {
    if (user == null || fingerprint == null || fingerprint.isBlank()) {
      return new DeviceResult(fingerprint, false, 0, "No device fingerprint available");
    }

    Set<String> known = getKnownFingerprints(user);
    if (known.isEmpty()) {
      // First time tracking device for this user
      return new DeviceResult(fingerprint, true, newDevicePenalty, "First recorded device for user");
    }

    if (known.contains(fingerprint)) {
      return new DeviceResult(fingerprint, false, 0, "Recognised familiar device");
    }

    return new DeviceResult(fingerprint, true, newDevicePenalty, "Unrecognised new device or browser detected");
  }

  /**
   * Records a verified device fingerprint to the user model, evicting oldest if capacity exceeded.
   *
   * @param user Keycloak user model
   * @param fingerprint Verified fingerprint to persist
   */
  public static void recordDeviceFingerprint(UserModel user, String fingerprint) {
    if (user == null || fingerprint == null || fingerprint.isBlank()) {
      return;
    }

    Set<String> known = new HashSet<>(getKnownFingerprints(user));
    known.add(fingerprint);

    // Limit stored fingerprints to prevent user attribute bloat
    List<String> list = known.stream().limit(MAX_SAVED_FINGERPRINTS).collect(Collectors.toList());
    user.setAttribute(ATTR_KNOWN_FINGERPRINTS, list);
  }

  /**
   * Retrieves the set of previously recorded fingerprints for a user.
   *
   * @param user Keycloak user model
   * @return Unmodifiable set of hex fingerprint strings
   */
  public static Set<String> getKnownFingerprints(UserModel user) {
    if (user == null) {
      return Collections.emptySet();
    }
    List<String> attributes = user.getAttributeStream(ATTR_KNOWN_FINGERPRINTS).collect(Collectors.toList());
    if (attributes.isEmpty()) {
      return Collections.emptySet();
    }

    Set<String> set = new HashSet<>();
    for (String attr : attributes) {
      if (attr != null && !attr.isBlank()) {
        String[] parts = attr.split(",");
        for (String part : parts) {
          String trimmed = part.trim();
          if (!trimmed.isEmpty()) {
            set.add(trimmed);
          }
        }
      }
    }
    return Collections.unmodifiableSet(set);
  }

  private static String extractSubnet(String ipAddress) {
    if (ipAddress == null || ipAddress.isBlank()) {
      return "0.0.0.0/24";
    }
    String clean = ipAddress.trim();
    int lastDot = clean.lastIndexOf('.');
    if (lastDot > 0) {
      // IPv4: take first 3 octets
      return clean.substring(0, lastDot) + ".0/24";
    }
    int colon = clean.indexOf(':');
    if (colon > 0) {
      // IPv6: take prefix
      return clean.substring(0, colon) + "::/64";
    }
    return clean;
  }
}
