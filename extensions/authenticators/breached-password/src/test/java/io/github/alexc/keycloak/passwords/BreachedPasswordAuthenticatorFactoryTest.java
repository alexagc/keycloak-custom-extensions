package io.github.alexc.keycloak.passwords;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.Config;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;

class BreachedPasswordAuthenticatorFactoryTest {

    @Test
    @DisplayName("Factory metadata and configuration properties are well-formed")
    void testFactoryMetadata() {
        BreachedPasswordAuthenticatorFactory factory = new BreachedPasswordAuthenticatorFactory();

        assertEquals(BreachedPasswordAuthenticatorFactory.PROVIDER_ID, factory.getId());
        assertEquals("Breached Password Detector", factory.getDisplayType());
        assertEquals("password", factory.getReferenceCategory());
        assertTrue(factory.isConfigurable());
        assertFalse(factory.isUserSetupAllowed());
        assertNotNull(factory.getHelpText());

        AuthenticationExecutionModel.Requirement[] choices = factory.getRequirementChoices();
        assertEquals(3, choices.length);

        assertFalse(factory.getConfigProperties().isEmpty());
    }

    @Test
    @DisplayName("Factory initializes without errors and instantiates authenticator")
    void testInitAndCreate() {
        BreachedPasswordAuthenticatorFactory factory = new BreachedPasswordAuthenticatorFactory();
        Config.Scope scope = mock(Config.Scope.class);

        assertDoesNotThrow(() -> factory.init(scope));
        assertDoesNotThrow(() -> factory.postInit(null));

        KeycloakSession session = mock(KeycloakSession.class);
        assertNotNull(factory.create(session));

        assertDoesNotThrow(factory::close);
    }
}
