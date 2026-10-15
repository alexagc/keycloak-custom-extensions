package io.github.alexc.keycloak.events.kafka.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudEventsRecordMapperTest {

    @Test
    @DisplayName("Should serialize User Event into compliant CloudEvents 1.0 JSON")
    void testToUserCloudEvent() throws IOException {
        Event event = new Event();
        event.setId("event-uuid-123");
        event.setTime(1700000000000L);
        event.setType(EventType.LOGIN);
        event.setRealmId("realm-abc");
        event.setClientId("my-frontend");
        event.setUserId("user-456");
        event.setSessionId("session-789");
        event.setIpAddress("192.168.1.100");

        Map<String, String> details = new HashMap<>();
        details.put("auth_method", "openid-connect");
        details.put("password", "raw-password-attempt");
        event.setDetails(details);

        String json = CloudEventsRecordMapper.toUserCloudEvent(event, "master");
        assertNotNull(json);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = JsonSerialization.readValue(json, Map.class);

        assertEquals("1.0", map.get("specversion"));
        assertEquals("event-uuid-123", map.get("id"));
        assertEquals("/realms/master", map.get("source"));
        assertEquals("io.keycloak.user.LOGIN", map.get("type"));
        assertEquals("application/json", map.get("datacontenttype"));
        assertNotNull(map.get("time"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) map.get("data");
        assertEquals("user-456", data.get("userId"));
        assertEquals("my-frontend", data.get("clientId"));
        assertEquals("192.168.1.100", data.get("ipAddress"));

        @SuppressWarnings("unchecked")
        Map<String, String> detailsMap = (Map<String, String>) data.get("details");
        assertEquals("openid-connect", detailsMap.get("auth_method"));
        assertEquals("******", detailsMap.get("password"));
    }

    @Test
    @DisplayName("Should serialize Admin Event into compliant CloudEvents 1.0 JSON")
    void testToAdminCloudEvent() throws IOException {
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setId("admin-event-uuid-456");
        adminEvent.setTime(1700000000000L);
        adminEvent.setRealmId("realm-admin");
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setResourceType(ResourceType.USER);
        adminEvent.setResourcePath("users/user-123");
        adminEvent.setRepresentation("{\"username\":\"newuser\",\"password\":\"temporaryPass\"}");

        AuthDetails authDetails = new AuthDetails();
        authDetails.setRealmId("master");
        authDetails.setClientId("admin-cli");
        authDetails.setUserId("admin-user-id");
        authDetails.setIpAddress("10.0.0.1");
        adminEvent.setAuthDetails(authDetails);

        String json = CloudEventsRecordMapper.toAdminCloudEvent(adminEvent, "my-realm");
        assertNotNull(json);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = JsonSerialization.readValue(json, Map.class);

        assertEquals("1.0", map.get("specversion"));
        assertEquals("admin-event-uuid-456", map.get("id"));
        assertEquals("/realms/my-realm", map.get("source"));
        assertEquals("io.keycloak.admin.CREATE", map.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) map.get("data");
        assertEquals("CREATE", data.get("operationType"));
        assertEquals("USER", data.get("resourceType"));
        assertEquals("users/user-123", data.get("resourcePath"));

        String representation = (String) data.get("representation");
        assertTrue(representation.contains("\"password\":\"******\""));
    }

    @Test
    @DisplayName("Should throw exception when serializing null event")
    void testNullEventValidation() {
        assertThrows(IllegalArgumentException.class, () -> CloudEventsRecordMapper.toUserCloudEvent(null, "realm"));
        assertThrows(IllegalArgumentException.class, () -> CloudEventsRecordMapper.toAdminCloudEvent(null, "realm"));
    }
}
