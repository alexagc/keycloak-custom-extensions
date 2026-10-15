package io.github.alexc.keycloak.risk.engine;

import io.github.alexc.keycloak.risk.config.AdaptiveRiskConfig;
import io.github.alexc.keycloak.risk.model.GeoLocation;
import io.github.alexc.keycloak.risk.model.LoginContext;
import io.github.alexc.keycloak.risk.model.RiskEvaluationResult;
import io.github.alexc.keycloak.risk.model.RiskFactor;
import io.github.alexc.keycloak.risk.model.RiskLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.keycloak.models.UserModel;

/**
 * Core risk scoring engine aggregating travel velocity, IP reputation, and device fingerprints into
 * a normalized composite risk score.
 */
public class RiskScoringEngine {

  private static final Logger LOG = Logger.getLogger(RiskScoringEngine.class);

  public static final String ATTR_LAST_LOGIN_LAT = "last_login_lat";
  public static final String ATTR_LAST_LOGIN_LON = "last_login_lon";
  public static final String ATTR_LAST_LOGIN_TIMESTAMP = "last_login_timestamp";
  public static final String ATTR_LAST_LOGIN_CITY = "last_login_city";
  public static final String ATTR_LAST_LOGIN_COUNTRY = "last_login_country";
  public static final String ATTR_LAST_LOGIN_IP = "last_login_ip";

  private final IpReputationChecker ipReputationChecker;

  /**
   * Constructs a RiskScoringEngine with default IP reputation checker.
   */
  public RiskScoringEngine() {
    this(new IpReputationChecker());
  }

  /**
   * Constructs a RiskScoringEngine with a custom IP reputation checker.
   *
   * @param ipReputationChecker IP reputation checker
   */
  public RiskScoringEngine(IpReputationChecker ipReputationChecker) {
    this.ipReputationChecker = (ipReputationChecker != null) ? ipReputationChecker : new IpReputationChecker();
  }

  /**
   * Evaluates overall risk for an authentication attempt.
   *
   * @param user Keycloak user model
   * @param context Current login context
   * @param config Adaptive risk configuration
   * @return RiskEvaluationResult
   */
  public RiskEvaluationResult evaluate(UserModel user, LoginContext context, AdaptiveRiskConfig config) {
    List<RiskFactor> factors = new ArrayList<>();

    // 1. Evaluate IP Reputation (TOR nodes & anonymous proxies)
    IpReputationChecker.ReputationResult repResult = ipReputationChecker.checkReputation(
        context.ipAddress(), config.blockTorNodes(), config.torRiskScore());

    if (repResult.riskScore() > 0) {
      factors.add(new RiskFactor("IP_REPUTATION", repResult.riskScore(), repResult.reason()));
    }

    // 2. Evaluate Device Fingerprint Anomaly
    DeviceFingerprintEvaluator.DeviceResult devResult = DeviceFingerprintEvaluator.evaluateDevice(
        user, context.deviceFingerprint(), config.newDeviceRiskScore());

    if (devResult.riskScore() > 0) {
      factors.add(new RiskFactor("DEVICE_ANOMALY", devResult.riskScore(), devResult.description()));
    }

    // 3. Evaluate Impossible Travel Velocity
    GeoLocation lastLocation = extractLastLocation(user);
    Long lastTimestamp = extractLastTimestamp(user);

    if (lastLocation != null && lastLocation.hasCoordinates() && lastTimestamp != null) {
      Optional<ImpossibleTravelCalculator.TravelResult> travelResult = ImpossibleTravelCalculator.evaluateTravel(
          lastLocation, lastTimestamp, context.geoLocation(), context.timestamp(), config.maxVelocityKmh());

      if (travelResult.isPresent() && travelResult.get().isImpossibleTravel()) {
        ImpossibleTravelCalculator.TravelResult tr = travelResult.get();
        String desc = String.format("Impossible velocity detected: %.1f km/h over %.1f km (max allowed: %d km/h)",
            tr.velocityKmh(), tr.distanceKm(), config.maxVelocityKmh());
        LOG.warnv("Impossible travel alert for user {0}: {1}", (user != null ? user.getUsername() : "unknown"), desc);
        factors.add(new RiskFactor("IMPOSSIBLE_TRAVEL", config.impossibleTravelRiskScore(), desc));
      }
    } else {
      // First login or missing historical location: assign controlled baseline risk
      factors.add(new RiskFactor("BASELINE_CONTEXT", config.baselineRiskScore(),
          "Initial login or unrecorded historical location baseline"));
    }

    // 4. Calculate Aggregate Score [0, 100]
    int rawScore = factors.stream().mapToInt(RiskFactor::points).sum();
    int overallScore = Math.min(100, Math.max(0, rawScore));

    RiskLevel level;
    boolean shouldStepUp;
    boolean shouldBlock;

    if (overallScore >= config.blockThreshold()) {
      level = RiskLevel.CRITICAL;
      shouldStepUp = true;
      shouldBlock = true;
    } else if (overallScore >= config.stepUpThreshold()) {
      level = RiskLevel.MEDIUM;
      shouldStepUp = true;
      shouldBlock = false;
    } else {
      level = RiskLevel.LOW;
      shouldStepUp = false;
      shouldBlock = false;
    }

    LOG.infov("Adaptive risk evaluation for user {0}: score={1}, level={2}, stepUp={3}, block={4}",
        (user != null ? user.getUsername() : "anonymous"), overallScore, level, shouldStepUp, shouldBlock);

    return new RiskEvaluationResult(overallScore, level, factors, shouldStepUp, shouldBlock);
  }

