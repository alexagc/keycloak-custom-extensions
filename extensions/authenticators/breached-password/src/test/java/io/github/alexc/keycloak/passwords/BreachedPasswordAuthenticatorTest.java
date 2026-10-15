package io.github.alexc.keycloak.passwords;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.alexc.keycloak.passwords.client.BreachCheckResult;
import io.github.alexc.keycloak.passwords.client.HibpClient;
import io.github.alexc.keycloak.passwords.config.BreachedPasswordConfig;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.events.EventBuilder;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

class BreachedPasswordAuthenticatorTest {

    private KeycloakSession session;
    private AuthenticationFlowContext context;
    private UserModel user;
    private RealmModel realm;
    private HttpRequest httpRequest;
    private AuthenticationSessionModel authSession;
    private EventBuilder event;
    private HibpClient hibpClient;

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        context = mock(AuthenticationFlowContext.class);
        user = mock(UserModel.class);
        realm = mock(RealmModel.class);
        httpRequest = mock(HttpRequest.class);
        authSession = mock(AuthenticationSessionModel.class);
        event = mock(EventBuilder.class);
        hibpClient = mock(HibpClient.class);

        when(context.getUser()).thenReturn(user);
        when(context.getRealm()).thenReturn(realm);
        when(context.getHttpRequest()).thenReturn(httpRequest);
        when(context.getAuthenticationSession()).thenReturn(authSession);
        when(context.getEvent()).thenReturn(event);
        when(user.getUsername()).thenReturn("testuser");
        when(realm.getName()).thenReturn("testrealm");
        when(event.detail(anyString(), anyString())).thenReturn(event);
    }

    @Test
    @DisplayName("Context without user attempts next execution in flow")
    void testNoUserAttempts() {
        when(context.getUser()).thenReturn(null);

        BreachedPasswordConfig config = createConfig("strict");
        BreachedPasswordAuthenticator auth = new BreachedPasswordAuthenticator(session, hibpClient, config);

        auth.authenticate(context);
        verify(context).attempted();
        verify(context, never()).success();
    }

    @Test
    @DisplayName("Request without password (e.g. WebAuthn or SSO) proceeds immediately with success")
    void testNoPasswordSkipsCheck() {
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        when(httpRequest.getDecodedFormParameters()).thenReturn(form);

        BreachedPasswordConfig config = createConfig("strict");
        BreachedPasswordAuthenticator auth = new BreachedPasswordAuthenticator(session, hibpClient, config);

        auth.authenticate(context);
        verify(context).success();
        verify(hibpClient, never()).checkPassword(anyString());
    }

    @Test
    @DisplayName("Clean password authentication succeeds without required actions")
    void testCleanPasswordSucceeds() {
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("password", "clean_secure_password");
        when(httpRequest.getDecodedFormParameters()).thenReturn(form);

        when(hibpClient.checkPassword("clean_secure_password")).thenReturn(BreachCheckResult.clean());

        BreachedPasswordConfig config = createConfig("strict");
        BreachedPasswordAuthenticator auth = new BreachedPasswordAuthenticator(session, hibpClient, config);

        auth.authenticate(context);

        verify(context).success();
        verify(authSession, never()).addRequiredAction(anyString());
        verify(user, never()).addRequiredAction(anyString());
    }

    @Test
    @DisplayName("Breached password in strict mode forces UPDATE_PASSWORD required action")
    void testBreachedPasswordStrictModeForcesUpdate() {
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("password", "leaked_password");
        when(httpRequest.getDecodedFormParameters()).thenReturn(form);

        when(hibpClient.checkPassword("leaked_password")).thenReturn(BreachCheckResult.breached(500));

        BreachedPasswordConfig config = createConfig("strict");
        BreachedPasswordAuthenticator auth = new BreachedPasswordAuthenticator(session, hibpClient, config);

        auth.authenticate(context);

        verify(context).success();
        verify(event).detail("breached_password_detected", "true");
        verify(event).detail("breached_password_count", "500");
        verify(authSession).addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
        verify(user).addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
    }

    @Test
    @DisplayName("Breached password in audit-only mode logs event but does not enforce action")
    void testBreachedPasswordAuditOnlyModeLogsWithoutEnforcing() {
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("password", "leaked_password");
        when(httpRequest.getDecodedFormParameters()).thenReturn(form);

        when(hibpClient.checkPassword("leaked_password")).thenReturn(BreachCheckResult.breached(25));

        BreachedPasswordConfig config = createConfig("audit-only");
        BreachedPasswordAuthenticator auth = new BreachedPasswordAuthenticator(session, hibpClient, config);

        auth.authenticate(context);

        verify(context).success();
        verify(event).detail("breached_password_detected", "true");
        verify(event).detail("breached_password_count", "25");
        verify(authSession, never()).addRequiredAction(anyString());
        verify(user, never()).addRequiredAction(anyString());
    }

    @Test
    @DisplayName("Degraded check under fail-open logs warning and succeeds")
    void testDegradedCheckFailOpen() {
        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("password", "some_password");
        when(httpRequest.getDecodedFormParameters()).thenReturn(form);

        when(hibpClient.checkPassword("some_password")).thenReturn(BreachCheckResult.degradedFailOpen());

        BreachedPasswordConfig config = createConfig("strict");
        BreachedPasswordAuthenticator auth = new BreachedPasswordAuthenticator(session, hibpClient, config);

        auth.authenticate(context);

        verify(context).success();
        verify(event).detail("breached_password_check", "degraded");
        verify(authSession, never()).addRequiredAction(anyString());
    }

    @Test
    @DisplayName("Authenticator lifecycle methods behave correctly")
    void testLifecycleMethods() {
        BreachedPasswordConfig config = createConfig("strict");
        BreachedPasswordAuthenticator auth = new BreachedPasswordAuthenticator(session, hibpClient, config);

        assertTrue(auth.requiresUser());
        assertTrue(auth.configuredFor(session, realm, user));
        assertDoesNotThrow(() -> auth.action(context));
        assertDoesNotThrow(() -> auth.setRequiredActions(session, realm, user));
        assertDoesNotThrow(auth::close);
    }

    private static BreachedPasswordConfig createConfig(String mode) {
        return new BreachedPasswordConfig(
                mode,
                1,
                1500,
                true,
                URI.create("https://api.pwnedpasswords.com/range/"),
                100,
                60
        );
    }
}
