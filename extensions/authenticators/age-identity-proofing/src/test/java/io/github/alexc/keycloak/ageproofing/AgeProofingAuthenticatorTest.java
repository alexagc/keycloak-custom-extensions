package io.github.alexc.keycloak.ageproofing;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.alexc.keycloak.ageproofing.crypto.VerifiablePresentationValidator;
import io.github.alexc.keycloak.ageproofing.model.AgeVerificationConfig;
import io.github.alexc.keycloak.ageproofing.model.VerificationEvidence;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AgeProofingAuthenticatorTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private AuthenticationFlowContext context;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    @Mock
    private AuthenticationSessionModel authSession;

    @Mock
    private LoginFormsProvider formsProvider;

    @Mock
    private HttpRequest httpRequest;

    @Mock
    private VerifiablePresentationValidator validator;

    private AgeVerificationConfig baseConfig;
    private AgeProofingAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        baseConfig = new AgeVerificationConfig(
                18,
                URI.create("https://identity.gov.example/jwks.json"),
                365,
                Set.of(),
                "hybrid",
                false,
                URI.create("https://clave.gob.es"),
                3000,
                60
        );

        authenticator = new AgeProofingAuthenticator(session, validator, baseConfig);

        when(context.getUser()).thenReturn(user);
        when(context.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn("test-realm");
        when(context.getAuthenticationSession()).thenReturn(authSession);
        when(context.getHttpRequest()).thenReturn(httpRequest);
        when(context.form()).thenReturn(formsProvider);
        when(context.getActionUrl(any())).thenReturn(URI.create("http://localhost:8080/auth/action"));
        when(formsProvider.setAttribute(anyString(), any())).thenReturn(formsProvider);
        when(formsProvider.setError(anyString(), any())).thenReturn(formsProvider);
        when(formsProvider.createForm(anyString())).thenReturn(mock(Response.class));
    }

    @Test
    void testAuthenticateUserAlreadyVerifiedAndFreshSucceedsImmediately() {
        when(user.getFirstAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED)).thenReturn("true");
        when(user.getFirstAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED_AT))
                .thenReturn(String.valueOf(Instant.now().getEpochSecond() - 3600));

        authenticator.authenticate(context);

        verify(context).success();
        verify(context, never()).challenge(any());
    }

    @Test
    void testAuthenticateUserVerifiedButExpiredRendersChallenge() {
        when(user.getFirstAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED)).thenReturn("true");
        long expiredTimestamp = Instant.now().getEpochSecond() - (400 * 86400L); // 400 days ago
        when(user.getFirstAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED_AT))
                .thenReturn(String.valueOf(expiredTimestamp));

        authenticator.authenticate(context);

        verify(context).challenge(any());
        verify(context, never()).success();
    }

    @Test
    void testAuthenticateUnverifiedUserRendersChallenge() {
        when(user.getFirstAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED)).thenReturn(null);

        authenticator.authenticate(context);

        verify(context).challenge(any());
        verify(context, never()).success();
    }

    @Test
    void testAuthenticateNullUserFailsWithUnknownUser() {
        when(context.getUser()).thenReturn(null);

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.UNKNOWN_USER);
    }

    @Test
    void testActionValidVpTokenSuccess() {
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("vp_token", "signed-valid-vp-token");
        when(httpRequest.getDecodedFormParameters()).thenReturn(formParams);

        when(validator.validate(eq("signed-valid-vp-token"), any()))
                .thenReturn(VerificationEvidence.successful("hash-sha256-test", "https://clave.gob.es"));

        authenticator.action(context);

        verify(user).setSingleAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED, "true");
        verify(user).setSingleAttribute(eq(AgeProofingAuthenticator.ATTR_AGE_PROOF_HASH), eq("hash-sha256-test"));
        verify(user).setSingleAttribute(eq(AgeProofingAuthenticator.ATTR_AGE_PROOF_ISSUER), eq("https://clave.gob.es"));
        verify(context).success();
    }

    @Test
    void testActionInvalidTokenFails() {
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("vp_token", "invalid-token");
        when(httpRequest.getDecodedFormParameters()).thenReturn(formParams);

        when(validator.validate(eq("invalid-token"), any()))
                .thenReturn(VerificationEvidence.failed("Cryptographic signature verification failed"));

        authenticator.action(context);

        verify(context).failureChallenge(eq(AuthenticationFlowError.INVALID_CREDENTIALS), any());
        verify(context, never()).success();
    }

    @Test
    void testActionMissingTokenRendersErrorChallenge() {
        when(httpRequest.getDecodedFormParameters()).thenReturn(new MultivaluedHashMap<>());

        authenticator.action(context);

        verify(context).failureChallenge(eq(AuthenticationFlowError.INVALID_CREDENTIALS), any());
        verify(context, never()).success();
    }
}
