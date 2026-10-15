package io.github.alexc.keycloak.passwords;

import com.google.auto.service.AutoService;
import io.github.alexc.keycloak.passwords.client.HibpClient;
import io.github.alexc.keycloak.passwords.client.HibpPrefixCache;
import io.github.alexc.keycloak.passwords.config.BreachedPasswordConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Factory for {@link BreachedPasswordAuthenticator}.
 * Manages shared HTTP client, configuration, and prefix cache lifecycles.
 */
@AutoService(AuthenticatorFactory.class)
public class BreachedPasswordAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "breached-password-authenticator";
    private static final Logger LOG = Logger.getLogger(BreachedPasswordAuthenticatorFactory.class);

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.DISABLED
    };

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

    static {
        List<ProviderConfigProperty> props = new ArrayList<>();

        ProviderConfigProperty mode = new ProviderConfigProperty();
        mode.setName("mode");
        mode.setLabel("Enforcement Mode");
        mode.setType(ProviderConfigProperty.LIST_TYPE);
        mode.setOptions(List.of("strict", "audit-only"));
        mode.setDefaultValue(BreachedPasswordConfig.DEFAULT_MODE);
        mode.setHelpText("Strict mode forces an immediate password change (UPDATE_PASSWORD); audit-only mode merely logs the event.");
        props.add(mode);

        ProviderConfigProperty threshold = new ProviderConfigProperty();
        threshold.setName("breach-threshold");
        threshold.setLabel("Breach Count Threshold");
        threshold.setType(ProviderConfigProperty.STRING_TYPE);
        threshold.setDefaultValue(String.valueOf(BreachedPasswordConfig.DEFAULT_THRESHOLD));
        threshold.setHelpText("Minimum number of times the password must appear in data breaches to be rejected.");
        props.add(threshold);

        ProviderConfigProperty timeout = new ProviderConfigProperty();
        timeout.setName("api-timeout-ms");
        timeout.setLabel("API Timeout (ms)");
        timeout.setType(ProviderConfigProperty.STRING_TYPE);
        timeout.setDefaultValue(String.valueOf(BreachedPasswordConfig.DEFAULT_TIMEOUT_MS));
        timeout.setHelpText("Maximum timeout in milliseconds for connecting and reading from HaveIBeenPwned API.");
        props.add(timeout);

        ProviderConfigProperty failOpen = new ProviderConfigProperty();
        failOpen.setName("fail-open");
        failOpen.setLabel("Fail Open Resilience");
        failOpen.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        failOpen.setDefaultValue(String.valueOf(BreachedPasswordConfig.DEFAULT_FAIL_OPEN));
        failOpen.setHelpText("If true, allows authentication to proceed when HIBP is unreachable, preventing denial of service.");
        props.add(failOpen);

        ProviderConfigProperty apiUrl = new ProviderConfigProperty();
        apiUrl.setName("api-url");
        apiUrl.setLabel("HIBP API URL");
        apiUrl.setType(ProviderConfigProperty.STRING_TYPE);
        apiUrl.setDefaultValue(BreachedPasswordConfig.DEFAULT_API_URL);
        apiUrl.setHelpText("Base URL for the HaveIBeenPwned k-Anonymity range API.");
        props.add(apiUrl);

        ProviderConfigProperty cacheEntries = new ProviderConfigProperty();
        cacheEntries.setName("cache-max-entries");
        cacheEntries.setLabel("Prefix Cache Capacity");
        cacheEntries.setType(ProviderConfigProperty.STRING_TYPE);
        cacheEntries.setDefaultValue(String.valueOf(BreachedPasswordConfig.DEFAULT_CACHE_MAX_ENTRIES));
        cacheEntries.setHelpText("Maximum number of 5-character SHA-1 range responses cached in memory.");
        props.add(cacheEntries);

        ProviderConfigProperty cacheTtl = new ProviderConfigProperty();
        cacheTtl.setName("cache-ttl-minutes");
        cacheTtl.setLabel("Cache TTL (minutes)");
        cacheTtl.setType(ProviderConfigProperty.STRING_TYPE);
        cacheTtl.setDefaultValue(String.valueOf(BreachedPasswordConfig.DEFAULT_CACHE_TTL_MINUTES));
        cacheTtl.setHelpText("Time-to-live in minutes for cached range entries.");
        props.add(cacheTtl);

        CONFIG_PROPERTIES = Collections.unmodifiableList(props);
    }

    private BreachedPasswordConfig config;
    private HttpClient httpClient;
    private HibpPrefixCache cache;
    private HibpClient hibpClient;

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Breached Password Detector";
    }

    @Override
    public String getReferenceCategory() {
        return "password";
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
    public String getHelpText() {
        return "Detects compromised passwords in real-time using HaveIBeenPwned k-Anonymity (NIST SP 800-63B / PCI-DSS v4.0).";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new BreachedPasswordAuthenticator(session, hibpClient, config);
    }

    @Override
    public void init(Config.Scope configScope) {
        this.config = BreachedPasswordConfig.fromScope(configScope);
        this.cache = new HibpPrefixCache(config.cacheMaxEntries(), config.cacheTtlMinutes());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.apiTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.hibpClient = new HibpClient(httpClient, config, cache);

        LOG.infov("Initialized BreachedPasswordAuthenticatorFactory (mode={0}, threshold={1}, timeout={2}ms, failOpen={3}, cacheCapacity={4})",
                config.mode(), config.breachThreshold(), config.apiTimeoutMs(), config.failOpen(), config.cacheMaxEntries());
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No cross-provider post-initialization required
    }

    @Override
    public void close() {
        if (cache != null) {
            cache.clear();
        }
    }
}
