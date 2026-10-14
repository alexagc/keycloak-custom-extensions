package io.github.alexc.keycloak.events.webhook;

import java.util.Map;

/**
 * Immutable JSON payload record for Keycloak user audit events.
 *
 * @param eventId    Unique identifier of the event.
 * @param time       Timestamp of the event in milliseconds.
 * @param type       The event type (e.g., LOGIN, LOGIN_ERROR, REGISTER, LOGOUT).
 * @param realmId    Identifier of the realm where the event occurred.
 * @param realmName  Display name or path of the realm.
 * @param clientId   Client identifier that initiated the request.
 * @param userId     User identifier associated with the event.
 * @param sessionId  User session ID, if applicable.
 * @param ipAddress  Client IP address.
 * @param error      Error message if the event represents a failure.
 * @param details    Key-value metadata details regarding the event.
 */
public record WebhookUserEventRecord(
        String eventId,
        long time,
        String type,
        String realmId,
        String realmName,
        String clientId,
        String userId,
        String sessionId,
        String ipAddress,
        String error,
        Map<String, String> details
) {
}
