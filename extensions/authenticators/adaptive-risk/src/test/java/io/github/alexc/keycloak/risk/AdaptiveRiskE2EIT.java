package io.github.alexc.keycloak.risk;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.info.ProviderRepresentation;
import org.keycloak.representations.info.ServerInfoRepresentation;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Enterprise End-to-End integration test validating that the Zero-Trust Adaptive Risk Evaluator:
 * 1. Mounts into a live Keycloak 26.x Quarkus container using testcontainers-keycloak.
 * 2. Successfully registers Authenticator SPI into Keycloak runtime via AutoService.
 * 3. Gracefully skips execution on developer workstations where Docker is not installed (Zero-Install workflow).
 */
@Testcontainers
@Tag("e2e")
class AdaptiveRiskE2EIT {

  private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8.0";

  @BeforeAll
  static void checkDockerEnvironment() {
    boolean dockerAvailable = false;
    try {
      dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable ignored) {
      // Docker daemon not available
    }
    Assumptions.assumeTrue(
        dockerAvailable, "Docker daemon not available; skipping Keycloak Testcontainers E2E test.");
  }

  @Test
  @DisplayName("Keycloak 26.x mounts and registers Zero-Trust Adaptive Risk Evaluator SPI")
  void testKeycloakRegistersAdaptiveRiskSPI() {
    try (KeycloakContainer keycloak =
        new KeycloakContainer(KEYCLOAK_IMAGE)
            .withProviderClassesFrom("target/classes")
            .withEnv("KC_SPI_AUTHENTICATOR_RISK_MAX_VELOCITY", "900")
            .withEnv("KC_SPI_AUTHENTICATOR_RISK_STEPUP_THRESHOLD", "40")
            .withEnv("KC_SPI_AUTHENTICATOR_RISK_BLOCK_THRESHOLD", "75")
            .withEnv("KC_SPI_AUTHENTICATOR_RISK_BLOCK_TOR", "true")) {

      keycloak.start();
      assertTrue(keycloak.isRunning(), "Keycloak container must be running");

      Keycloak adminClient = keycloak.getKeycloakAdminClient();
      ServerInfoRepresentation serverInfo = adminClient.serverInfo().getInfo();
      assertNotNull(serverInfo, "Server info must not be null");

      Map<String, ProviderRepresentation> authProviders =
          serverInfo.getProviders().get("authenticator").getProviders();
      assertTrue(
          authProviders.containsKey(AdaptiveRiskAuthenticatorFactory.PROVIDER_ID),
          "Keycloak must register adaptive-risk-evaluator authenticator SPI");
    }
  }
}
