package io.github.alexc.keycloak.passwords;

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Factory for {@link CheckBreachedPasswordAction}.
 */
@AutoService(RequiredActionFactory.class)
public class CheckBreachedPasswordActionFactory implements RequiredActionFactory {

    public static final String PROVIDER_ID = "check-breached-password-action";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayText() {
        return "Update Breached Password";
    }

    @Override
    public boolean isOneTimeAction() {
        return true;
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return new CheckBreachedPasswordAction();
    }

    @Override
    public void init(Config.Scope config) {
        // No factory-level configuration required
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No cross-provider post-initialization required
    }

    @Override
    public void close() {
        // No resources to release
    }
}
