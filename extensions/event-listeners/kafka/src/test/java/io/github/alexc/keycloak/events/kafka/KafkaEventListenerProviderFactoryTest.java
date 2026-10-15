package io.github.alexc.keycloak.events.kafka;

import io.github.alexc.keycloak.events.kafka.config.KafkaEventListenerConfig;
import io.github.alexc.keycloak.events.kafka.config.PartitionStrategy;
import io.github.alexc.keycloak.events.kafka.producer.KafkaProducerManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.models.KeycloakSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaEventListenerProviderFactoryTest {

    @Test
    @DisplayName("Should return correct provider ID")
    void testGetId() {
        KafkaEventListenerProviderFactory factory = new KafkaEventListenerProviderFactory();
        assertEquals("kafka-audit-streamer", factory.getId());
    }

    @Test
    @DisplayName("Should initialize configuration from Config.Scope")
    void testInitFromScope() {
        KafkaEventListenerProviderFactory factory = new KafkaEventListenerProviderFactory();
        KafkaProducerManager mockProducerManager = mock(KafkaProducerManager.class);
        factory.setProducerManager(mockProducerManager);

        Config.Scope scope = mock(Config.Scope.class);
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_BOOTSTRAP_SERVERS)).thenReturn("kafka.corp:9092");
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_EVENTS_TOPIC)).thenReturn("corp.events.{realm}");
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_ADMIN_EVENTS_TOPIC)).thenReturn("corp.admin.{realm}");
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_PARTITION_STRATEGY)).thenReturn("REALM");
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_ACKS)).thenReturn("all");
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_COMPRESSION)).thenReturn("zstd");
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_SECURITY_PROTOCOL)).thenReturn("SASL_SSL");
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_SASL_MECHANISM)).thenReturn("SCRAM-SHA-512");
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_SASL_JAAS_CONFIG)).thenReturn("org.apache.kafka.common.security.scram.ScramLoginModule required;");
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_MAX_BLOCK_MS)).thenReturn("250");
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_BUFFER_CAPACITY)).thenReturn("2000");
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_INCLUDE_EVENTS)).thenReturn("LOGIN, LOGOUT");
        when(scope.get(KafkaEventListenerProviderFactory.CONFIG_INCLUDE_ADMIN_EVENTS)).thenReturn("false");

        factory.init(scope);

        KafkaEventListenerConfig config = factory.getConfig();
        assertNotNull(config);
        assertEquals("kafka.corp:9092", config.bootstrapServers());
        assertEquals("corp.events.{realm}", config.eventsTopicPattern());
        assertEquals("corp.admin.{realm}", config.adminEventsTopicPattern());
        assertEquals(PartitionStrategy.REALM, config.partitionStrategy());
        assertEquals("all", config.acks());
        assertEquals("zstd", config.compressionType());
        assertEquals("SASL_SSL", config.securityProtocol());
        assertEquals("SCRAM-SHA-512", config.saslMechanism());
        assertNotNull(config.saslJaasConfig());
        assertEquals(250L, config.maxBlockMs());
        assertEquals(2000, config.bufferCapacity());
        assertTrue(config.shouldDispatchUserEvent("LOGIN"));
        assertTrue(config.shouldDispatchUserEvent("LOGOUT"));
        assertFalse(config.shouldDispatchUserEvent("REGISTER"));
        assertFalse(config.shouldDispatchAdminEvent());
    }

    @Test
    @DisplayName("Should create request-scoped provider and close resources cleanly")
    void testCreateAndClose() {
        KafkaEventListenerProviderFactory factory = new KafkaEventListenerProviderFactory();
        KafkaProducerManager mockProducerManager = mock(KafkaProducerManager.class);
        factory.setProducerManager(mockProducerManager);

        Config.Scope scope = mock(Config.Scope.class);
        factory.init(scope);

        KeycloakSession session = mock(KeycloakSession.class);
        EventListenerProvider provider = factory.create(session);
        assertNotNull(provider);
        assertTrue(provider instanceof KafkaEventListenerProvider);

        factory.close();
        verify(mockProducerManager).close();
    }
}
