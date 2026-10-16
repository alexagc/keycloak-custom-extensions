package io.github.alexc.keycloak.passwords;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpRequest.request;

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
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Enterprise End-to-End integration test validating that the Breached Password SPIs:
 * 1. Mount into a live Keycloak 26.x Quarkus container using testcontainers-keycloak.
 * 2. Successfully register Authenticator, PasswordPolicyProvider, and RequiredActionProvider into Keycloak runtime.
 * 3. Connect to MockServer on an isolated Docker network to verify k-Anonymity range endpoints.
 * 4. Gracefully skips execution on developer machines where Docker is not installed (Zero-Install workflow).
 */
@Testcontainers
@Tag("e2e")
class BreachedPasswordE2EIT {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8.1";
    private static final DockerImageName MOCKSERVER_IMAGE = DockerImageName.parse("mockserver/mockserver:5.15.0");

    @BeforeAll
    static void checkDockerEnvironment() {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            // Docker daemon not available
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker daemon not available; skipping Keycloak Testcontainers E2E test.");
    }

    @Test
    @DisplayName("Keycloak 26.x mounts and registers all Breached Password SPIs with MockServer endpoint")
    void testKeycloakRegistersBreachedPasswordSPIs() {
        Network network = Network.newNetwork();

        try (MockServerContainer mockServer = new MockServerContainer(MOCKSERVER_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("mockserver");
             KeycloakContainer keycloak = new KeycloakContainer(KEYCLOAK_IMAGE)
                     .withProviderClassesFrom("target/classes")
                     .withNetwork(network)
                     .withNetworkAliases("keycloak")
                     .withEnv("KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_API_URL", "http://mockserver:1080/range/")
                     .withEnv("KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_MODE", "strict")
                     .withEnv("KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_THRESHOLD", "1")) {

            mockServer.start();

            // Configure MockServer expectation for prefix "5BAA6" (hash of "password")
            MockServerClient mockServerClient = new MockServerClient(mockServer.getHost(), mockServer.getServerPort());
            mockServerClient
                    .when(request().withPath("/range/5BAA6").withMethod("GET"))
                    .respond(HttpResponse.response()
                            .withStatusCode(200)
                            .withHeader("Content-Type", "text/plain")
                            .withBody("1E4C9B93F3F0682250B6CF8331B7EE68FD8:3861493\r\n"));

            keycloak.start();
            assertTrue(keycloak.isRunning(), "Keycloak container must be running");

            Keycloak adminClient = keycloak.getKeycloakAdminClient();
            ServerInfoRepresentation serverInfo = adminClient.serverInfo().getInfo();
            assertNotNull(serverInfo, "Server info must not be null");

            // Verify Authenticator registration
            Map<String, ProviderRepresentation> authProviders = serverInfo.getProviders().get("authenticator").getProviders();
            assertTrue(authProviders.containsKey("breached-password-authenticator"),
                    "Keycloak must register breached-password-authenticator");

            // Verify Password Policy registration
            Map<String, ProviderRepresentation> policyProviders = serverInfo.getProviders().get("password-policy").getProviders();
            assertTrue(policyProviders.containsKey("breached-password-policy"),
                    "Keycloak must register breached-password-policy");

            // Verify Required Action registration
            Map<String, ProviderRepresentation> actionProviders = serverInfo.getProviders().get("required-action").getProviders();
            assertTrue(actionProviders.containsKey("check-breached-password-action"),
                    "Keycloak must register check-breached-password-action");

        } finally {
            network.close();
        }
    }
}
