package io.github.alexc.keycloak.events.webhook;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

/**
 * Immutable configuration holder for the Webhook Event Listener SPI.
 *
 * @param targetUri           Target HTTP/HTTPS webhook URI endpoint.
 * @param authorizationHeader Optional Authorization header (e.g., "Bearer token" or "ApiKey secret").
 * @param includedEventTypes  Specific user event types to forward (empty set means all events).
 * @param includeAdminEvents  Whether to forward administrative operations.
 * @param connectTimeout      HTTP connection timeout.
 * @param readTimeout         HTTP request timeout.
 */
public record WebhookEventListenerConfig(
        URI targetUri,
        String authorizationHeader,
        Set<String> includedEventTypes,
        boolean includeAdminEvents,
        Duration connectTimeout,
        Duration readTimeout
) {
    public boolean shouldDispatchUserEvent(String eventType) {
        if (targetUri == null) {
            return false;
        }
        return includedEventTypes.isEmpty() || includedEventTypes.contains(eventType.toUpperCase());
    }

    public boolean shouldDispatchAdminEvent() {
        return targetUri != null && includeAdminEvents;
    }
}
