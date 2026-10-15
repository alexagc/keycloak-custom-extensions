package io.github.alexc.keycloak.authenticator.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;

class DistributedRateLimiterAuthenticatorFactoryTest {

  private RateLimiterClient client;
  private RateLimiterConfig config;
  private DistributedRateLimiterAuthenticatorFactory factory;

  @BeforeEach
  void setUp() {
    client = mock(RateLimiterClient.class);
    config = RateLimiterConfig.defaultConfiguration();
    factory = new DistributedRateLimiterAuthenticatorFactory(client, config);
  }

  @Test
  @DisplayName("Factory metadata and capabilities comply with Keycloak standards")
  void testMetadata() {
    assertEquals(DistributedRateLimiterAuthenticatorFactory.PROVIDER_ID, factory.getId());
    assertEquals("Distributed Rate Limiter (Valkey/Redis)", factory.getDisplayType());
    assertEquals("rate-limiter", factory.getReferenceCategory());
    assertTrue(factory.isConfigurable());
    assertFalse(factory.isUserSetupAllowed());
    assertNotNull(factory.getHelpText());

    AuthenticationExecutionModel.Requirement[] choices = factory.getRequirementChoices();
    assertNotNull(choices);
    assertEquals(3, choices.length);
  }

  @Test
  @DisplayName("Config properties define all expected parameters")
  void testConfigProperties() {
    List<ProviderConfigProperty> props = factory.getConfigProperties();
    assertNotNull(props);
    assertEquals(9, props.size());

    assertTrue(props.stream().anyMatch(p -> p.getName().equals(RateLimiterConfig.CONF_REDIS_URI)));
    assertTrue(props.stream().anyMatch(p -> p.getName().equals(RateLimiterConfig.CONF_IP_LIMIT)));
    assertTrue(props.stream().anyMatch(p -> p.getName().equals(RateLimiterConfig.CONF_IP_WINDOW)));
    assertTrue(props.stream().anyMatch(p -> p.getName().equals(RateLimiterConfig.CONF_USER_LIMIT)));
    assertTrue(props.stream().anyMatch(p -> p.getName().equals(RateLimiterConfig.CONF_USER_WINDOW)));
    assertTrue(props.stream().anyMatch(p -> p.getName().equals(RateLimiterConfig.CONF_FAIL_OPEN)));
    assertTrue(props.stream().anyMatch(p -> p.getName().equals(RateLimiterConfig.CONF_CONNECT_TIMEOUT)));
    assertTrue(props.stream().anyMatch(p -> p.getName().equals(RateLimiterConfig.CONF_SOCKET_TIMEOUT)));
    assertTrue(props.stream().anyMatch(p -> p.getName().equals(RateLimiterConfig.CONF_MAX_POOL_SIZE)));
  }

  @Test
  @DisplayName("create returns non-null Authenticator instance")
  void testCreate() {
    KeycloakSession session = mock(KeycloakSession.class);
    Authenticator authenticator = factory.create(session);

    assertNotNull(authenticator);
    assertTrue(authenticator instanceof DistributedRateLimiterAuthenticator);
  }

  @Test
  @DisplayName("close closes client connection pool")
  void testClose() {
    factory.close();
    verify(client).close();
  }

  @Test
  @DisplayName("Default constructor instantiates factory for SPI registration")
  void testDefaultConstructor() {
    DistributedRateLimiterAuthenticatorFactory defaultFactory =
        new DistributedRateLimiterAuthenticatorFactory();
    assertEquals(DistributedRateLimiterAuthenticatorFactory.PROVIDER_ID, defaultFactory.getId());
    defaultFactory.postInit(null);
    defaultFactory.close(); // No client initialized, safe no-op
  }
}
