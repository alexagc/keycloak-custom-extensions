package io.github.alexc.keycloak.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.alexc.keycloak.risk.config.AdaptiveRiskConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.Config;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;

class AdaptiveRiskAuthenticatorFactoryTest {

  @Test
  @DisplayName("Should verify factory metadata, provider ID, and requirements")
  void testFactoryMetadata() {
    AdaptiveRiskAuthenticatorFactory factory = new AdaptiveRiskAuthenticatorFactory();

    assertEquals("adaptive-risk-evaluator", factory.getId());
    assertEquals("Zero-Trust Adaptive Risk Evaluator", factory.getDisplayType());
    assertEquals("risk-evaluator", factory.getReferenceCategory());
    assertTrue(factory.isConfigurable());
    assertFalse(factory.isUserSetupAllowed());
    assertNotNull(factory.getHelpText());

    AuthenticationExecutionModel.Requirement[] requirements = factory.getRequirementChoices();
    assertEquals(4, requirements.length);
  }

  @Test
  @DisplayName("Should expose expected SPI configuration properties")
  void testConfigProperties() {
    AdaptiveRiskAuthenticatorFactory factory = new AdaptiveRiskAuthenticatorFactory();
    List<ProviderConfigProperty> properties = factory.getConfigProperties();

    assertNotNull(properties);
    assertEquals(4, properties.size());

    assertTrue(properties.stream().anyMatch(p -> p.getName().equals(AdaptiveRiskConfig.CONF_MAX_VELOCITY)));
    assertTrue(properties.stream().anyMatch(p -> p.getName().equals(AdaptiveRiskConfig.CONF_STEPUP_THRESHOLD)));
    assertTrue(properties.stream().anyMatch(p -> p.getName().equals(AdaptiveRiskConfig.CONF_BLOCK_THRESHOLD)));
    assertTrue(properties.stream().anyMatch(p -> p.getName().equals(AdaptiveRiskConfig.CONF_BLOCK_TOR)));
  }

  @Test
  @DisplayName("Should initialize base configuration and create authenticator instances")
  void testInitAndCreate() {
    AdaptiveRiskAuthenticatorFactory factory = new AdaptiveRiskAuthenticatorFactory();
    Config.Scope scope = mock(Config.Scope.class);
    when(scope.getInt(AdaptiveRiskConfig.CONF_MAX_VELOCITY)).thenReturn(1000);
    when(scope.getInt(AdaptiveRiskConfig.CONF_STEPUP_THRESHOLD)).thenReturn(45);
    when(scope.getInt(AdaptiveRiskConfig.CONF_BLOCK_THRESHOLD)).thenReturn(80);
    when(scope.getBoolean(AdaptiveRiskConfig.CONF_BLOCK_TOR)).thenReturn(true);

    factory.init(scope);

    assertEquals(1000, factory.getBaseConfig().maxVelocityKmh());
    assertEquals(45, factory.getBaseConfig().stepUpThreshold());
    assertEquals(80, factory.getBaseConfig().blockThreshold());

    KeycloakSession session = mock(KeycloakSession.class);
    AdaptiveRiskAuthenticator authenticator = (AdaptiveRiskAuthenticator) factory.create(session);
    assertNotNull(authenticator);
    assertTrue(authenticator.requiresUser());
  }
}
