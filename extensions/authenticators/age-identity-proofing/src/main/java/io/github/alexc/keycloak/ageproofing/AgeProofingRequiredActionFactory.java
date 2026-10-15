package io.github.alexc.keycloak.ageproofing;

import com.google.auto.service.AutoService;
import io.github.alexc.keycloak.ageproofing.crypto.PublicTrustProviderResolver;
import io.github.alexc.keycloak.ageproofing.crypto.VerifiablePresentationValidator;
import io.github.alexc.keycloak.ageproofing.model.AgeVerificationConfig;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Singleton Factory for {@link AgeProofingRequiredAction}.
 */
@AutoService(RequiredActionFactory.class)
public class AgeProofingRequiredActionFactory implements RequiredActionFactory {

    public static final String PROVIDER_ID = "age-verification-action";

    private PublicTrustProviderResolver keyResolver;
    private VerifiablePresentationValidator validator;
    private AgeVerificationConfig config;

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayText() {
        return "Age & Identity Verification (eIDAS 2.0 / OpenID4VP)";
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return new AgeProofingRequiredAction(session, validator, config);
    }

    @Override
    public void init(Config.Scope scope) {
        this.config = AgeVerificationConfig.fromScope(scope);
        this.keyResolver = new PublicTrustProviderResolver();
        this.validator = new VerifiablePresentationValidator(this.keyResolver);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        if (keyResolver != null) {
            keyResolver.clearCache();
        }
    }
}