  /**
   * Persists the current verified location and timestamp into the user model upon successful authentication.
   *
   * @param user Keycloak user model
   * @param location Verified current location
   * @param timestamp Verification timestamp in milliseconds
   * @param deviceFingerprint Verified device fingerprint
   */
  public void persistSuccessBaseline(UserModel user, GeoLocation location, long timestamp, String deviceFingerprint) {
    if (user == null) {
      return;
    }

    if (location != null) {
      if (location.latitude() != null) {
        user.setSingleAttribute(ATTR_LAST_LOGIN_LAT, String.valueOf(location.latitude()));
      }
      if (location.longitude() != null) {
        user.setSingleAttribute(ATTR_LAST_LOGIN_LON, String.valueOf(location.longitude()));
      }
      if (location.cityName() != null) {
        user.setSingleAttribute(ATTR_LAST_LOGIN_CITY, location.cityName());
      }
      if (location.countryCode() != null) {
        user.setSingleAttribute(ATTR_LAST_LOGIN_COUNTRY, location.countryCode());
      }
      if (location.ipAddress() != null) {
        user.setSingleAttribute(ATTR_LAST_LOGIN_IP, location.ipAddress());
      }
    }
    user.setSingleAttribute(ATTR_LAST_LOGIN_TIMESTAMP, String.valueOf(timestamp));

    if (deviceFingerprint != null && !deviceFingerprint.isBlank()) {
      DeviceFingerprintEvaluator.recordDeviceFingerprint(user, deviceFingerprint);
    }
  }

  private static GeoLocation extractLastLocation(UserModel user) {
    if (user == null) {
      return null;
    }
    String latStr = user.getFirstAttribute(ATTR_LAST_LOGIN_LAT);
    String lonStr = user.getFirstAttribute(ATTR_LAST_LOGIN_LON);
    String city = user.getFirstAttribute(ATTR_LAST_LOGIN_CITY);
    String country = user.getFirstAttribute(ATTR_LAST_LOGIN_COUNTRY);
    String ip = user.getFirstAttribute(ATTR_LAST_LOGIN_IP);

    if (latStr == null || lonStr == null) {
      return null;
    }

    try {
      double lat = Double.parseDouble(latStr);
      double lon = Double.parseDouble(lonStr);
      return new GeoLocation(lat, lon, country, city, ip);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Long extractLastTimestamp(UserModel user) {
    if (user == null) {
      return null;
    }
    String tsStr = user.getFirstAttribute(ATTR_LAST_LOGIN_TIMESTAMP);
    if (tsStr == null) {
      return null;
    }
    try {
      return Long.parseLong(tsStr);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
