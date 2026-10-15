package io.github.alexc.keycloak.validators.email;

import com.google.auto.service.AutoService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ConfiguredProvider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.validate.ValidationResult;
import org.keycloak.validate.Validator;
import org.keycloak.validate.ValidatorConfig;
import org.keycloak.validate.ValidatorFactory;

/**
 * Factory and lifecycle manager for {@link DisposableEmailValidatorProvider}.
 *
 * <p>Loads the embedded domain blocklist from {@value #RESOURCE_PATH} into memory once during server
 * initialization.
 */
@AutoService(ValidatorFactory.class)
public class DisposableEmailValidatorProviderFactory
    implements ValidatorFactory, ConfiguredProvider {

  private static final Logger LOG = Logger.getLogger(DisposableEmailValidatorProviderFactory.class);

  public static final String ID = "disposable-email";
  public static final String CONF_BLOCK_SUBDOMAINS = "block-subdomains";
  public static final String CONF_CUSTOM_BLOCKED_DOMAINS = "custom-blocked-domains";

  public static final boolean DEFAULT_BLOCK_SUBDOMAINS = true;
  public static final String RESOURCE_PATH = "/disposable-domains.txt";

  private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

  private Set<String> preloadedBlockedDomains = Collections.emptySet();

  static {
    List<ProviderConfigProperty> properties = new ArrayList<>();

    ProviderConfigProperty blockSubdomainsProp = new ProviderConfigProperty();
    blockSubdomainsProp.setName(CONF_BLOCK_SUBDOMAINS);
    blockSubdomainsProp.setLabel("Block Subdomains");
    blockSubdomainsProp.setHelpText(
        "Whether to automatically block subdomains of blocked domains (e.g. foo.mailinator.com).");
    blockSubdomainsProp.setType(ProviderConfigProperty.BOOLEAN_TYPE);
    blockSubdomainsProp.setDefaultValue(Boolean.TRUE);
    properties.add(blockSubdomainsProp);

    ProviderConfigProperty customDomainsProp = new ProviderConfigProperty();
    customDomainsProp.setName(CONF_CUSTOM_BLOCKED_DOMAINS);
    customDomainsProp.setLabel("Custom Blocked Domains");
    customDomainsProp.setHelpText(
        "Comma-separated list of additional domains to reject (e.g. spamcorp.org, example.fake).");
    customDomainsProp.setType(ProviderConfigProperty.STRING_TYPE);
    properties.add(customDomainsProp);

    CONFIG_PROPERTIES = Collections.unmodifiableList(properties);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public Validator create(KeycloakSession session) {
    return new DisposableEmailValidatorProvider(preloadedBlockedDomains);
  }

  @Override
  public void init(Config.Scope config) {
    Set<String> loaded = new HashSet<>();
    try (InputStream in = getClass().getResourceAsStream(RESOURCE_PATH)) {
      if (in != null) {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            String trimmed = line.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
              loaded.add(trimmed);
            }
          }
        }
      } else {
        LOG.warnv("Resource {0} not found on classpath; disposable email list is empty", RESOURCE_PATH);
      }
    } catch (IOException e) {
      LOG.errorv(e, "Failed to load disposable email domains from {0}", RESOURCE_PATH);
    }

    this.preloadedBlockedDomains = Collections.unmodifiableSet(loaded);
    LOG.infov("Loaded {0} disposable email domains into memory", preloadedBlockedDomains.size());
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    // No post-initialization required
  }

  @Override
  public void close() {
    preloadedBlockedDomains = Collections.emptySet();
  }

  @Override
  public ValidationResult validateConfig(KeycloakSession session, ValidatorConfig config) {
    return new ValidationResult();
  }

  @Override
  public String getHelpText() {
    return "Validates that the email domain does not belong to a known disposable or temporary email provider.";
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return CONFIG_PROPERTIES;
  }

  /**
   * Package-private accessor for unit testing to verify loaded domains.
   *
   * @return immutable set of loaded blocked domains
   */
  Set<String> getPreloadedBlockedDomains() {
    return preloadedBlockedDomains;
  }

  /**
   * Package-private setter for unit testing to inject custom domain sets.
   *
   * @param domains domains to set
   */
  void setPreloadedBlockedDomains(Set<String> domains) {
    this.preloadedBlockedDomains =
        domains != null ? Collections.unmodifiableSet(domains) : Collections.emptySet();
  }
}
