package io.github.alexc.keycloak.authenticator.ratelimit;

import com.google.auto.service.AutoService;
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
 * Singleton factory and lifecycle manager for {@link DistributedRateLimiterAuthenticator}.
 *
 * <p>Initializes the shared {@link RateLimiterClient} connection pool at server startup,
 * provides configuration properties for the Keycloak Admin Console, and gracefully closes
 * connection resources upon shutdown.
 */
@AutoService(AuthenticatorFactory.class)
public class DistributedRateLimiterAuthenticatorFactory implements AuthenticatorFactory {

  public static final String PROVIDER_ID = "distributed-rate-limiter";
  private static final Logger LOG =
      Logger.getLogger(DistributedRateLimiterAuthenticatorFactory.class);

  private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
    AuthenticationExecutionModel.Requirement.REQUIRED,
    AuthenticationExecutionModel.Requirement.ALTERNATIVE,
    AuthenticationExecutionModel.Requirement.DISABLED
  };

  private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

  static {
    List<ProviderConfigProperty> props = new ArrayList<>();

    ProviderConfigProperty redisUri = new ProviderConfigProperty();
    redisUri.setName(RateLimiterConfig.CONF_REDIS_URI);
    redisUri.setLabel("Valkey / Redis URI");
    redisUri.setHelpText(
        "Connection URI for Valkey or Redis (supports valkey://, valkeys://, redis://, rediss://).");
    redisUri.setType(ProviderConfigProperty.STRING_TYPE);
    redisUri.setDefaultValue(RateLimiterConfig.DEFAULT_REDIS_URI);
    props.add(redisUri);

    ProviderConfigProperty ipLimit = new ProviderConfigProperty();
    ipLimit.setName(RateLimiterConfig.CONF_IP_LIMIT);
    ipLimit.setLabel("IP Rate Limit");
    ipLimit.setHelpText("Maximum requests allowed per client IP within the sliding window.");
    ipLimit.setType(ProviderConfigProperty.STRING_TYPE);
    ipLimit.setDefaultValue(String.valueOf(RateLimiterConfig.DEFAULT_IP_LIMIT));
    props.add(ipLimit);

    ProviderConfigProperty ipWindow = new ProviderConfigProperty();
    ipWindow.setName(RateLimiterConfig.CONF_IP_WINDOW);
    ipWindow.setLabel("IP Sliding Window (seconds)");
    ipWindow.setHelpText("Duration in seconds for the client IP sliding window.");
    ipWindow.setType(ProviderConfigProperty.STRING_TYPE);
    ipWindow.setDefaultValue(String.valueOf(RateLimiterConfig.DEFAULT_IP_WINDOW));
    props.add(ipWindow);

    ProviderConfigProperty userLimit = new ProviderConfigProperty();
    userLimit.setName(RateLimiterConfig.CONF_USER_LIMIT);
    userLimit.setLabel("User Account Limit");
    userLimit.setHelpText("Maximum attempts allowed per username/account within the sliding window.");
    userLimit.setType(ProviderConfigProperty.STRING_TYPE);
    userLimit.setDefaultValue(String.valueOf(RateLimiterConfig.DEFAULT_USER_LIMIT));
    props.add(userLimit);

    ProviderConfigProperty userWindow = new ProviderConfigProperty();
    userWindow.setName(RateLimiterConfig.CONF_USER_WINDOW);
    userWindow.setLabel("User Sliding Window (seconds)");
    userWindow.setHelpText("Duration in seconds for the username sliding window.");
    userWindow.setType(ProviderConfigProperty.STRING_TYPE);
    userWindow.setDefaultValue(String.valueOf(RateLimiterConfig.DEFAULT_USER_WINDOW));
    props.add(userWindow);

    ProviderConfigProperty failOpen = new ProviderConfigProperty();
    failOpen.setName(RateLimiterConfig.CONF_FAIL_OPEN);
    failOpen.setLabel("Fail-Open Resilience");
    failOpen.setHelpText(
        "If true, allows requests through when Valkey/Redis is unreachable. If false, returns HTTP 503.");
    failOpen.setType(ProviderConfigProperty.BOOLEAN_TYPE);
    failOpen.setDefaultValue(String.valueOf(RateLimiterConfig.DEFAULT_FAIL_OPEN));
    props.add(failOpen);

    ProviderConfigProperty connTimeout = new ProviderConfigProperty();
    connTimeout.setName(RateLimiterConfig.CONF_CONNECT_TIMEOUT);
    connTimeout.setLabel("Connect Timeout (ms)");
    connTimeout.setHelpText("Socket connection timeout in milliseconds.");
    connTimeout.setType(ProviderConfigProperty.STRING_TYPE);
    connTimeout.setDefaultValue(String.valueOf(RateLimiterConfig.DEFAULT_CONNECT_TIMEOUT));
    props.add(connTimeout);

    ProviderConfigProperty sockTimeout = new ProviderConfigProperty();
    sockTimeout.setName(RateLimiterConfig.CONF_SOCKET_TIMEOUT);
    sockTimeout.setLabel("Socket Timeout (ms)");
    sockTimeout.setHelpText("Socket read/command timeout in milliseconds.");
    sockTimeout.setType(ProviderConfigProperty.STRING_TYPE);
    sockTimeout.setDefaultValue(String.valueOf(RateLimiterConfig.DEFAULT_SOCKET_TIMEOUT));
    props.add(sockTimeout);

    ProviderConfigProperty poolSize = new ProviderConfigProperty();
    poolSize.setName(RateLimiterConfig.CONF_MAX_POOL_SIZE);
    poolSize.setLabel("Max Connection Pool Size");
    poolSize.setHelpText("Maximum number of concurrent connections in the Jedis connection pool.");
    poolSize.setType(ProviderConfigProperty.STRING_TYPE);
    poolSize.setDefaultValue(String.valueOf(RateLimiterConfig.DEFAULT_MAX_POOL_SIZE));
    props.add(poolSize);

    CONFIG_PROPERTIES = Collections.unmodifiableList(props);
  }

  private RateLimiterConfig globalConfig;
  private RateLimiterClient client;

  /**
   * Default constructor required for ServiceLoader.
   */
  public DistributedRateLimiterAuthenticatorFactory() {}

  /**
   * Package-private constructor for testing with custom dependencies.
   *
   * @param client custom rate limiter client
   * @param globalConfig custom global configuration
   */
  DistributedRateLimiterAuthenticatorFactory(
      RateLimiterClient client, RateLimiterConfig globalConfig) {
    this.client = client;
    this.globalConfig = globalConfig;
  }

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getDisplayType() {
    return "Distributed Rate Limiter (Valkey/Redis)";
  }

  @Override
  public String getHelpText() {
    return "Distributed sliding window rate limiter backed by Valkey/Redis cluster protecting against brute-force and credential stuffing.";
  }

  @Override
  public String getReferenceCategory() {
    return "rate-limiter";
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
    RateLimiterConfig effectiveConfig =
        this.globalConfig != null ? this.globalConfig : RateLimiterConfig.defaultConfiguration();
    return new DistributedRateLimiterAuthenticator(session, this.client, effectiveConfig);
  }

  @Override
  public void init(Config.Scope config) {
    if (this.globalConfig == null) {
      this.globalConfig = RateLimiterConfig.fromScope(config);
    }
    if (this.client == null) {
      try {
        this.client = new ValkeyRateLimiterClient(this.globalConfig);
      } catch (Exception e) {
        LOG.errorv(
            e,
            "Failed to initialize ValkeyRateLimiterClient during factory init. Will fail-open if configured.");
      }
    }
    LOG.infov("Initialized DistributedRateLimiterAuthenticatorFactory (id={0})", PROVIDER_ID);
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    // No post-init actions needed
  }

  @Override
  public void close() {
    if (this.client != null) {
      try {
        this.client.close();
      } catch (Exception e) {
        LOG.warnv("Error closing RateLimiterClient: {0}", e.getMessage());
      }
    }
  }

  RateLimiterClient getClient() {
    return this.client;
  }

  RateLimiterConfig getGlobalConfig() {
    return this.globalConfig;
  }
}
