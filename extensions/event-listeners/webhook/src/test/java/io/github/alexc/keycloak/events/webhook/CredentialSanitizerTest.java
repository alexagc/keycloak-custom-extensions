package io.github.alexc.keycloak.events.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialSanitizerTest {

    @Test
    @DisplayName("Should mask sensitive keys in event details map")
    void testSanitizeDetails() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("username", "john_doe");
        details.put("password", "SuperSecret123!");
        details.put("custom_client_secret", "my-api-key");
        details.put("code", "auth-grant-code-123");
        details.put("token_id", "jwt.token.string");
        details.put("auth_method", "openid-connect");

        Map<String, String> sanitized = CredentialSanitizer.sanitizeDetails(details);

        assertEquals("john_doe", sanitized.get("username"));
        assertEquals("openid-connect", sanitized.get("auth_method"));
        assertEquals("******", sanitized.get("password"));
        assertEquals("******", sanitized.get("custom_client_secret"));
        assertEquals("******", sanitized.get("code"));
        assertEquals("******", sanitized.get("token_id"));
    }

    @Test
    @DisplayName("Should handle null and empty details gracefully")
    void testSanitizeEmptyDetails() {
        assertTrue(CredentialSanitizer.sanitizeDetails(null).isEmpty());
        assertTrue(CredentialSanitizer.sanitizeDetails(Collections.emptyMap()).isEmpty());
    }

    @Test
    @DisplayName("Should mask credentials inside JSON representation strings")
    void testSanitizeRepresentation() {
        String json = "{\"username\":\"alice\",\"password\":\"rawSecret123\",\"email\":\"alice@example.com\",\"secret\":\"shhh\"}";
        String sanitized = CredentialSanitizer.sanitizeRepresentation(json);

        assertTrue(sanitized.contains("\"password\":\"******\""));
        assertTrue(sanitized.contains("\"secret\":\"******\""));
        assertTrue(sanitized.contains("\"username\":\"alice\""));
        assertTrue(sanitized.contains("\"email\":\"alice@example.com\""));
    }

    @Test
    @DisplayName("Should return null or blank representation as-is")
    void testSanitizeNullRepresentation() {
        assertNull(CredentialSanitizer.sanitizeRepresentation(null));
        assertEquals("", CredentialSanitizer.sanitizeRepresentation(""));
    }
}
