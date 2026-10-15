package io.github.alexc.keycloak.passwords;

import io.github.alexc.keycloak.passwords.client.BreachCheckResult;
import io.github.alexc.keycloak.passwords.client.HibpClient;
import io.github.alexc.keycloak.passwords.config.BreachedPasswordConfig;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.policy.PasswordPolicyProvider;
import org.keycloak.policy.PolicyError;

/**
 * Keycloak PasswordPolicyProvider that actively prevents users from selecting
 * compromised passwords during registration, reset, or profile updates.
 */
public class BreachedPasswordPolicyProvider implements PasswordPolicyProvider {

    private static final Logger LOG = Logger.getLogger(BreachedPasswordPolicyProvider.class);
    public static final String ERROR_KEY = "breachedPasswordPolicyError";

    private final KeycloakSession session;
    private final HibpClient hibpClient;
    private final BreachedPasswordConfig config;

    /**
     * Initializes the policy provider for the current session.
     *
     * @param session    current request KeycloakSession
     * @param hibpClient client for HIBP k-Anonymity queries
     * @param config     extension configuration
     */
    public BreachedPasswordPolicyProvider(KeycloakSession session, HibpClient hibpClient, BreachedPasswordConfig config) {
        this.session = session;
        this.hibpClient = hibpClient;
        this.config = config;
    }

    @Override
    public PolicyError validate(RealmModel realm, UserModel user, String password) {
        if (password == null || password.isBlank()) {
            return null;
        }

        BreachCheckResult result = hibpClient.checkPassword(password);
        if (result.degraded()) {
            LOG.warnv("Breached password policy check degraded (fail-open) for realm {0}",
                    realm != null ? realm.getName() : "unknown");
            return null;
        }

        if (result.breached()) {
            int count = result.breachCount();
            String username = user != null ? user.getUsername() : "new_user";
            LOG.warnv("Password rejected by breached password policy for user {0} (count={1})", username, count);
            return new PolicyError(ERROR_KEY, count);
        }

        return null;
    }

    @Override
    public PolicyError validate(String user, String password) {
        return validate(null, null, password);
    }

    @Override
    public Object parseConfig(String value) {
        if (value == null || value.isBlank()) {
            return config.breachThreshold();
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return config.breachThreshold();
        }
    }

    @Override
    public void close() {
        // Request-scoped resources cleanup
    }
}
