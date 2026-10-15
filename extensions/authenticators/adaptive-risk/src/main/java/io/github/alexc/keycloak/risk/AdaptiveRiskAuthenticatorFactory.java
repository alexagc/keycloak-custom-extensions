package io.github.alexc.keycloak.risk;

import com.google.auto.service.AutoService;
import io.github.alexc.keycloak.risk.config.AdaptiveRiskConfig;
import io.github.alexc.keycloak.risk.engine.RiskScoringEngine;
import java.util.Collections;
import java.util.List;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Singleton factory and lifecycle manager for {@link AdaptiveRiskAuthenticator}.
 */
@AutoService(AuthenticatorFactory.class)
public class AdaptiveRiskAuthenticatorFactory implements AuthenticatorFactory {

  private static final Logger LOG = Logger.getLogger(AdaptiveRiskAuthenticatorFactory.class);

  public static final String PROVIDER_ID = "adaptive-risk-evaluator";
  public static final String DISPLAY_NAME = "Zero-Trust Adaptive Risk Evaluator";

  private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
      AuthenticationExecutionModel.Requirement.REQUIRED,
      AuthenticationExecutionModel.Requirement.ALTERNATIVE,
      AuthenticationExecutionModel.Requirement.CONDITIONAL,
      AuthenticationExecutionModel.Requirement.DISABLED
  };

  private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

  static {
    ProviderConfigProperty maxVel = new ProviderConfigProperty();
    maxVel.setName(AdaptiveRiskConfig.CONF_MAX_VELOCITY);
    maxVel.setLabel("Max Velocity (km/h)");
    maxVel.setType(ProviderConfigProperty.STRING_TYPE);
    maxVel.setDefaultValue(String.valueOf(AdaptiveRiskConfig.DEFAULT_MAX_VELOCITY));
    maxVel.setHelpText("Maximum travel velocity in km/h admissible between two consecutive logins (default: 900 km/h).");

    ProviderConfigProperty stepUp = new ProviderConfigProperty();
    stepUp.setName(AdaptiveRiskConfig.CONF_STEPUP_THRESHOLD);
    stepUp.setLabel("Step-Up MFA Threshold (0-100)");
    stepUp.setType(ProviderConfigProperty.STRING_TYPE);
    stepUp.setDefaultValue(String.valueOf(AdaptiveRiskConfig.DEFAULT_STEPUP_THRESHOLD));
    stepUp.setHelpText("Risk score at or above which Step-Up MFA is challenged (default: 40).");

    ProviderConfigProperty block = new ProviderConfigProperty();
    block.setName(AdaptiveRiskConfig.CONF_BLOCK_THRESHOLD);
    block.setLabel("Block Threshold (0-100)");
    block.setType(ProviderConfigProperty.STRING_TYPE);
    block.setDefaultValue(String.valueOf(AdaptiveRiskConfig.DEFAULT_BLOCK_THRESHOLD));
    block.setHelpText("Risk score at or above which access is blocked preventively (default: 75).");

    ProviderConfigProperty tor = new ProviderConfigProperty();
    tor.setName(AdaptiveRiskConfig.CONF_BLOCK_TOR);
    tor.setLabel("Block TOR Exit Nodes");
    tor.setType(ProviderConfigProperty.BOOLEAN_TYPE);
    tor.setDefaultValue(String.valueOf(AdaptiveRiskConfig.DEFAULT_BLOCK_TOR));
    tor.setHelpText("Assign high risk penalty to logins originating from verified TOR exit nodes (default: true).");

    CONFIG_PROPERTIES = Collections.unmodifiableList(List.of(maxVel, stepUp, block, tor));
  }

  private RiskScoringEngine scoringEngine;
  private AdaptiveRiskConfig baseConfig;

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getDisplayType() {
    return DISPLAY_NAME;
  }

  @Override
  public String getHelpText() {
    return "Evaluates contextual login risk based on impossible travel velocity, IP reputation, and device anomalies, enforcing dynamic Step-Up MFA or blocking suspicious logins.";
  }

  @Override
  public String getReferenceCategory() {
    return "risk-evaluator";
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
    return REQUIREMENT_CHOICES.clone();
  }

  @Override
  public boolean isUserSetupAllowed() {
    return false;
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return CONFIG_PROPERTIES;
  }

  @Override
  public Authenticator create(KeycloakSession session) {
    return new AdaptiveRiskAuthenticator(session, scoringEngine, baseConfig);
  }

  @Override
  public void init(Config.Scope config) {
    this.baseConfig = AdaptiveRiskConfig.fromScope(config);
    this.scoringEngine = new RiskScoringEngine();
    LOG.infov("Initialized AdaptiveRiskAuthenticatorFactory with baseConfig={0}", baseConfig);
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    // Post-initialization if needed
  }

  @Override
  public void close() {
    // Factory shutdown cleanup
  }

  /**
   * Package-private getter for testing base configuration.
   */
  AdaptiveRiskConfig getBaseConfig() {
    return baseConfig;
  }
}
