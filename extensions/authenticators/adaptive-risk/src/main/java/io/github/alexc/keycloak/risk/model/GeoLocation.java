package io.github.alexc.keycloak.risk.model;

import java.io.Serializable;

/**
 * Immutable geographic location representing client coordinates, locality, and associated IP address.
 *
 * @param latitude Latitude in decimal degrees [-90.0, 90.0], or null if unknown
 * @param longitude Longitude in decimal degrees [-180.0, 180.0], or null if unknown
 * @param countryCode ISO 3166-1 alpha-2 country code (e.g., "ES", "US"), or null if unknown
 * @param cityName City or locality name, or null if unknown
 * @param ipAddress IP address resolved for the location
 */
public record GeoLocation(
    Double latitude,
    Double longitude,
    String countryCode,
    String cityName,
    String ipAddress) implements Serializable {

  /**
   * Checks if valid geographic coordinates are present in this location.
   *
   * @return true if both latitude and longitude are non-null and within valid Earth ranges
   */
  public boolean hasCoordinates() {
    return latitude != null
        && longitude != null
        && !latitude.isNaN()
        && !longitude.isNaN()
        && latitude >= -90.0
        && latitude <= 90.0
        && longitude >= -180.0
        && longitude <= 180.0;
  }

  /**
   * Creates an empty GeoLocation with only the IP address populated.
   *
   * @param ipAddress Client IP address
   * @return Empty GeoLocation instance
   */
  public static GeoLocation empty(String ipAddress) {
    return new GeoLocation(null, null, null, null, ipAddress);
  }
}
