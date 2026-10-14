package io.github.alexc.keycloak.events.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.Config;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebhookEventListenerProviderFactoryTest {

    @Test
    @DisplayName("Provider ID should be 'webhook'")
    void testProviderId() {
        WebhookEventListenerProviderFactory factory = new WebhookEventListenerProviderFactory();
        assertEquals("webhook", factory.getId());
    }

    @Test
    @DisplayName("Should correctly parse SPI configuration properties")
    void testInitWithCustomConfig() {
        Config.Scope scope = mock(Config.Scope.class);
        when(scope.get("target-url")).thenReturn("https://api.example.com/webhooks");
        when(scope.get("authorization-header")).thenReturn("Bearer my-secret-token");
        when(scope.get("include-events")).thenReturn("LOGIN,LOGOUT,REGISTER");
        when(scope.get("include-admin-events")).thenReturn("false");
        when(scope.get("connect-timeout-ms")).thenReturn("2000");
        when(scope.get("read-timeout-ms")).thenReturn("4000");

        WebhookEventListenerProviderFactory factory = new WebhookEventListenerProviderFactory();
        factory.init(scope);

        WebhookEventListenerConfig config = factory.getConfig();
        assertNotNull(config);
        assertEquals(URI.create("https://api.example.com/webhooks"), config.targetUri());
        assertEquals("Bearer my-secret-token", config.authorizationHeader());
        assertTrue(config.shouldDispatchUserEvent("LOGIN"));
        assertTrue(config.shouldDispatchUserEvent("LOGOUT"));
        assertFalse(config.shouldDispatchUserEvent("LOGIN_ERROR"));
        assertFalse(config.includeAdminEvents());
        assertEquals(Duration.ofMillis(2000), config.connectTimeout());
        assertEquals(Duration.ofMillis(4000), config.readTimeout());
    }
}
