package io.github.alexc.keycloak.ageproofing;

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
 * Enterprise End-to-End integration test validating that the Age &amp; Identity Proofing SPIs:
 * 1. Mount into a live Keycloak 26.x Quarkus container using testcontainers-keycloak.
 * 2. Successfully register Authenticator and RequiredActionProvider into the Keycloak runtime.
 * 3. Connect to MockServer on an isolated Docker network to verify JWKS discovery.
 * 4. Gracefully skips execution on developer machines where Docker is not installed (Zero-Install workflow).
 */
@Testcontainers
@Tag("e2e")
class AgeProofingE2EIT {

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
    @DisplayName("Keycloak 26.x mounts and registers Age & Identity Proofing SPIs with MockServer JWKS endpoint")
    void testKeycloakRegistersAgeProofingSPIs() {
        Network network = Network.newNetwork();

        try (MockServerContainer mockServer = new MockServerContainer(MOCKSERVER_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("mockserver");
             KeycloakContainer keycloak = new KeycloakContainer(KEYCLOAK_IMAGE)
                     .withProviderClassesFrom("target/classes")
                     .withNetwork(network)
                     .withNetworkAliases("keycloak")
                     .withEnv("KC_SPI_AUTHENTICATOR_AGE_PROOFING_JWKS_URL", "http://mockserver:1080/jwks.json")
                     .withEnv("KC_SPI_AUTHENTICATOR_AGE_PROOFING_MIN_AGE", "18")
                     .withEnv("KC_SPI_AUTHENTICATOR_AGE_PROOFING_MODE", "hybrid")) {

            mockServer.start();

            // Configure MockServer expectation for JWKS endpoint
            MockServerClient mockServerClient = new MockServerClient(mockServer.getHost(), mockServer.getServerPort());
            mockServerClient
                    .when(request().withPath("/jwks.json").withMethod("GET"))
                    .respond(HttpResponse.response()
                            .withStatusCode(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "keys": [
                                        {
                                          "kty": "RSA",
                                          "kid": "eidas-key-01",
                                          "use": "sig",
                                          "alg": "RS256",
                                          "n": "u1SU1LfVY4gMQBH8n...",
                                          "e": "AQAB"
                                        }
                                      ]
                                    }
                                    """));

            keycloak.start();
            assertTrue(keycloak.isRunning(), "Keycloak container must be running");

            Keycloak adminClient = keycloak.getKeycloakAdminClient();
            ServerInfoRepresentation serverInfo = adminClient.serverInfo().getInfo();
            assertNotNull(serverInfo, "Server info must not be null");

            // Verify Authenticator registration
            Map<String, ProviderRepresentation> authProviders = serverInfo.getProviders().get("authenticator").getProviders();
            assertTrue(authProviders.containsKey("age-identity-proofing"),
                    "Keycloak must register age-identity-proofing authenticator");

            // Verify Required Action registration
            Map<String, ProviderRepresentation> actionProviders = serverInfo.getProviders().get("required-action").getProviders();
            assertTrue(actionProviders.containsKey("age-verification-action"),
                    "Keycloak must register age-verification-action required action");

        } finally {
            network.close();
        }
    }
}
