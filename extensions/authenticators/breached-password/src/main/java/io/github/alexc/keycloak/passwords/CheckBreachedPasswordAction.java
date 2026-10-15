package io.github.alexc.keycloak.passwords;

import org.jboss.logging.Logger;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.UserModel;

/**
 * RequiredActionProvider executed when a user must update their password due to a detected breach.
 */
public class CheckBreachedPasswordAction implements RequiredActionProvider {

    private static final Logger LOG = Logger.getLogger(CheckBreachedPasswordAction.class);

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        // Fast, instant trigger evaluation (O(1)) without external I/O as per SPI standards
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        UserModel user = context.getUser();
        LOG.infov("Triggering password update required action for user {0} due to breached password",
                user != null ? user.getUsername() : "unknown");

        // Forward to standard Keycloak UPDATE_PASSWORD required action
        context.getAuthenticationSession().addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
        if (user != null) {
            user.addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
            user.removeRequiredAction(CheckBreachedPasswordActionFactory.PROVIDER_ID);
        }
        context.success();
    }

    @Override
    public void processAction(RequiredActionContext context) {
        UserModel user = context.getUser();
        if (user != null) {
            user.removeRequiredAction(CheckBreachedPasswordActionFactory.PROVIDER_ID);
        }
        context.success();
    }

    @Override
    public void close() {
        // Request-scoped resources cleanup
    }
}
