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
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AgeProofingRequiredActionTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private RequiredActionContext context;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    @Mock
    private LoginFormsProvider formsProvider;

    @Mock
    private HttpRequest httpRequest;

    @Mock
    private VerifiablePresentationValidator validator;

    private AgeVerificationConfig config;
    private AgeProofingRequiredAction requiredAction;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        config = new AgeVerificationConfig(
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

        requiredAction = new AgeProofingRequiredAction(session, validator, config);

        when(context.getUser()).thenReturn(user);
        when(context.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn("test-realm");
        when(context.getHttpRequest()).thenReturn(httpRequest);
        when(context.form()).thenReturn(formsProvider);
        when(context.getActionUrl()).thenReturn(URI.create("http://localhost:8080/auth/action"));
        when(formsProvider.setAttribute(anyString(), any())).thenReturn(formsProvider);
        when(formsProvider.setError(anyString(), any())).thenReturn(formsProvider);
        when(formsProvider.createForm(anyString())).thenReturn(mock(Response.class));
    }

    @Test
    void testEvaluateTriggersUnverifiedUserAddsRequiredAction() {
        when(user.getFirstAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED)).thenReturn(null);

        requiredAction.evaluateTriggers(context);

        verify(user).addRequiredAction(AgeProofingRequiredActionFactory.PROVIDER_ID);
    }

    @Test
    void testEvaluateTriggersVerifiedUserWithinValidityPeriodDoesNotAddAction() {
        when(user.getFirstAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED)).thenReturn("true");
        when(user.getFirstAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED_AT))
                .thenReturn(String.valueOf(Instant.now().getEpochSecond() - 3600));

        requiredAction.evaluateTriggers(context);

        verify(user, never()).addRequiredAction(anyString());
    }

    @Test
    void testEvaluateTriggersExpiredVerificationAddsRequiredAction() {
        when(user.getFirstAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED)).thenReturn("true");
        long expiredTimestamp = Instant.now().getEpochSecond() - (400 * 86400L);
        when(user.getFirstAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED_AT))
                .thenReturn(String.valueOf(expiredTimestamp));

        requiredAction.evaluateTriggers(context);

        verify(user).addRequiredAction(AgeProofingRequiredActionFactory.PROVIDER_ID);
    }

    @Test
    void testRequiredActionChallengeRendersForm() {
        requiredAction.requiredActionChallenge(context);

        verify(context).challenge(any());
    }

    @Test
    void testProcessActionValidTokenSuccess() {
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("vp_token", "valid-vp-token");
        when(httpRequest.getDecodedFormParameters()).thenReturn(formParams);

        when(validator.validate(eq("valid-vp-token"), any()))
                .thenReturn(VerificationEvidence.successful("hash-proof-xyz", "https://clave.gob.es"));

        requiredAction.processAction(context);

        verify(user).setSingleAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED, "true");
        verify(user).setSingleAttribute(eq(AgeProofingAuthenticator.ATTR_AGE_PROOF_HASH), eq("hash-proof-xyz"));
        verify(user).removeRequiredAction(AgeProofingRequiredActionFactory.PROVIDER_ID);
        verify(context).success();
    }

    @Test
    void testProcessActionInvalidTokenRendersChallenge() {
        MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle("vp_token", "bad-token");
        when(httpRequest.getDecodedFormParameters()).thenReturn(formParams);

        when(validator.validate(eq("bad-token"), any()))
                .thenReturn(VerificationEvidence.failed("Signature verification error"));

        requiredAction.processAction(context);

        verify(context).challenge(any());
        verify(context, never()).success();
    }
}
