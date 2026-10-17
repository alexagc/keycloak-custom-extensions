package io.github.alexc.keycloak.ageproofing;

import io.github.alexc.keycloak.ageproofing.challenge.OpenId4VpChallengeGenerator;
import io.github.alexc.keycloak.ageproofing.challenge.OpenId4VpChallengeGenerator.ChallengeDescriptor;
import io.github.alexc.keycloak.ageproofing.crypto.VerifiablePresentationValidator;
import io.github.alexc.keycloak.ageproofing.model.AgeVerificationConfig;
import io.github.alexc.keycloak.ageproofing.model.VerificationEvidence;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Instant;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Request-scoped Authenticator implementing privacy-preserving Age &amp; Identity Proofing (eIDAS 2.0 / OpenID4VP).
 */
public class AgeProofingAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(AgeProofingAuthenticator.class);

    public static final String ATTR_AGE_VERIFIED = "age_verified";
    public static final String ATTR_AGE_VERIFIED_AT = "age_verified_at";
    public static final String ATTR_AGE_PROOF_HASH = "age_proof_hash";
    public static final String ATTR_AGE_PROOF_ISSUER = "age_proof_issuer";
    public static final String AUTH_NOTE_NONCE = "age_proofing_nonce";

    private final KeycloakSession session;
    private final VerifiablePresentationValidator validator;
    private final AgeVerificationConfig baseConfig;

    public AgeProofingAuthenticator(KeycloakSession session, VerifiablePresentationValidator validator, AgeVerificationConfig baseConfig) {
        this.session = session;
        this.validator = validator;
        this.baseConfig = baseConfig;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            LOG.warn("No user associated with current authentication flow context");
            context.failure(AuthenticationFlowError.UNKNOWN_USER);
            return;
        }

        AgeVerificationConfig config = baseConfig.withExecutionConfig(context.getAuthenticatorConfig());

        if (isUserVerified(user, config)) {
            LOG.debugv("User {0} already possesses valid age verification", user.getUsername());
            context.success();
            return;
        }

        renderChallenge(context, config, null);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            context.failure(AuthenticationFlowError.UNKNOWN_USER);
            return;
        }

        AgeVerificationConfig config = baseConfig.withExecutionConfig(context.getAuthenticatorConfig());

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String rawToken = formData.getFirst("vp_token");
        if (rawToken == null || rawToken.isBlank()) {
            rawToken = formData.getFirst("assertion");
        }

        if (rawToken == null || rawToken.isBlank()) {
            renderChallenge(context, config, "ageProofingErrorInvalidToken");
            return;
        }

        String expectedNonce = context.getAuthenticationSession() != null
                ? context.getAuthenticationSession().getAuthNote(AUTH_NOTE_NONCE)
                : null;
        VerificationEvidence evidence = validator.validate(rawToken, config, expectedNonce);
        if (evidence.success()) {
            user.setSingleAttribute(ATTR_AGE_VERIFIED, "true");
            user.setSingleAttribute(ATTR_AGE_VERIFIED_AT, String.valueOf(evidence.verifiedAt()));
            if (evidence.proofHash() != null) {
                user.setSingleAttribute(ATTR_AGE_PROOF_HASH, evidence.proofHash());
            }
            if (evidence.issuer() != null) {
                user.setSingleAttribute(ATTR_AGE_PROOF_ISSUER, evidence.issuer());
            }

            LOG.infov("Age successfully verified for user {0} in realm {1} (issuer: {2})",
                    user.getUsername(), context.getRealm().getName(), evidence.issuer());
            context.success();
        } else {
            LOG.warnv("Age verification rejected for user {0}: {1}", user.getUsername(), evidence.errorMessage());
            if (config.failOpen()) {
                LOG.warn("Proceeding under fail-open policy despite assertion rejection");
                context.success();
                return;
            }

            String errorKey = resolveErrorKey(evidence.errorMessage());
            renderChallenge(context, config, errorKey);
        }
    }

    private boolean isUserVerified(UserModel user, AgeVerificationConfig config) {
        String verified = user.getFirstAttribute(ATTR_AGE_VERIFIED);
        if (!"true".equalsIgnoreCase(verified)) {
            return false;
        }

        String verifiedAtStr = user.getFirstAttribute(ATTR_AGE_VERIFIED_AT);
        if (verifiedAtStr == null) {
            return false;
        }

        try {
            long verifiedAt = Long.parseLong(verifiedAtStr);
            long validitySeconds = config.validityDays() * 86400L;
            long now = Instant.now().getEpochSecond();
            return (now - verifiedAt) < validitySeconds;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void renderChallenge(AuthenticationFlowContext context, AgeVerificationConfig config, String errorKey) {
        URI actionUrl = context.getActionUrl(context.generateAccessCode());
        ChallengeDescriptor challenge = OpenId4VpChallengeGenerator.createChallenge(
                context.getRealm().getName(),
                actionUrl.toString(),
                config.minAge()
        );

        context.getAuthenticationSession().setAuthNote(AUTH_NOTE_NONCE, challenge.nonce());

        var form = context.form()
                .setAttribute("minAge", String.valueOf(config.minAge()))
                .setAttribute("mode", config.mode())
                .setAttribute("openid4vpUri", challenge.openid4vpUri())
                .setAttribute("eidGatewayUrl", config.eidGatewayUrl() != null ? config.eidGatewayUrl().toString() : null);

        if (errorKey != null) {
            form.setError(errorKey, String.valueOf(config.minAge()));
            Response challengeResponse = form.createForm("age-proofing-challenge.ftl");
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challengeResponse);
        } else {
            Response challengeResponse = form.createForm("age-proofing-challenge.ftl");
            context.challenge(challengeResponse);
        }
    }

    private String resolveErrorKey(String errorMessage) {
        if (errorMessage == null) {
            return "ageProofingErrorInvalidToken";
        }
        if (errorMessage.toLowerCase().contains("expired")) {
            return "ageProofingErrorExpired";
        }
        if (errorMessage.toLowerCase().contains("minimum age")) {
            return "ageProofingErrorUnderage";
        }
        return "ageProofingErrorInvalidToken";
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No default required actions to set automatically here
    }

    @Override
    public void close() {
        // No per-request resources requiring disposal
    }
}
