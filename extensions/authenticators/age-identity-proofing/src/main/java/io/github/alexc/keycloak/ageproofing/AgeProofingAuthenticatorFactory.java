package io.github.alexc.keycloak.ageproofing;

import com.google.auto.service.AutoService;
import io.github.alexc.keycloak.ageproofing.crypto.PublicTrustProviderResolver;
import io.github.alexc.keycloak.ageproofing.crypto.VerifiablePresentationValidator;
import io.github.alexc.keycloak.ageproofing.model.AgeVerificationConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Singleton Factory and lifecycle manager for {@link AgeProofingAuthenticator}.
 */
@AutoService(AuthenticatorFactory.class)
public class AgeProofingAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "age-identity-proofing";

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.CONDITIONAL,
            AuthenticationExecutionModel.Requirement.DISABLED
    };

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

    static {
        List<ProviderConfigProperty> properties = new ArrayList<>();

        ProviderConfigProperty minAge = new ProviderConfigProperty();
        minAge.setName("min-age");
        minAge.setLabel("Minimum Legal Age");
        minAge.setHelpText("The minimum legal age required to access age-restricted resources (e.g. 18).");
        minAge.setType(ProviderConfigProperty.STRING_TYPE);
        minAge.setDefaultValue(String.valueOf(AgeVerificationConfig.DEFAULT_MIN_AGE));
        properties.add(minAge);

        ProviderConfigProperty jwksUrl = new ProviderConfigProperty();
        jwksUrl.setName("jwks-url");
        jwksUrl.setLabel("Trusted JWKS URL");
        jwksUrl.setHelpText("URL to the public JSON Web Key Set of the trusted identity authority or wallet gateway.");
        jwksUrl.setType(ProviderConfigProperty.STRING_TYPE);
        properties.add(jwksUrl);

        ProviderConfigProperty validityDays = new ProviderConfigProperty();
        validityDays.setName("validity-days");
        validityDays.setLabel("Evidence Validity (Days)");
        validityDays.setHelpText("Number of days before the user must re-verify their age (default: 365).");
        validityDays.setType(ProviderConfigProperty.STRING_TYPE);
        validityDays.setDefaultValue(String.valueOf(AgeVerificationConfig.DEFAULT_VALIDITY_DAYS));
        properties.add(validityDays);

        ProviderConfigProperty allowedIssuers = new ProviderConfigProperty();
        allowedIssuers.setName("allowed-issuers");
        allowedIssuers.setLabel("Allowed Issuers");
        allowedIssuers.setHelpText("Comma-separated list of permitted identity issuers (leave blank to allow all valid under JWKS).");
        allowedIssuers.setType(ProviderConfigProperty.STRING_TYPE);
        properties.add(allowedIssuers);

        ProviderConfigProperty mode = new ProviderConfigProperty();
        mode.setName("mode");
        mode.setLabel("Interaction Mode");
        mode.setHelpText("Challenge presentation mode: hybrid (QR + eID redirect), qr-wallet (OpenID4VP only), or redirect (eID only).");
        mode.setType(ProviderConfigProperty.LIST_TYPE);
        mode.setOptions(List.of("hybrid", "qr-wallet", "redirect"));
        mode.setDefaultValue(AgeVerificationConfig.DEFAULT_MODE);
        properties.add(mode);

        ProviderConfigProperty failOpen = new ProviderConfigProperty();
        failOpen.setName("fail-open");
        failOpen.setLabel("Fail-Open Policy (Staging Only)");
        failOpen.setHelpText("If enabled, bypasses blocking if the external JWKS service is down. Recommended: false for production.");
        failOpen.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        failOpen.setDefaultValue(String.valueOf(AgeVerificationConfig.DEFAULT_FAIL_OPEN));
        properties.add(failOpen);

        ProviderConfigProperty eidGateway = new ProviderConfigProperty();
        eidGateway.setName("eid-gateway-url");
        eidGateway.setLabel("eID Gateway Web URL");
        eidGateway.setHelpText("Direct URL of the public identity gateway portal (e.g. Cl@ve, FranceConnect).");
        eidGateway.setType(ProviderConfigProperty.STRING_TYPE);
        properties.add(eidGateway);

        ProviderConfigProperty timeout = new ProviderConfigProperty();
        timeout.setName("api-timeout-ms");
        timeout.setLabel("HTTP Timeout (ms)");
        timeout.setHelpText("Connection and read timeout in milliseconds for resolving public JWKS (default: 3000).");
        timeout.setType(ProviderConfigProperty.STRING_TYPE);
        timeout.setDefaultValue(String.valueOf(AgeVerificationConfig.DEFAULT_TIMEOUT_MS));
        properties.add(timeout);

        ProviderConfigProperty ttl = new ProviderConfigProperty();
        ttl.setName("cache-ttl-minutes");
        ttl.setLabel("JWKS Cache TTL (Minutes)");
        ttl.setHelpText("Time-to-live in minutes for caching resolved public keys in memory (default: 60).");
        ttl.setType(ProviderConfigProperty.STRING_TYPE);
        ttl.setDefaultValue(String.valueOf(AgeVerificationConfig.DEFAULT_CACHE_TTL_MINUTES));
        properties.add(ttl);

        CONFIG_PROPERTIES = Collections.unmodifiableList(properties);
    }

    private PublicTrustProviderResolver keyResolver;
    private VerifiablePresentationValidator validator;
    private AgeVerificationConfig baseConfig;

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Age & Identity Proofing";
    }

    @Override
    public String getHelpText() {
        return "Verifies legal age and identity proofing using eIDAS 2.0 / OpenID4VP zero-PII cryptographic assertions.";
    }

    @Override
    public String getReferenceCategory() {
        return "age-verification";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES.clone();
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new AgeProofingAuthenticator(session, validator, baseConfig);
    }

    @Override
    public void init(Config.Scope config) {
        this.baseConfig = AgeVerificationConfig.fromScope(config);
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
