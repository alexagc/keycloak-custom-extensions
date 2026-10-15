package io.github.alexc.keycloak.events.kafka.producer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicResolverTest {

    @Test
    @DisplayName("Should resolve topic with {realm} placeholder substituted and sanitized")
    void testResolveWithPlaceholder() {
        String pattern = "keycloak.events.{realm}";
        String resolved = TopicResolver.resolve(pattern, "My_Company_Realm");
        assertEquals("keycloak.events.my_company_realm", resolved);
    }

    @Test
    @DisplayName("Should return pattern as-is when no {realm} placeholder is present")
    void testResolveStaticTopic() {
        String pattern = "keycloak.global.events";
        String resolved = TopicResolver.resolve(pattern, "ignored-realm");
        assertEquals("keycloak.global.events", resolved);
    }

    @ParameterizedTest
    @CsvSource({
            "'Special #$* Realm', 'special-----realm'",
            "'', 'default'",
            "'...invalid..', 'invalid..'",
            "'---test---', 'test---'"
    })
    @DisplayName("Should sanitize invalid characters for Kafka topic naming rules")
    void testSanitizeRealm(String input, String expected) {
        String sanitized = TopicResolver.sanitizeRealm(input);
        assertEquals(expected, sanitized);
    }

    @Test
    @DisplayName("Should handle null realm gracefully with default fallback")
    void testResolveWithNullRealm() {
        String pattern = "keycloak.events.{realm}";
        String resolved = TopicResolver.resolve(pattern, null);
        assertEquals("keycloak.events.default", resolved);
    }

    @Test
    @DisplayName("Should throw exception when pattern is null or blank")
    void testResolveNullPattern() {
        assertThrows(IllegalArgumentException.class, () -> TopicResolver.resolve(null, "realm"));
        assertThrows(IllegalArgumentException.class, () -> TopicResolver.resolve("   ", "realm"));
    }

    @Test
    @DisplayName("Should truncate resolved topic if it exceeds Kafka 249 character limit")
    void testTruncateLongTopic() {
        String pattern = "events.{realm}";
        String longRealm = "a".repeat(300);
        String resolved = TopicResolver.resolve(pattern, longRealm);
        assertTrue(resolved.length() <= 249);
    }
}
