package io.github.alexc.keycloak.passwords;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.alexc.keycloak.passwords.client.BreachCheckResult;
import io.github.alexc.keycloak.passwords.client.HibpClient;
import io.github.alexc.keycloak.passwords.config.BreachedPasswordConfig;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.policy.PolicyError;

class BreachedPasswordPolicyProviderTest {

    private KeycloakSession session;
    private RealmModel realm;
    private UserModel user;
    private HibpClient hibpClient;
    private BreachedPasswordConfig config;
    private BreachedPasswordPolicyProvider policyProvider;

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        realm = mock(RealmModel.class);
        user = mock(UserModel.class);
        hibpClient = mock(HibpClient.class);
        config = new BreachedPasswordConfig(
                "strict",
                1,
                1500,
                true,
                URI.create("https://api.pwnedpasswords.com/range/"),
                100,
                60
        );

        when(realm.getName()).thenReturn("test-realm");
        when(user.getUsername()).thenReturn("john.doe");

        policyProvider = new BreachedPasswordPolicyProvider(session, hibpClient, config);
    }

    @Test
    @DisplayName("Blank or null password returns no policy error")
    void testBlankOrNullPassword() {
        assertNull(policyProvider.validate(realm, user, null));
        assertNull(policyProvider.validate(realm, user, "   "));
    }

    @Test
    @DisplayName("Clean password passes validation without error")
    void testCleanPasswordValidates() {
        when(hibpClient.checkPassword("CleanAndSecure123!")).thenReturn(BreachCheckResult.clean());

        PolicyError error = policyProvider.validate(realm, user, "CleanAndSecure123!");
        assertNull(error);
    }

    @Test
    @DisplayName("Breached password triggers PolicyError with error key and count")
    void testBreachedPasswordRejection() {
        when(hibpClient.checkPassword("password")).thenReturn(BreachCheckResult.breached(3861493));

        PolicyError error = policyProvider.validate(realm, user, "password");
        assertNotNull(error);
        assertEquals(BreachedPasswordPolicyProvider.ERROR_KEY, error.getMessage());
        assertEquals(1, error.getParameters().length);
        assertEquals(3861493, error.getParameters()[0]);
    }

    @Test
    @DisplayName("Degraded check under fail-open allows password without blocking user")
    void testDegradedCheckPasses() {
        when(hibpClient.checkPassword("any_password")).thenReturn(BreachCheckResult.degradedFailOpen());

        PolicyError error = policyProvider.validate(realm, user, "any_password");
        assertNull(error);
    }

    @Test
    @DisplayName("Convenience overload validate(username, password) delegates cleanly")
    void testConvenienceValidate() {
        when(hibpClient.checkPassword("p@ssword")).thenReturn(BreachCheckResult.breached(100));

        PolicyError error = policyProvider.validate("testuser", "p@ssword");
        assertNotNull(error);
        assertEquals(BreachedPasswordPolicyProvider.ERROR_KEY, error.getMessage());
    }

    @Test
    @DisplayName("parseConfig handles valid integer, blank, and malformed strings")
    void testParseConfig() {
        assertEquals(5, policyProvider.parseConfig("5"));
        assertEquals(config.breachThreshold(), policyProvider.parseConfig(null));
        assertEquals(config.breachThreshold(), policyProvider.parseConfig(""));
        assertEquals(config.breachThreshold(), policyProvider.parseConfig("not_a_number"));
    }
}
