package io.github.alexc.keycloak.events.kafka;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-End integration test validating that the Kafka Event Listener SPI:
 * 1. Mounts into a real Keycloak 26.x Quarkus container.
 * 2. Connects to an Apache Kafka broker in KRaft mode on an isolated Docker network.
 * 3. Asynchronously streams administrative events formatted as CloudEvents 1.0 JSON.
 * 4. Gracefully skips execution on local developer workstations without Docker (Zero-Install workflow).
 */
@Testcontainers
@Tag("e2e")
class KafkaEventListenerE2EIT {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8.1";
    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:3.8.0");

    @BeforeAll
    static void checkDockerEnvironment() {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            // Docker daemon not available
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker daemon not available; skipping Keycloak Kafka Testcontainers E2E test.");
    }

    @Test
    @DisplayName("Keycloak should stream administrative CloudEvents to Apache Kafka")
    void testRealKeycloakStreamsAuditEventsToKafka() {
        Network network = Network.newNetwork();

        try (KafkaContainer kafka = new KafkaContainer(KAFKA_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("kafka");
             KeycloakContainer keycloak = new KeycloakContainer(KEYCLOAK_IMAGE)
                     .withProviderClassesFrom("target/classes")
                     .withNetwork(network)
                     .withNetworkAliases("keycloak")
                     .withEnv("KC_SPI_EVENT_LISTENER_KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
                     .withEnv("KC_SPI_EVENT_LISTENER_KAFKA_EVENTS_TOPIC", "keycloak.events.{realm}")
                     .withEnv("KC_SPI_EVENT_LISTENER_KAFKA_ADMIN_TOPIC", "keycloak.admin-events.{realm}")
                     .withEnv("KC_SPI_EVENT_LISTENER_KAFKA_INCLUDE_ADMIN_EVENTS", "true")) {

            kafka.start();
            assertTrue(kafka.isRunning(), "Kafka container must be running");

            keycloak.start();
            assertTrue(keycloak.isRunning(), "Keycloak container must be running with Kafka provider mounted");

            Keycloak adminClient = keycloak.getKeycloakAdminClient();

            // Register 'kafka-audit-streamer' in the master realm
            RealmRepresentation masterRealm = adminClient.realm("master").toRepresentation();
            List<String> listeners = masterRealm.getEventsListeners();
            if (listeners == null || !listeners.contains(KafkaEventListenerProviderFactory.PROVIDER_ID)) {
                masterRealm.setEventsListeners(Collections.singletonList(KafkaEventListenerProviderFactory.PROVIDER_ID));
                adminClient.realm("master").update(masterRealm);
            }

            // Trigger an administrative event by creating a new realm
            RealmRepresentation testRealm = new RealmRepresentation();
            testRealm.setRealm("kafka-e2e-test-realm");
            testRealm.setEnabled(true);
            adminClient.realms().create(testRealm);

            // Verify via Kafka Consumer that the CloudEvent was streamed
            Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-audit-verifier");
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
                consumer.subscribe(Pattern.compile("keycloak\\.admin-events\\..*"));

                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
                assertFalse(records.isEmpty(), "Kafka should have received administrative CloudEvents from Keycloak");

                records.forEach(record -> {
                    assertTrue(record.value().contains("\"specversion\":\"1.0\""));
                    assertTrue(record.value().contains("\"type\":\"io.keycloak.admin."));
                });
            }
        } finally {
            network.close();
        }
    }
}
