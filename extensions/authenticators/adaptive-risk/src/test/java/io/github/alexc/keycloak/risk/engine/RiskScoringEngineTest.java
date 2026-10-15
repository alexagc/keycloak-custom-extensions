package io.github.alexc.keycloak.risk.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.alexc.keycloak.risk.config.AdaptiveRiskConfig;
import io.github.alexc.keycloak.risk.model.GeoLocation;
import io.github.alexc.keycloak.risk.model.LoginContext;
import io.github.alexc.keycloak.risk.model.RiskEvaluationResult;
import io.github.alexc.keycloak.risk.model.RiskLevel;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.models.UserModel;

class RiskScoringEngineTest {

  private RiskScoringEngine engine;
  private AdaptiveRiskConfig config;

  @BeforeEach
  void setUp() {
    IpReputationChecker ipChecker = new IpReputationChecker(Set.of("198.51.100.99"));
    engine = new RiskScoringEngine(ipChecker);
    config = AdaptiveRiskConfig.defaultConfig();
  }

  @Test
  @DisplayName("Should evaluate familiar login as low risk without step-up or block")
  void testLowRiskFamiliarLogin() {
    UserModel user = mock(UserModel.class);
    when(user.getUsername()).thenReturn("john.doe");
    when(user.getFirstAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_LAT)).thenReturn("40.4168");
    when(user.getFirstAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_LON)).thenReturn("-3.7038");
    long twoHoursAgo = System.currentTimeMillis() - 7_200_000L;
    when(user.getFirstAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_TIMESTAMP)).thenReturn(String.valueOf(twoHoursAgo));
    when(user.getAttributeStream(DeviceFingerprintEvaluator.ATTR_KNOWN_FINGERPRINTS))
        .thenReturn(Stream.of("familiar-fp"));

    GeoLocation currentLocation = new GeoLocation(40.4200, -3.7000, "ES", "Madrid", "1.1.1.1");
    LoginContext loginContext = new LoginContext("1.1.1.1", "Mozilla", System.currentTimeMillis(), currentLocation, "familiar-fp");

    RiskEvaluationResult result = engine.evaluate(user, loginContext, config);

    assertEquals(RiskLevel.LOW, result.level());
    assertEquals(0, result.overallScore());
    assertFalse(result.shouldStepUp());
    assertFalse(result.shouldBlock());
    assertTrue(result.isLowRisk());
  }

  @Test
  @DisplayName("Should evaluate impossible travel as medium risk requiring Step-Up MFA")
  void testImpossibleTravelStepUp() {
    UserModel user = mock(UserModel.class);
    when(user.getUsername()).thenReturn("john.doe");
    when(user.getFirstAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_LAT)).thenReturn("40.4168"); // Madrid
    when(user.getFirstAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_LON)).thenReturn("-3.7038");
    long twentyMinsAgo = System.currentTimeMillis() - 1_200_000L;
    when(user.getFirstAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_TIMESTAMP)).thenReturn(String.valueOf(twentyMinsAgo));
    when(user.getAttributeStream(DeviceFingerprintEvaluator.ATTR_KNOWN_FINGERPRINTS))
        .thenReturn(Stream.of("familiar-fp"));

    // Current location is Tokyo (~10,765 km away 20 mins later)
    GeoLocation tokyo = new GeoLocation(35.6762, 139.6503, "JP", "Tokyo", "2.2.2.2");
    LoginContext loginContext = new LoginContext("2.2.2.2", "Mozilla", System.currentTimeMillis(), tokyo, "familiar-fp");

    RiskEvaluationResult result = engine.evaluate(user, loginContext, config);

    assertEquals(RiskLevel.MEDIUM, result.level());
    assertEquals(50, result.overallScore());
    assertTrue(result.shouldStepUp());
    assertFalse(result.shouldBlock());
  }

  @Test
  @DisplayName("Should evaluate combined TOR node + impossible travel as critical risk requiring block")
  void testCriticalRiskBlock() {
    UserModel user = mock(UserModel.class);
    when(user.getUsername()).thenReturn("john.doe");
    when(user.getFirstAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_LAT)).thenReturn("40.4168");
    when(user.getFirstAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_LON)).thenReturn("-3.7038");
    long tenMinsAgo = System.currentTimeMillis() - 600_000L;
    when(user.getFirstAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_TIMESTAMP)).thenReturn(String.valueOf(tenMinsAgo));
    when(user.getAttributeStream(DeviceFingerprintEvaluator.ATTR_KNOWN_FINGERPRINTS))
        .thenReturn(Stream.of("different-fp"));

    // IP is configured TOR node (198.51.100.99) from Tokyo
    GeoLocation tokyoTor = new GeoLocation(35.6762, 139.6503, "JP", "Tokyo", "198.51.100.99");
    LoginContext loginContext = new LoginContext("198.51.100.99", "Mozilla", System.currentTimeMillis(), tokyoTor, "new-fp");

    RiskEvaluationResult result = engine.evaluate(user, loginContext, config);

    // Impossible travel (50) + TOR (50) + New device (15) = 115 clamped to 100
    assertEquals(100, result.overallScore());
    assertEquals(RiskLevel.CRITICAL, result.level());
    assertTrue(result.shouldStepUp());
    assertTrue(result.shouldBlock());
  }

  @Test
  @DisplayName("Should assign baseline risk on first login without blocking")
  void testFirstLoginBaseline() {
    UserModel user = mock(UserModel.class);
    when(user.getUsername()).thenReturn("new.user");
    when(user.getFirstAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_LAT)).thenReturn(null);
    when(user.getAttributeStream(DeviceFingerprintEvaluator.ATTR_KNOWN_FINGERPRINTS)).thenReturn(Stream.empty());

    GeoLocation location = new GeoLocation(40.4168, -3.7038, "ES", "Madrid", "1.1.1.1");
    LoginContext loginContext = new LoginContext("1.1.1.1", "Mozilla", System.currentTimeMillis(), location, "fp-1");

    RiskEvaluationResult result = engine.evaluate(user, loginContext, config);

    // Baseline (10) + New device (15) = 25
    assertEquals(25, result.overallScore());
    assertEquals(RiskLevel.LOW, result.level());
    assertFalse(result.shouldStepUp());
    assertFalse(result.shouldBlock());
  }

  @Test
  @DisplayName("Should persist baseline user attributes on successful authentication")
  void testPersistSuccessBaseline() {
    UserModel user = mock(UserModel.class);
    GeoLocation loc = new GeoLocation(40.4168, -3.7038, "ES", "Madrid", "1.1.1.1");
    long now = 1700000000000L;

    engine.persistSuccessBaseline(user, loc, now, "fp-test");

    verify(user).setSingleAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_LAT, "40.4168");
    verify(user).setSingleAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_LON, "-3.7038");
    verify(user).setSingleAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_CITY, "Madrid");
    verify(user).setSingleAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_COUNTRY, "ES");
    verify(user).setSingleAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_IP, "1.1.1.1");
    verify(user).setSingleAttribute(RiskScoringEngine.ATTR_LAST_LOGIN_TIMESTAMP, "1700000000000");
  }
}
