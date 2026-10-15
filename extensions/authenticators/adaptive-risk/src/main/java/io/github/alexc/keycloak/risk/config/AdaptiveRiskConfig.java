package io.github.alexc.keycloak.risk.config;

import java.util.Map;
import org.keycloak.Config;
import org.keycloak.models.AuthenticatorConfigModel;

/**
 * Configuration holder and factory for Zero-Trust Adaptive Risk Evaluator settings.
 *
 * @param maxVelocityKmh Maximum acceptable travel velocity between consecutive logins in km/h (default: 900)
 * @param stepUpThreshold Risk score threshold at or above which Step-Up MFA is challenged (default: 40)
 * @param blockThreshold Risk score threshold at or above which access is blocked (default: 75)
 * @param blockTorNodes Whether TOR exit node logins trigger a critical penalty (default: true)
 * @param torRiskScore Risk points added for TOR exit nodes (default: 50)
 * @param newDeviceRiskScore Risk points added for an unrecognised device/browser (default: 15)
 * @param impossibleTravelRiskScore Risk points added for impossible velocity (default: 50)
 * @param baselineRiskScore Baseline risk points for new user without login history (default: 10)
 * @param failOpen Whether failures during evaluation allow access rather than blocking (default: true)
 */
public record AdaptiveRiskConfig(
    int maxVelocityKmh,
    int stepUpThreshold,
    int blockThreshold,
    boolean blockTorNodes,
    int torRiskScore,
    int newDeviceRiskScore,
    int impossibleTravelRiskScore,
    int baselineRiskScore,
    boolean failOpen) {

  public static final String CONF_MAX_VELOCITY = "max-velocity-kmh";
  public static final String CONF_STEPUP_THRESHOLD = "step-up-threshold";
  public static final String CONF_BLOCK_THRESHOLD = "block-threshold";
  public static final String CONF_BLOCK_TOR = "block-tor-nodes";

  public static final String ENV_MAX_VELOCITY = "KC_SPI_AUTHENTICATOR_RISK_MAX_VELOCITY";
  public static final String ENV_STEPUP_THRESHOLD = "KC_SPI_AUTHENTICATOR_RISK_STEPUP_THRESHOLD";
  public static final String ENV_BLOCK_THRESHOLD = "KC_SPI_AUTHENTICATOR_RISK_BLOCK_THRESHOLD";
  public static final String ENV_BLOCK_TOR = "KC_SPI_AUTHENTICATOR_RISK_BLOCK_TOR";

  public static final int DEFAULT_MAX_VELOCITY = 900;
  public static final int DEFAULT_STEPUP_THRESHOLD = 40;
  public static final int DEFAULT_BLOCK_THRESHOLD = 75;
  public static final boolean DEFAULT_BLOCK_TOR = true;
  public static final int DEFAULT_TOR_RISK_SCORE = 50;
  public static final int DEFAULT_NEW_DEVICE_RISK_SCORE = 15;
  public static final int DEFAULT_IMPOSSIBLE_TRAVEL_RISK_SCORE = 50;
  public static final int DEFAULT_BASELINE_RISK_SCORE = 10;
  public static final boolean DEFAULT_FAIL_OPEN = true;

  /**
   * Creates a default configuration instance.
   *
   * @return default AdaptiveRiskConfig
   */
  public static AdaptiveRiskConfig defaultConfig() {
    return new AdaptiveRiskConfig(
        DEFAULT_MAX_VELOCITY,
        DEFAULT_STEPUP_THRESHOLD,
        DEFAULT_BLOCK_THRESHOLD,
        DEFAULT_BLOCK_TOR,
        DEFAULT_TOR_RISK_SCORE,
        DEFAULT_NEW_DEVICE_RISK_SCORE,
        DEFAULT_IMPOSSIBLE_TRAVEL_RISK_SCORE,
        DEFAULT_BASELINE_RISK_SCORE,
        DEFAULT_FAIL_OPEN);
  }

  /**
   * Creates an AdaptiveRiskConfig from Keycloak global SPI Scope with environment variable fallbacks.
   *
   * @param scope Global SPI configuration scope
   * @return Initialized AdaptiveRiskConfig
   */
  public static AdaptiveRiskConfig fromScope(Config.Scope scope) {
    int maxVel = getInt(scope, CONF_MAX_VELOCITY, ENV_MAX_VELOCITY, DEFAULT_MAX_VELOCITY);
    int stepUp = getInt(scope, CONF_STEPUP_THRESHOLD, ENV_STEPUP_THRESHOLD, DEFAULT_STEPUP_THRESHOLD);
    int block = getInt(scope, CONF_BLOCK_THRESHOLD, ENV_BLOCK_THRESHOLD, DEFAULT_BLOCK_THRESHOLD);
    boolean tor = getBoolean(scope, CONF_BLOCK_TOR, ENV_BLOCK_TOR, DEFAULT_BLOCK_TOR);

    return new AdaptiveRiskConfig(
        maxVel,
        stepUp,
        block,
        tor,
        DEFAULT_TOR_RISK_SCORE,
        DEFAULT_NEW_DEVICE_RISK_SCORE,
        DEFAULT_IMPOSSIBLE_TRAVEL_RISK_SCORE,
        DEFAULT_BASELINE_RISK_SCORE,
        DEFAULT_FAIL_OPEN);
  }

  /**
   * Overrides settings using authenticator execution configuration if available.
   *
   * @param baseConfig Base configuration from factory
   * @param configModel Execution configuration model
   * @return Merged AdaptiveRiskConfig
   */
  public static AdaptiveRiskConfig fromExecution(
      AdaptiveRiskConfig baseConfig, AuthenticatorConfigModel configModel) {
    if (configModel == null || configModel.getConfig() == null) {
      return baseConfig;
    }
    Map<String, String> map = configModel.getConfig();

    int maxVel = parseOrDefault(map.get(CONF_MAX_VELOCITY), baseConfig.maxVelocityKmh());
    int stepUp = parseOrDefault(map.get(CONF_STEPUP_THRESHOLD), baseConfig.stepUpThreshold());
    int block = parseOrDefault(map.get(CONF_BLOCK_THRESHOLD), baseConfig.blockThreshold());
    boolean tor = parseBoolOrDefault(map.get(CONF_BLOCK_TOR), baseConfig.blockTorNodes());

    return new AdaptiveRiskConfig(
        maxVel,
        stepUp,
        block,
        tor,
        baseConfig.torRiskScore(),
        baseConfig.newDeviceRiskScore(),
        baseConfig.impossibleTravelRiskScore(),
        baseConfig.baselineRiskScore(),
        baseConfig.failOpen());
  }

  private static int getInt(Config.Scope scope, String key, String envKey, int defaultValue) {
    if (scope != null) {
      Integer val = scope.getInt(key);
      if (val != null) {
        return val;
      }
    }
    String env = System.getenv(envKey);
    if (env != null && !env.isBlank()) {
      try {
        return Integer.parseInt(env.trim());
      } catch (NumberFormatException ignored) {
        // Fall back to default
      }
    }
    return defaultValue;
  }

  private static boolean getBoolean(Config.Scope scope, String key, String envKey, boolean defaultValue) {
    if (scope != null) {
      Boolean val = scope.getBoolean(key);
      if (val != null) {
        return val;
      }
    }
    String env = System.getenv(envKey);
    if (env != null && !env.isBlank()) {
      return Boolean.parseBoolean(env.trim());
    }
    return defaultValue;
  }

  private static int parseOrDefault(String value, int defaultValue) {
    if (value != null && !value.isBlank()) {
      try {
        return Integer.parseInt(value.trim());
      } catch (NumberFormatException ignored) {
        // Fall back to default
      }
    }
    return defaultValue;
  }

  private static boolean parseBoolOrDefault(String value, boolean defaultValue) {
    if (value != null && !value.isBlank()) {
      return Boolean.parseBoolean(value.trim());
    }
    return defaultValue;
  }
}
