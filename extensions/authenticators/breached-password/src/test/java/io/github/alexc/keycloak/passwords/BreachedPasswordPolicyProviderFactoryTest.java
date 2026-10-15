package io.github.alexc.keycloak.passwords;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.policy.PasswordPolicyProvider;

class BreachedPasswordPolicyProviderFactoryTest {

    @Test
    @DisplayName("Factory metadata and defaults are correctly configured")
    void testFactoryMetadata() {
        BreachedPasswordPolicyProviderFactory factory = new BreachedPasswordPolicyProviderFactory();

        assertEquals(BreachedPasswordPolicyProviderFactory.PROVIDER_ID, factory.getId());
        assertEquals("Reject Breached Passwords (HIBP)", factory.getDisplayName());
        assertEquals(PasswordPolicyProvider.INT_CONFIG_TYPE, factory.getConfigType());
        assertEquals("1", factory.getDefaultConfigValue());
        assertFalse(factory.isMultiplSupported());

        Config.Scope scope = mock(Config.Scope.class);
        assertDoesNotThrow(() -> factory.init(scope));
        assertDoesNotThrow(() -> factory.postInit(null));

        KeycloakSession session = mock(KeycloakSession.class);
        assertNotNull(factory.create(session));

        assertDoesNotThrow(factory::close);
    }
}
