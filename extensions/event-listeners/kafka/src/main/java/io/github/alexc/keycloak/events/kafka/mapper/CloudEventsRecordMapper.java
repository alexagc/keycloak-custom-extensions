package io.github.alexc.keycloak.events.kafka.mapper;

import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Serializes Keycloak user and admin events into canonical CloudEvents 1.0 JSON format.
 */
public final class CloudEventsRecordMapper {

    public static final String SPEC_VERSION = "1.0";
    public static final String CONTENT_TYPE_JSON = "application/json";

    public static final String USER_EVENT_TYPE_PREFIX = "io.keycloak.user.";
    public static final String ADMIN_EVENT_TYPE_PREFIX = "io.keycloak.admin.";

    private CloudEventsRecordMapper() {
        // Utility class
    }

    /**
     * Converts a Keycloak {@link Event} into a CloudEvents 1.0 JSON string.
     *
     * @param event     user event
     * @param realmName human-readable realm name
     * @return CloudEvents 1.0 JSON string
     * @throws IOException if serialization fails
     */
    public static String toUserCloudEvent(Event event, String realmName) throws IOException {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        String eventId = event.getId() != null ? event.getId() : UUID.randomUUID().toString();
        String eventType = event.getType() != null ? event.getType().name() : "UNKNOWN";
        long timestamp = event.getTime() > 0 ? event.getTime() : System.currentTimeMillis();
        String effectiveRealm = realmName != null && !realmName.isBlank() ? realmName : event.getRealmId();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", eventId);
        data.put("time", timestamp);
        data.put("type", eventType);
        data.put("realmId", event.getRealmId());
        data.put("realmName", realmName);
        data.put("clientId", event.getClientId());
        data.put("userId", event.getUserId());
        data.put("sessionId", event.getSessionId());
        data.put("ipAddress", event.getIpAddress());
        data.put("error", event.getError());
        data.put("details", CredentialSanitizer.sanitizeDetails(event.getDetails()));

        Map<String, Object> cloudEvent = new LinkedHashMap<>();
        cloudEvent.put("specversion", SPEC_VERSION);
        cloudEvent.put("id", eventId);
        cloudEvent.put("source", "/realms/" + (effectiveRealm != null ? effectiveRealm : "unknown"));
        cloudEvent.put("type", USER_EVENT_TYPE_PREFIX + eventType);
        cloudEvent.put("time", Instant.ofEpochMilli(timestamp).toString());
        cloudEvent.put("datacontenttype", CONTENT_TYPE_JSON);
        cloudEvent.put("data", data);

        return JsonSerialization.writeValueAsString(cloudEvent);
    }

    /**
     * Converts a Keycloak {@link AdminEvent} into a CloudEvents 1.0 JSON string.
     *
     * @param event     administrative event
     * @param realmName human-readable realm name
     * @return CloudEvents 1.0 JSON string
     * @throws IOException if serialization fails
     */
    public static String toAdminCloudEvent(AdminEvent event, String realmName) throws IOException {
        if (event == null) {
            throw new IllegalArgumentException("AdminEvent cannot be null");
        }

        String eventId = event.getId() != null ? event.getId() : UUID.randomUUID().toString();
        String operationType = event.getOperationType() != null ? event.getOperationType().name() : "UNKNOWN";
        long timestamp = event.getTime() > 0 ? event.getTime() : System.currentTimeMillis();
        String effectiveRealm = realmName != null && !realmName.isBlank() ? realmName : event.getRealmId();

        Map<String, Object> authMap = new LinkedHashMap<>();
        AuthDetails authDetails = event.getAuthDetails();
        if (authDetails != null) {
            authMap.put("realmId", authDetails.getRealmId());
            authMap.put("clientId", authDetails.getClientId());
            authMap.put("userId", authDetails.getUserId());
            authMap.put("ipAddress", authDetails.getIpAddress());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", eventId);
        data.put("time", timestamp);
        data.put("realmId", event.getRealmId());
        data.put("realmName", realmName);
        data.put("authDetails", authMap);
        data.put("operationType", operationType);
        data.put("resourceType", event.getResourceType() != null ? event.getResourceType().name() : null);
        data.put("resourcePath", event.getResourcePath());
        data.put("error", event.getError());
        data.put("representation", CredentialSanitizer.sanitizeRepresentation(event.getRepresentation()));

        Map<String, Object> cloudEvent = new LinkedHashMap<>();
        cloudEvent.put("specversion", SPEC_VERSION);
        cloudEvent.put("id", eventId);
        cloudEvent.put("source", "/realms/" + (effectiveRealm != null ? effectiveRealm : "unknown"));
        cloudEvent.put("type", ADMIN_EVENT_TYPE_PREFIX + operationType);
        cloudEvent.put("time", Instant.ofEpochMilli(timestamp).toString());
        cloudEvent.put("datacontenttype", CONTENT_TYPE_JSON);
        cloudEvent.put("data", data);

        return JsonSerialization.writeValueAsString(cloudEvent);
    }
}
