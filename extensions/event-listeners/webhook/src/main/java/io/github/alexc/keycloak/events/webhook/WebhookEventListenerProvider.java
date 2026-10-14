package io.github.alexc.keycloak.events.webhook;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Request-scoped EventListenerProvider that processes Keycloak events
 * and asynchronously dispatches JSON payloads to a configured HTTP webhook.
 */
public class WebhookEventListenerProvider implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(WebhookEventListenerProvider.class);

    private final KeycloakSession session;
    private final HttpClient httpClient;
    private final WebhookEventListenerConfig config;

    public WebhookEventListenerProvider(KeycloakSession session,
                                        HttpClient httpClient,
                                        WebhookEventListenerConfig config) {
        this.session = session;
        this.httpClient = httpClient;
        this.config = config;
    }

    @Override
    public void onEvent(Event event) {
        if (event == null || !config.shouldDispatchUserEvent(event.getType() != null ? event.getType().name() : "")) {
            return;
        }

        try {
            String realmName = resolveRealmName(event.getRealmId());
            WebhookUserEventRecord record = new WebhookUserEventRecord(
                    event.getId(),
                    event.getTime(),
                    event.getType() != null ? event.getType().name() : "UNKNOWN",
                    event.getRealmId(),
                    realmName,
                    event.getClientId(),
                    event.getUserId(),
                    event.getSessionId(),
                    event.getIpAddress(),
                    event.getError(),
                    event.getDetails()
            );

            String jsonPayload = JsonSerialization.writeValueAsString(record);
            dispatchAsync(jsonPayload, "user", event.getId());
        } catch (Exception e) {
            // Fault tolerance: Never compromise the user authentication transaction
            LOG.errorv(e, "Unexpected error processing user event {0} for webhook dispatch", event.getId());
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        if (adminEvent == null || !config.shouldDispatchAdminEvent()) {
            return;
        }

        try {
            String realmName = resolveRealmName(adminEvent.getRealmId());
            String representation = includeRepresentation ? adminEvent.getRepresentation() : null;

            WebhookAdminEventRecord record = new WebhookAdminEventRecord(
                    adminEvent.getId(),
                    adminEvent.getTime(),
                    adminEvent.getRealmId(),
                    realmName,
                    adminEvent.getAuthDetails() != null ? adminEvent.getAuthDetails().getRealmId() : null,
                    adminEvent.getAuthDetails() != null ? adminEvent.getAuthDetails().getClientId() : null,
                    adminEvent.getAuthDetails() != null ? adminEvent.getAuthDetails().getUserId() : null,
                    adminEvent.getAuthDetails() != null ? adminEvent.getAuthDetails().getIpAddress() : null,
                    adminEvent.getOperationType() != null ? adminEvent.getOperationType().name() : "UNKNOWN",
                    adminEvent.getResourceType() != null ? adminEvent.getResourceType().name() : "UNKNOWN",
                    adminEvent.getResourcePath(),
                    representation,
                    adminEvent.getError()
            );

            String jsonPayload = JsonSerialization.writeValueAsString(record);
            dispatchAsync(jsonPayload, "admin", adminEvent.getId());
        } catch (Exception e) {
            // Fault tolerance: Never compromise Keycloak administrative actions
            LOG.errorv(e, "Unexpected error processing admin event {0} for webhook dispatch", adminEvent.getId());
        }
    }

    @Override
    public void close() {
        // No per-request cleanup required
    }

    private void dispatchAsync(String jsonPayload, String eventCategory, String eventId) {
        if (httpClient == null || config.targetUri() == null) {
            return;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(config.targetUri())
                .timeout(config.readTimeout())
                .header("Content-Type", "application/json")
                .header("User-Agent", "Keycloak-Webhook-EventListener/1.0")
                .header("X-Keycloak-Event-Category", eventCategory)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

        if (config.authorizationHeader() != null && !config.authorizationHeader().isBlank()) {
            requestBuilder.header("Authorization", config.authorizationHeader().trim());
        }

        HttpRequest request = requestBuilder.build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        LOG.warnv("Failed to deliver webhook for {0} event {1} to {2}: {3}",
                                eventCategory, eventId, config.targetUri(), throwable.getMessage());
                    } else if (response.statusCode() >= 400) {
                        LOG.warnv("Webhook target {0} responded with HTTP status {1} for {2} event {3}",
                                config.targetUri(), response.statusCode(), eventCategory, eventId);
                    } else {
                        LOG.debugv("Successfully delivered webhook for {0} event {1} (HTTP {2})",
                                eventCategory, eventId, response.statusCode());
                    }
                });
    }

    private String resolveRealmName(String realmId) {
        if (session == null || realmId == null) {
            return null;
        }
        try {
            RealmModel realm = session.realms().getRealm(realmId);
            return realm != null ? realm.getName() : null;
        } catch (Exception e) {
            LOG.debugv("Could not resolve realm name for realmId: {0}", realmId);
            return null;
        }
    }
}
