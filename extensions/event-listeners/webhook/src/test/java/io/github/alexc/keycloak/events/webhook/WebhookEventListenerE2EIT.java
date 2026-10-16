package io.github.alexc.keycloak.events.webhook;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpRequest.request;

/**
 * Enterprise End-to-End integration test validating that the Webhook Event Listener SPI:
 * 1. Mounts into a real Keycloak 26.x Quarkus container using testcontainers-keycloak.
 * 2. Successfully registers into Keycloak's event listener pipeline.
 * 3. Asynchronously dispatches live events to a dedicated MockServer container on the same Docker network.
 * 4. Gracefully skips execution on developer machines where Docker is not installed (Zero-Install workflow).
 */
@Testcontainers
@Tag("e2e")
class WebhookEventListenerE2EIT {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8.1";
    private static final DockerImageName MOCKSERVER_IMAGE = DockerImageName.parse("mockserver/mockserver:5.15.0");

    @BeforeAll
    static void checkDockerEnvironment() {
        // Zero-install support: Skip gracefully on local machines without Docker; runs in GitHub Actions CI
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            // Docker not running or not installed
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker daemon not available; skipping Keycloak Testcontainers E2E test.");
    }

    @Test
    @DisplayName("Keycloak should dispatch real administrative events to MockServer webhook")
    void testRealKeycloakDispatchesWebhookToMockServer() {
        Network network = Network.newNetwork();

        try (MockServerContainer mockServer = new MockServerContainer(MOCKSERVER_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("mockserver");
             KeycloakContainer keycloak = new KeycloakContainer(KEYCLOAK_IMAGE)
                     .withProviderClassesFrom("target/classes")
                     .withNetwork(network)
                     .withNetworkAliases("keycloak")
                     .withEnv("KC_SPI_EVENTS_LISTENER_WEBHOOK_TARGET_URL", "http://mockserver:1080/webhook")
                     .withEnv("KC_SPI_EVENTS_LISTENER_WEBHOOK_INCLUDE_ADMIN_EVENTS", "true")) {

            mockServer.start();

            // Configure MockServer expectation
            MockServerClient mockServerClient = new MockServerClient(mockServer.getHost(), mockServer.getServerPort());
            mockServerClient
                    .when(request().withPath("/webhook").withMethod("POST"))
                    .respond(HttpResponse.response().withStatusCode(200));

            // Start Keycloak with the Webhook Event Listener registered in the master realm
            keycloak.start();
            assertTrue(keycloak.isRunning(), "Keycloak container must be running");

            // Use the pre-authenticated Keycloak Admin Client
            Keycloak adminClient = keycloak.getKeycloakAdminClient();

            // Enable 'webhook' event listener in master realm
            RealmRepresentation masterRealm = adminClient.realm("master").toRepresentation();
            List<String> eventListeners = masterRealm.getEventsListeners();
            if (eventListeners == null || !eventListeners.contains("webhook")) {
                masterRealm.setEventsListeners(Collections.singletonList("webhook"));
                adminClient.realm("master").update(masterRealm);
            }

            // Trigger an administrative event by creating a new test realm
            RealmRepresentation testRealm = new RealmRepresentation();
            testRealm.setRealm("test-e2e-realm");
            testRealm.setEnabled(true);
            adminClient.realms().create(testRealm);

            // Verify MockServer received the webhook POST dispatch
            HttpRequest[] recordedRequests = mockServerClient.retrieveRecordedRequests(
                    request().withPath("/webhook").withMethod("POST")
            );

            assertTrue(recordedRequests.length >= 1, "MockServer should have received at least one webhook dispatch from Keycloak");
        } finally {
            network.close();
        }
    }
}
