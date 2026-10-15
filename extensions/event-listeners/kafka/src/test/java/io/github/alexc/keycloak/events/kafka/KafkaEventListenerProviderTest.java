package io.github.alexc.keycloak.events.kafka;

import io.github.alexc.keycloak.events.kafka.config.KafkaEventListenerConfig;
import io.github.alexc.keycloak.events.kafka.config.PartitionStrategy;
import io.github.alexc.keycloak.events.kafka.producer.KafkaProducerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaEventListenerProviderTest {

    private KeycloakSession session;
    private RealmModel realm;
    private KafkaProducerManager producerManager;

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        KeycloakContext context = mock(KeycloakContext.class);
        realm = mock(RealmModel.class);

        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        when(realm.getId()).thenReturn("demo-realm");
        when(realm.getName()).thenReturn("demo-realm");

        producerManager = mock(KafkaProducerManager.class);
    }

    private KafkaEventListenerConfig buildConfig(PartitionStrategy strategy, Set<String> includedEvents, boolean includeAdmin) {
        return new KafkaEventListenerConfig(
                "localhost:9092",
                "keycloak.events.{realm}",
                "keycloak.admin-events.{realm}",
                strategy,
                "1",
                "snappy",
                "PLAINTEXT",
                "PLAIN",
                null,
                500L,
                1000,
                includedEvents,
                includeAdmin
        );
    }

    @Test
    @DisplayName("Should dispatch user event to Kafka with USER partitioning key")
    void testDispatchUserEventWithUserKey() {
        KafkaEventListenerConfig config = buildConfig(PartitionStrategy.USER, Set.of(), true);
        KafkaEventListenerProvider provider = new KafkaEventListenerProvider(session, producerManager, config);

        Event event = new Event();
        event.setId("evt-1");
        event.setTime(System.currentTimeMillis());
        event.setType(EventType.LOGIN);
        event.setRealmId("demo-realm");
        event.setUserId("user-12345");
        event.setClientId("web-app");

        provider.onEvent(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(producerManager).sendAsync(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals("keycloak.events.demo-realm", topicCaptor.getValue());
        assertEquals("user-12345", keyCaptor.getValue());
        assertTrue(payloadCaptor.getValue().contains("\"type\":\"io.keycloak.user.LOGIN\""));
    }

    @Test
    @DisplayName("Should dispatch user event with REALM partitioning key")
    void testDispatchUserEventWithRealmKey() {
        KafkaEventListenerConfig config = buildConfig(PartitionStrategy.REALM, Set.of(), true);
        KafkaEventListenerProvider provider = new KafkaEventListenerProvider(session, producerManager, config);

        Event event = new Event();
        event.setId("evt-2");
        event.setType(EventType.LOGIN);
        event.setRealmId("demo-realm");
        event.setUserId("user-12345");

        provider.onEvent(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producerManager).sendAsync(anyString(), keyCaptor.capture(), anyString());
        assertEquals("demo-realm", keyCaptor.getValue());
    }

    @Test
    @DisplayName("Should dispatch user event with null key when NONE strategy is selected")
    void testDispatchUserEventWithNoKey() {
        KafkaEventListenerConfig config = buildConfig(PartitionStrategy.NONE, Set.of(), true);
        KafkaEventListenerProvider provider = new KafkaEventListenerProvider(session, producerManager, config);

        Event event = new Event();
        event.setId("evt-3");
        event.setType(EventType.LOGIN);
        event.setRealmId("demo-realm");
        event.setUserId("user-12345");

        provider.onEvent(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producerManager).sendAsync(anyString(), keyCaptor.capture(), anyString());
        assertNull(keyCaptor.getValue());
    }

    @Test
    @DisplayName("Should filter out user events not matching included event types")
    void testFilterExcludedUserEvents() {
        KafkaEventListenerConfig config = buildConfig(PartitionStrategy.USER, Set.of("LOGIN"), true);
        KafkaEventListenerProvider provider = new KafkaEventListenerProvider(session, producerManager, config);

        Event event = new Event();
        event.setType(EventType.REFRESH_TOKEN); // Not in ["LOGIN"]
        event.setRealmId("demo-realm");

        provider.onEvent(event);
        verify(producerManager, never()).sendAsync(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should dispatch admin event to Kafka admin topic")
    void testDispatchAdminEvent() {
        KafkaEventListenerConfig config = buildConfig(PartitionStrategy.USER, Set.of(), true);
        KafkaEventListenerProvider provider = new KafkaEventListenerProvider(session, producerManager, config);

        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setId("admin-evt-1");
        adminEvent.setRealmId("demo-realm");
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setResourceType(ResourceType.USER);
        adminEvent.setResourcePath("users/new-user");

        AuthDetails authDetails = new AuthDetails();
        authDetails.setUserId("admin-operator");
        adminEvent.setAuthDetails(authDetails);

        provider.onEvent(adminEvent, true);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(producerManager).sendAsync(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertEquals("keycloak.admin-events.demo-realm", topicCaptor.getValue());
        assertEquals("admin-operator", keyCaptor.getValue());
        assertTrue(payloadCaptor.getValue().contains("\"type\":\"io.keycloak.admin.CREATE\""));
    }

    @Test
    @DisplayName("Should skip admin event when includeAdminEvents is disabled")
    void testSkipAdminEventsWhenDisabled() {
        KafkaEventListenerConfig config = buildConfig(PartitionStrategy.USER, Set.of(), false);
        KafkaEventListenerProvider provider = new KafkaEventListenerProvider(session, producerManager, config);

        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setRealmId("demo-realm");

        provider.onEvent(adminEvent, true);
        verify(producerManager, never()).sendAsync(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should catch unexpected exceptions and never throw from onEvent")
    void testResilientExceptionHandling() {
        KafkaEventListenerConfig config = buildConfig(PartitionStrategy.USER, Set.of(), true);
        KafkaEventListenerProvider provider = new KafkaEventListenerProvider(session, producerManager, config);

        doThrow(new RuntimeException("Fatal producer crash")).when(producerManager)
                .sendAsync(anyString(), anyString(), anyString());

        Event event = new Event();
        event.setType(EventType.LOGIN);
        event.setRealmId("demo-realm");

        assertDoesNotThrow(() -> provider.onEvent(event));
    }
}
