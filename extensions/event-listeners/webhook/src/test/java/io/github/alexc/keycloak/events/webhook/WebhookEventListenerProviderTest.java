package io.github.alexc.keycloak.events.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookEventListenerProviderTest {

    private KeycloakSession session;
    private RealmProvider realmProvider;
    private RealmModel realmModel;
    private HttpClient httpClient;
    private HttpResponse<Void> httpResponse;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        session = mock(KeycloakSession.class);
        realmProvider = mock(RealmProvider.class);
        realmModel = mock(RealmModel.class);
        httpClient = mock(HttpClient.class);
        httpResponse = mock(HttpResponse.class);

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("test-realm-id")).thenReturn(realmModel);
        when(realmModel.getName()).thenReturn("master");

        when(httpResponse.statusCode()).thenReturn(200);
        doReturn(CompletableFuture.completedFuture(httpResponse))
                .when(httpClient).sendAsync(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("Should dispatch user event when event type is included in configuration")
    void testDispatchUserEventSuccess() {
        WebhookEventListenerConfig config = new WebhookEventListenerConfig(
                URI.create("https://webhook.site/events"),
                "Bearer secret-token",
                Set.of("LOGIN", "REGISTER"),
                true,
                Duration.ofSeconds(3),
                Duration.ofSeconds(5)
        );

        WebhookEventListenerProvider provider = new WebhookEventListenerProvider(session, httpClient, config);

        Event event = new Event();
        event.setId("evt-123");
        event.setTime(System.currentTimeMillis());
        event.setType(EventType.LOGIN);
        event.setRealmId("test-realm-id");
        event.setClientId("my-app");
        event.setUserId("user-456");
        event.setIpAddress("192.168.1.1");
        event.setDetails(Map.of("username", "john_doe"));

        provider.onEvent(event);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(1)).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals("POST", capturedRequest.method());
        assertEquals(URI.create("https://webhook.site/events"), capturedRequest.uri());
        assertEquals("application/json", capturedRequest.headers().firstValue("Content-Type").orElse(""));
        assertEquals("Bearer secret-token", capturedRequest.headers().firstValue("Authorization").orElse(""));
        assertEquals("user", capturedRequest.headers().firstValue("X-Keycloak-Event-Category").orElse(""));
    }

    @Test
    @DisplayName("Should skip user event if event type is not in included types")
    void testSkipUserEventWhenFilteredOut() {
        WebhookEventListenerConfig config = new WebhookEventListenerConfig(
                URI.create("https://webhook.site/events"),
                null,
                Set.of("LOGIN"), // Only LOGIN is allowed
                true,
                Duration.ofSeconds(3),
                Duration.ofSeconds(5)
        );

        WebhookEventListenerProvider provider = new WebhookEventListenerProvider(session, httpClient, config);

        Event event = new Event();
        event.setType(EventType.LOGOUT); // Should be skipped

        provider.onEvent(event);

        verify(httpClient, never()).sendAsync(any(), any());
    }

    @Test
    @DisplayName("Should dispatch admin event when admin events are enabled")
    void testDispatchAdminEventSuccess() {
        WebhookEventListenerConfig config = new WebhookEventListenerConfig(
                URI.create("https://webhook.site/events"),
                null,
                Collections.emptySet(),
                true, // Admin events enabled
                Duration.ofSeconds(3),
                Duration.ofSeconds(5)
        );

        WebhookEventListenerProvider provider = new WebhookEventListenerProvider(session, httpClient, config);

        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setId("adm-789");
        adminEvent.setTime(System.currentTimeMillis());
        adminEvent.setRealmId("test-realm-id");
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setResourceType(ResourceType.USER);
        adminEvent.setResourcePath("users/user-123");
        adminEvent.setRepresentation("{\"username\":\"new_user\"}");

        AuthDetails authDetails = new AuthDetails();
        authDetails.setRealmId("master");
        authDetails.setClientId("admin-cli");
        authDetails.setUserId("admin-uuid");
        adminEvent.setAuthDetails(authDetails);

        provider.onEvent(adminEvent, true);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(1)).sendAsync(requestCaptor.capture(), any());

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals("admin", capturedRequest.headers().firstValue("X-Keycloak-Event-Category").orElse(""));
    }

    @Test
    @DisplayName("Should not throw or fail when HTTP delivery encounters network exception")
    void testResilienceOnHttpFailure() {
        WebhookEventListenerConfig config = new WebhookEventListenerConfig(
                URI.create("https://webhook.site/events"),
                null,
                Collections.emptySet(),
                true,
                Duration.ofSeconds(3),
                Duration.ofSeconds(5)
        );

        CompletableFuture<HttpResponse<Void>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Connection refused"));
        doReturn(failedFuture).when(httpClient).sendAsync(any(), any());

        WebhookEventListenerProvider provider = new WebhookEventListenerProvider(session, httpClient, config);

        Event event = new Event();
        event.setId("evt-fail");
        event.setType(EventType.LOGIN);

        // Verification: must not throw exception to caller
        assertDoesNotThrow(() -> provider.onEvent(event));
    }
}
