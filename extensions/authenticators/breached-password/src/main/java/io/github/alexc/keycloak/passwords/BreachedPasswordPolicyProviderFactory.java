package io.github.alexc.keycloak.passwords;

import com.google.auto.service.AutoService;
import io.github.alexc.keycloak.passwords.client.HibpClient;
import io.github.alexc.keycloak.passwords.client.HibpPrefixCache;
import io.github.alexc.keycloak.passwords.config.BreachedPasswordConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.policy.PasswordPolicyProvider;
import org.keycloak.policy.PasswordPolicyProviderFactory;

/**
 * Factory for {@link BreachedPasswordPolicyProvider}.
 */
@AutoService(PasswordPolicyProviderFactory.class)
public class BreachedPasswordPolicyProviderFactory implements PasswordPolicyProviderFactory {

    public static final String PROVIDER_ID = "breached-password-policy";
    private static final Logger LOG = Logger.getLogger(BreachedPasswordPolicyProviderFactory.class);

    private BreachedPasswordConfig config;
    private HttpClient httpClient;
    private HibpPrefixCache cache;
    private HibpClient hibpClient;

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayName() {
        return "Reject Breached Passwords (HIBP)";
    }

    @Override
    public String getConfigType() {
        return PasswordPolicyProvider.INT_CONFIG_TYPE;
    }

    @Override
    public String getDefaultConfigValue() {
        return String.valueOf(BreachedPasswordConfig.DEFAULT_THRESHOLD);
    }

    @Override
    public boolean isMultiplSupported() {
        return false;
    }

    @Override
    public PasswordPolicyProvider create(KeycloakSession session) {
        return new BreachedPasswordPolicyProvider(session, hibpClient, config);
    }

    @Override
    public void init(Config.Scope configScope) {
        this.config = BreachedPasswordConfig.fromScope(configScope);
        this.cache = HibpPrefixCache.getOrCreateShared(config.cacheMaxEntries(), config.cacheTtlMinutes());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.apiTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.hibpClient = new HibpClient(httpClient, config, cache);

        LOG.infov("Initialized BreachedPasswordPolicyProviderFactory (id={0}, threshold={1}, timeout={2}ms)",
                PROVIDER_ID, config.breachThreshold(), config.apiTimeoutMs());
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No cross-provider post-initialization required
    }

    @Override
    public void close() {
        if (httpClient != null) {
            httpClient.close();
        }
        if (cache != null) {
            cache.clear();
        }
    }
}
