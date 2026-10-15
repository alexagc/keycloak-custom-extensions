package io.github.alexc.keycloak.risk.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Contextual metadata captured during an incoming authentication attempt.
 *
 * @param ipAddress Client IP address
 * @param userAgent HTTP User-Agent header string
 * @param timestamp Epoch millisecond timestamp of the login attempt
 * @param geoLocation Resolved geographic location
 * @param deviceFingerprint Client device fingerprint hash
 */
public record LoginContext(
    String ipAddress,
    String userAgent,
    long timestamp,
    GeoLocation geoLocation,
    String deviceFingerprint) implements Serializable {

  /**
   * Compact constructor ensuring null-safety.
   */
  public LoginContext {
    Objects.requireNonNull(ipAddress, "ipAddress must not be null");
    userAgent = (userAgent != null) ? userAgent : "Unknown";
    geoLocation = (geoLocation != null) ? geoLocation : GeoLocation.empty(ipAddress);
    deviceFingerprint = (deviceFingerprint != null) ? deviceFingerprint : "";
  }
}
