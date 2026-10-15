package io.github.alexc.keycloak.risk.engine;

import io.github.alexc.keycloak.risk.model.GeoLocation;
import java.util.Optional;

/**
 * Calculates geographical distance and minimum required transit velocity between consecutive logins
 * using the great-circle Haversine formula.
 */
public final class ImpossibleTravelCalculator {

  /**
   * Earth mean radius in kilometers (WGS-84 / IUGG recommended spherical approximation).
   */
  public static final double EARTH_RADIUS_KM = 6371.0;

  /**
   * Distance threshold in kilometers below which movement is treated as stationary/negligible.
   */
  public static final double STATIONARY_THRESHOLD_KM = 1.0;

  private ImpossibleTravelCalculator() {
    // Utility class
  }

  /**
   * Evaluation result of a travel trajectory between two consecutive authentication points.
   *
   * @param distanceKm Great-circle distance in kilometers
   * @param durationHours Elapsed time in hours
   * @param velocityKmh Calculated travel velocity in km/h
   * @param isImpossibleTravel Whether velocity exceeds the configured threshold
   */
  public record TravelResult(
      double distanceKm,
      double durationHours,
      double velocityKmh,
      boolean isImpossibleTravel) {}

  /**
   * Computes the great-circle distance between two geographic coordinates using the Haversine formula.
   *
   * @param lat1 Latitude of point 1 in decimal degrees
   * @param lon1 Longitude of point 1 in decimal degrees
   * @param lat2 Latitude of point 2 in decimal degrees
   * @param lon2 Longitude of point 2 in decimal degrees
   * @return Great-circle distance in kilometers
   */
  public static double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
    double phi1 = Math.toRadians(lat1);
    double phi2 = Math.toRadians(lat2);
    double deltaPhi = Math.toRadians(lat2 - lat1);
    double deltaLambda = Math.toRadians(lon2 - lon1);

    double sinDeltaPhi = Math.sin(deltaPhi / 2.0);
    double sinDeltaLambda = Math.sin(deltaLambda / 2.0);

    double a = sinDeltaPhi * sinDeltaPhi
        + Math.cos(phi1) * Math.cos(phi2) * sinDeltaLambda * sinDeltaLambda;

    // Numerical clamping to avoid NaN due to floating-point rounding
    double clampedA = Math.max(0.0, Math.min(1.0, a));
    double c = 2.0 * Math.atan2(Math.sqrt(clampedA), Math.sqrt(1.0 - clampedA));

    return EARTH_RADIUS_KM * c;
  }

  /**
   * Evaluates travel velocity between a historical login and the current login.
   *
   * @param previousLocation Previous known geographic location
   * @param previousTimestamp Epoch timestamp of previous login in milliseconds
   * @param currentLocation Current geographic location
   * @param currentTimestamp Epoch timestamp of current login in milliseconds
   * @param maxVelocityKmh Commercial travel speed threshold in km/h
   * @return Optional containing TravelResult if both locations have valid coordinates, or empty otherwise
   */
  public static Optional<TravelResult> evaluateTravel(
      GeoLocation previousLocation,
      Long previousTimestamp,
      GeoLocation currentLocation,
      Long currentTimestamp,
      int maxVelocityKmh) {

    if (previousLocation == null || currentLocation == null
        || !previousLocation.hasCoordinates() || !currentLocation.hasCoordinates()
        || previousTimestamp == null || currentTimestamp == null) {
      return Optional.empty();
    }

    double distance = calculateHaversineDistance(
        previousLocation.latitude(), previousLocation.longitude(),
        currentLocation.latitude(), currentLocation.longitude());

    long elapsedMs = currentTimestamp - previousTimestamp;

    // If movement is negligible (within same city/district/substation), velocity is 0
    if (distance <= STATIONARY_THRESHOLD_KM) {
      return Optional.of(new TravelResult(distance, Math.max(0.0, elapsedMs / 3_600_000.0), 0.0, false));
    }

    // If timestamp difference is non-positive or sub-second and distance is significant, it's instant impossible travel
    if (elapsedMs <= 0) {
      return Optional.of(new TravelResult(distance, 0.0, Double.POSITIVE_INFINITY, true));
    }

    double durationHours = (double) elapsedMs / 3_600_000.0;
    double velocity = distance / durationHours;

    boolean impossible = velocity > maxVelocityKmh;
    return Optional.of(new TravelResult(distance, durationHours, velocity, impossible));
  }
}
