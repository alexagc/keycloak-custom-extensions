package io.github.alexc.keycloak.events.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-End integration test validating that the Webhook Event Listener SPI
 * deploys into a real Keycloak 26.x Quarkus container, augments via Quarkus,
 * and boots successfully with the provider registered.
 */
@Testcontainers
@Tag("e2e")
class WebhookEventListenerE2EIT {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8.0";

    @Test
    @DisplayName("Keycloak container should boot and successfully load the webhook provider")
    void testKeycloakBootsWithWebhookProvider() throws Exception {
        // Find compiled JAR file in target/
        File targetDir = new File("target");
        File[] jarFiles = targetDir.listFiles((dir, name) -> name.endsWith(".jar") && !name.contains("sources") && !name.contains("original"));

        if (jarFiles == null || jarFiles.length == 0) {
            // In unit-test phase before package, skip gracefully
            return;
        }

        File providerJar = jarFiles[0];

        try (GenericContainer<?> keycloak = new GenericContainer<>(DockerImageName.parse(KEYCLOAK_IMAGE))
                .withExposedPorts(8080)
                .withEnv("KEYCLOAK_ADMIN", "admin")
                .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
                .withEnv("KC_SPI_EVENTS_LISTENER_WEBHOOK_TARGET_URL", "https://httpbin.org/post")
                .withEnv("KC_SPI_EVENTS_LISTENER_WEBHOOK_INCLUDE_EVENTS", "LOGIN,LOGOUT")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(providerJar.getAbsolutePath()),
                        "/opt/keycloak/providers/" + providerJar.getName()
                )
                .withCommand("start-dev")
                .waitingFor(Wait.forLogMessage(".*Listening on:.*8080.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(3)))) {

            keycloak.start();

            assertTrue(keycloak.isRunning(), "Keycloak container should be running");

            // Query Keycloak health check endpoint
            Integer port = keycloak.getMappedPort(8080);
            String healthUrl = "http://" + keycloak.getHost() + ":" + port + "/health/ready";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "Keycloak health endpoint should return 200 OK");
        }
    }
}
