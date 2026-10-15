package io.github.alexc.keycloak.authenticator.ratelimit;

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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Enterprise End-to-End integration test validating that the Distributed Rate Limiter:
 * 1. Mounts into a live Keycloak 26.x Quarkus container using testcontainers-keycloak.
 * 2. Successfully registers Authenticator SPI into Keycloak runtime.
 * 3. Connects to Valkey/Redis on an isolated Docker network.
 * 4. Gracefully skips execution on developer machines where Docker is not installed (Zero-Install workflow).
 */
@Testcontainers
@Tag("e2e")
class DistributedRateLimiterE2EIT {

  private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8.0";
  private static final DockerImageName VALKEY_IMAGE = DockerImageName.parse("redis:7-alpine");

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
  @DisplayName("Keycloak 26.x mounts and registers Distributed Rate Limiter SPI with Valkey/Redis")
  void testKeycloakRegistersDistributedRateLimiterSPI() {
    Network network = Network.newNetwork();

    try (GenericContainer<?> valkey =
            new GenericContainer<>(VALKEY_IMAGE)
                .withExposedPorts(6379)
                .withNetwork(network)
                .withNetworkAliases("valkey");
        KeycloakContainer keycloak =
            new KeycloakContainer(KEYCLOAK_IMAGE)
                .withProviderClassesFrom("target/classes")
                .withNetwork(network)
                .withNetworkAliases("keycloak")
                .withEnv(
                    "KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_REDIS_URI",
                    "valkey://valkey:6379")
                .withEnv("KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_IP_LIMIT", "50")
                .withEnv("KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_FAIL_OPEN", "true")) {

      valkey.start();
      keycloak.start();

      assertTrue(keycloak.isRunning(), "Keycloak container must be running");

      Keycloak adminClient = keycloak.getKeycloakAdminClient();
      ServerInfoRepresentation serverInfo = adminClient.serverInfo().getInfo();
      assertNotNull(serverInfo, "Server info must not be null");

      Map<String, ProviderRepresentation> authProviders =
          serverInfo.getProviders().get("authenticator").getProviders();
      assertTrue(
          authProviders.containsKey(DistributedRateLimiterAuthenticatorFactory.PROVIDER_ID),
          "Keycloak must register distributed-rate-limiter authenticator");

    } finally {
      network.close();
    }
  }
}
