package io.github.alexc.keycloak.passwords;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

class CheckBreachedPasswordActionTest {

    private RequiredActionContext context;
    private UserModel user;
    private AuthenticationSessionModel authSession;
    private CheckBreachedPasswordAction action;

    @BeforeEach
    void setUp() {
        context = mock(RequiredActionContext.class);
        user = mock(UserModel.class);
        authSession = mock(AuthenticationSessionModel.class);
        action = new CheckBreachedPasswordAction();

        when(context.getUser()).thenReturn(user);
        when(context.getAuthenticationSession()).thenReturn(authSession);
        when(user.getUsername()).thenReturn("alice");
    }

    @Test
    @DisplayName("evaluateTriggers is non-blocking and executes safely")
    void testEvaluateTriggers() {
        assertDoesNotThrow(() -> action.evaluateTriggers(context));
    }

    @Test
    @DisplayName("requiredActionChallenge delegates to UPDATE_PASSWORD and cleans up action")
    void testRequiredActionChallenge() {
        action.requiredActionChallenge(context);

        verify(authSession).addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
        verify(user).addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
        verify(user).removeRequiredAction(CheckBreachedPasswordActionFactory.PROVIDER_ID);
        verify(context).success();
    }

    @Test
    @DisplayName("processAction cleans up action and completes successfully")
    void testProcessAction() {
        action.processAction(context);

        verify(user).removeRequiredAction(CheckBreachedPasswordActionFactory.PROVIDER_ID);
        verify(context).success();
    }

    @Test
    @DisplayName("Factory instantiates provider and returns expected metadata")
    void testFactoryMetadata() {
        CheckBreachedPasswordActionFactory factory = new CheckBreachedPasswordActionFactory();
        assertEquals(CheckBreachedPasswordActionFactory.PROVIDER_ID, factory.getId());
        assertEquals("Update Breached Password", factory.getDisplayText());
        assertTrue(factory.isOneTimeAction());

        KeycloakSession session = mock(KeycloakSession.class);
        assertNotNull(factory.create(session));

        assertDoesNotThrow(() -> factory.init(null));
        assertDoesNotThrow(() -> factory.postInit(null));
        assertDoesNotThrow(factory::close);
    }
}
