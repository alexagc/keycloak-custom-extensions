package io.github.alexc.keycloak.events.webhook;

/**
 * Immutable JSON payload record for Keycloak administrative events.
 *
 * @param eventId        Unique identifier of the admin event.
 * @param time           Timestamp of the event in milliseconds.
 * @param realmId        Target realm identifier where the change was executed.
 * @param realmName      Target realm name.
 * @param authRealmId    Realm where the administrator authenticated.
 * @param authClientId   Client ID used by the administrator.
 * @param authUserId     User ID of the administrator.
 * @param authIpAddress  IP address of the administrator.
 * @param operationType  Type of administrative operation (e.g., CREATE, UPDATE, DELETE, ACTION).
 * @param resourceType   Target resource type (e.g., USER, REALM, CLIENT, ROLE).
 * @param resourcePath   Relative REST path to the resource affected.
 * @param representation Serialized JSON representation of the entity, if enabled and available.
 * @param error          Error message if the operation failed.
 */
public record WebhookAdminEventRecord(
        String eventId,
        long time,
        String realmId,
        String realmName,
        String authRealmId,
        String authClientId,
        String authUserId,
        String authIpAddress,
        String operationType,
        String resourceType,
        String resourcePath,
        String representation,
        String error
) {
}
