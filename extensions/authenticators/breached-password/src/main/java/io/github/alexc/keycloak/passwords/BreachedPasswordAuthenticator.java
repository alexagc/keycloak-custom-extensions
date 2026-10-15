package io.github.alexc.keycloak.passwords;

import io.github.alexc.keycloak.passwords.client.BreachCheckResult;
import io.github.alexc.keycloak.passwords.client.HibpClient;
import io.github.alexc.keycloak.passwords.config.BreachedPasswordConfig;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Keycloak Authenticator that checks passwords against known public data breaches
 * using the HaveIBeenPwned k-Anonymity API.
 */
public class BreachedPasswordAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(BreachedPasswordAuthenticator.class);
    private static final String FORM_PASSWORD = "password";

    private final KeycloakSession session;
    private final HibpClient hibpClient;
    private final BreachedPasswordConfig config;

    /**
     * Creates a new instance of the authenticator within the current Keycloak session.
     *
     * @param session    current request KeycloakSession
     * @param hibpClient client for HIBP k-Anonymity queries
     * @param config     authenticator configuration
     */
    public BreachedPasswordAuthenticator(KeycloakSession session, HibpClient hibpClient, BreachedPasswordConfig config) {
        this.session = session;
        this.hibpClient = hibpClient;
        this.config = config;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            context.attempted();
            return;
        }

        MultivaluedMap<String, String> formParams = context.getHttpRequest().getDecodedFormParameters();
        String password = formParams != null ? formParams.getFirst(FORM_PASSWORD) : null;

        // If no password was provided in this request (e.g. WebAuthn, OTP-only, SSO federation), skip check
        if (password == null || password.isBlank()) {
            context.success();
            return;
        }

        BreachCheckResult result = hibpClient.checkPassword(password);
        EventBuilder event = context.getEvent();

        if (result.degraded()) {
            LOG.warnv("Breached password lookup degraded (fail-open) for user {0} in realm {1}",
                    user.getUsername(), context.getRealm().getName());
            if (event != null) {
                event.detail("breached_password_check", "degraded")
                        .detail("breached_password_reason", "hibp_unavailable");
            }
            context.success();
            return;
        }

        if (result.breached()) {
            int breachCount = result.breachCount();
            LOG.warnv("Breached password detected for user {0} in realm {1} (count={2}, mode={3})",
                    user.getUsername(), context.getRealm().getName(), breachCount, config.mode());

            if (event != null) {
                event.detail("breached_password_detected", "true")
                        .detail("breached_password_count", String.valueOf(breachCount));
            }

            if (config.isStrict()) {
                // Force UPDATE_PASSWORD required action in this session so user must change password
                context.getAuthenticationSession().addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
                user.addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
            }
        }

        context.success();
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // Form actions handled by authenticate()
        context.success();
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
        // Dynamic assignment handled during authenticate()
    }

    @Override
    public void close() {
        // Request-scoped resources cleanup
    }
}
