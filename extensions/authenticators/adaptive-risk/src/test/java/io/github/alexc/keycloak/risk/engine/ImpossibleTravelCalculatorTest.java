package io.github.alexc.keycloak.risk.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alexc.keycloak.risk.model.GeoLocation;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImpossibleTravelCalculatorTest {

  // Coordinates
  private static final double MADRID_LAT = 40.4168;
  private static final double MADRID_LON = -3.7038;
  private static final double BARCELONA_LAT = 41.3879;
  private static final double BARCELONA_LON = 2.1699;
  private static final double TOKYO_LAT = 35.6762;
  private static final double TOKYO_LON = 139.6503;

  @Test
  @DisplayName("Should accurately calculate Madrid to Barcelona distance (~505 km)")
  void testMadridToBarcelonaDistance() {
    double distance = ImpossibleTravelCalculator.calculateHaversineDistance(
        MADRID_LAT, MADRID_LON, BARCELONA_LAT, BARCELONA_LON);

    // Expected great-circle distance is roughly 505 km
    assertEquals(505.0, distance, 10.0, "Madrid to Barcelona distance should be ~505 km");
  }

  @Test
  @DisplayName("Should accurately calculate Madrid to Tokyo distance (~10,765 km)")
  void testMadridToTokyoDistance() {
    double distance = ImpossibleTravelCalculator.calculateHaversineDistance(
        MADRID_LAT, MADRID_LON, TOKYO_LAT, TOKYO_LON);

    assertEquals(10765.0, distance, 50.0, "Madrid to Tokyo distance should be ~10,765 km");
  }

  @Test
  @DisplayName("Should return 0 km for identical coordinates")
  void testIdenticalCoordinatesDistance() {
    double distance = ImpossibleTravelCalculator.calculateHaversineDistance(
        MADRID_LAT, MADRID_LON, MADRID_LAT, MADRID_LON);

    assertEquals(0.0, distance, 0.001);
  }

  @Test
  @DisplayName("Should accept plausible travel velocity (Madrid to Barcelona in 3 hours ~168 km/h)")
  void testPlausibleTravelVelocity() {
    GeoLocation madrid = new GeoLocation(MADRID_LAT, MADRID_LON, "ES", "Madrid", "1.1.1.1");
    GeoLocation barcelona = new GeoLocation(BARCELONA_LAT, BARCELONA_LON, "ES", "Barcelona", "2.2.2.2");

    long now = System.currentTimeMillis();
    long threeHoursAgo = now - (3 * 3_600_000L);

    Optional<ImpossibleTravelCalculator.TravelResult> result = ImpossibleTravelCalculator.evaluateTravel(
        madrid, threeHoursAgo, barcelona, now, 900);

    assertTrue(result.isPresent());
    assertFalse(result.get().isImpossibleTravel(), "168 km/h should NOT be impossible travel");
    assertEquals(168.0, result.get().velocityKmh(), 5.0);
  }

  @Test
  @DisplayName("Should detect supersonic impossible travel (Madrid to Tokyo in 30 minutes)")
  void testImpossibleTravelTokyo() {
    GeoLocation madrid = new GeoLocation(MADRID_LAT, MADRID_LON, "ES", "Madrid", "1.1.1.1");
    GeoLocation tokyo = new GeoLocation(TOKYO_LAT, TOKYO_LON, "JP", "Tokyo", "3.3.3.3");

    long now = System.currentTimeMillis();
    long thirtyMinsAgo = now - (30 * 60_000L); // 0.5 hours

    Optional<ImpossibleTravelCalculator.TravelResult> result = ImpossibleTravelCalculator.evaluateTravel(
        madrid, thirtyMinsAgo, tokyo, now, 900);

    assertTrue(result.isPresent());
    assertTrue(result.get().isImpossibleTravel(), ">20,000 km/h MUST be flagged as impossible travel");
    assertTrue(result.get().velocityKmh() > 20000.0);
  }

  @Test
  @DisplayName("Should treat stationary movement (<= 1 km) as velocity 0")
  void testStationaryMovement() {
    GeoLocation loc1 = new GeoLocation(MADRID_LAT, MADRID_LON, "ES", "Madrid", "1.1.1.1");
    GeoLocation loc2 = new GeoLocation(MADRID_LAT + 0.0001, MADRID_LON + 0.0001, "ES", "Madrid", "1.1.1.1");

    long now = System.currentTimeMillis();
    long fiveMinsAgo = now - 300_000L;

    Optional<ImpossibleTravelCalculator.TravelResult> result = ImpossibleTravelCalculator.evaluateTravel(
        loc1, fiveMinsAgo, loc2, now, 900);

    assertTrue(result.isPresent());
    assertFalse(result.get().isImpossibleTravel());
    assertEquals(0.0, result.get().velocityKmh());
  }

  @Test
  @DisplayName("Should flag zero elapsed time between distant points as impossible travel")
  void testZeroElapsedTimeImpossibleTravel() {
    GeoLocation madrid = new GeoLocation(MADRID_LAT, MADRID_LON, "ES", "Madrid", "1.1.1.1");
    GeoLocation barcelona = new GeoLocation(BARCELONA_LAT, BARCELONA_LON, "ES", "Barcelona", "2.2.2.2");

    long now = System.currentTimeMillis();

    Optional<ImpossibleTravelCalculator.TravelResult> result = ImpossibleTravelCalculator.evaluateTravel(
        madrid, now, barcelona, now, 900);

    assertTrue(result.isPresent());
    assertTrue(result.get().isImpossibleTravel());
  }

  @Test
  @DisplayName("Should return empty when coordinates are missing")
  void testMissingCoordinatesReturnsEmpty() {
    GeoLocation noCoords = GeoLocation.empty("1.1.1.1");
    GeoLocation madrid = new GeoLocation(MADRID_LAT, MADRID_LON, "ES", "Madrid", "2.2.2.2");

    Optional<ImpossibleTravelCalculator.TravelResult> result = ImpossibleTravelCalculator.evaluateTravel(
        noCoords, System.currentTimeMillis() - 1000, madrid, System.currentTimeMillis(), 900);

    assertTrue(result.isEmpty());
  }
}
