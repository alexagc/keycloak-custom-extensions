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
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;

/**
 * RequiredActionProvider executing Age &amp; Identity Proofing for onboarding or policy enforcement.
 */
public class AgeProofingRequiredAction implements RequiredActionProvider {

    private static final Logger LOG = Logger.getLogger(AgeProofingRequiredAction.class);

    private final KeycloakSession session;
    private final VerifiablePresentationValidator validator;
    private final AgeVerificationConfig config;

    public AgeProofingRequiredAction(KeycloakSession session, VerifiablePresentationValidator validator, AgeVerificationConfig config) {
        this.session = session;
        this.validator = validator;
        this.config = config;
    }

    @Override
    public InitiatedActionSupport initiatedActionSupport() {
        return InitiatedActionSupport.SUPPORTED;
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            return;
        }

        String verified = user.getFirstAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED);
        if (!"true".equalsIgnoreCase(verified)) {
            user.addRequiredAction(AgeProofingRequiredActionFactory.PROVIDER_ID);
            return;
        }

        String verifiedAtStr = user.getFirstAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED_AT);
        if (verifiedAtStr == null) {
            user.addRequiredAction(AgeProofingRequiredActionFactory.PROVIDER_ID);
            return;
        }

        try {
            long verifiedAt = Long.parseLong(verifiedAtStr);
            long validitySeconds = config.validityDays() * 86400L;
            long now = Instant.now().getEpochSecond();
            if ((now - verifiedAt) >= validitySeconds) {
                user.addRequiredAction(AgeProofingRequiredActionFactory.PROVIDER_ID);
            }
        } catch (NumberFormatException e) {
            user.addRequiredAction(AgeProofingRequiredActionFactory.PROVIDER_ID);
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        renderChallenge(context, null);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            context.failure();
            return;
        }

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String rawToken = formData.getFirst("vp_token");
        if (rawToken == null || rawToken.isBlank()) {
            rawToken = formData.getFirst("assertion");
        }

        if (rawToken == null || rawToken.isBlank()) {
            renderChallenge(context, "ageProofingErrorInvalidToken");
            return;
        }

        VerificationEvidence evidence = validator.validate(rawToken, config);
        if (evidence.success()) {
            user.setSingleAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED, "true");
            user.setSingleAttribute(AgeProofingAuthenticator.ATTR_AGE_VERIFIED_AT, String.valueOf(evidence.verifiedAt()));
            if (evidence.proofHash() != null) {
                user.setSingleAttribute(AgeProofingAuthenticator.ATTR_AGE_PROOF_HASH, evidence.proofHash());
            }
            if (evidence.issuer() != null) {
                user.setSingleAttribute(AgeProofingAuthenticator.ATTR_AGE_PROOF_ISSUER, evidence.issuer());
            }

            user.removeRequiredAction(AgeProofingRequiredActionFactory.PROVIDER_ID);
            LOG.infov("RequiredAction age proofing completed for user {0} (issuer: {1})", user.getUsername(), evidence.issuer());
            context.success();
        } else {
            LOG.warnv("RequiredAction age proofing rejected for user {0}: {1}", user.getUsername(), evidence.errorMessage());
            if (config.failOpen()) {
                user.removeRequiredAction(AgeProofingRequiredActionFactory.PROVIDER_ID);
                context.success();
                return;
            }

            String errorKey = resolveErrorKey(evidence.errorMessage());
            renderChallenge(context, errorKey);
        }
    }

    private void renderChallenge(RequiredActionContext context, String errorKey) {
        URI actionUrl = context.getActionUrl();
        ChallengeDescriptor challenge = OpenId4VpChallengeGenerator.createChallenge(
                context.getRealm().getName(),
                actionUrl.toString(),
                config.minAge()
        );

        var form = context.form()
                .setAttribute("minAge", String.valueOf(config.minAge()))
                .setAttribute("mode", config.mode())
                .setAttribute("openid4vpUri", challenge.openid4vpUri())
                .setAttribute("eidGatewayUrl", config.eidGatewayUrl() != null ? config.eidGatewayUrl().toString() : null);

        if (errorKey != null) {
            form.setError(errorKey, String.valueOf(config.minAge()));
        }

        Response response = form.createForm("age-proofing-challenge.ftl");
        context.challenge(response);
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
    public void close() {
        // No per-request cleanup needed
    }
}
