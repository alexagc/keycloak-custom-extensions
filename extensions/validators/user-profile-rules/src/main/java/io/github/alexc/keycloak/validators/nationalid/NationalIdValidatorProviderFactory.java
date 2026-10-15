package io.github.alexc.keycloak.validators.nationalid;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ConfiguredProvider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.validate.ValidationResult;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.Validator;
import org.keycloak.validate.ValidatorConfig;
import org.keycloak.validate.ValidatorFactory;

/**
 * Factory and metadata provider for {@link NationalIdValidatorProvider}.
 *
 * <p>Initializes the {@link CountryStrategyRegistry} server singleton and exposes configuration
 * options for Keycloak's User Profile Admin Console.
 */
@AutoService(ValidatorFactory.class)
public class NationalIdValidatorProviderFactory implements ValidatorFactory, ConfiguredProvider {

  public static final String ID = "national-id";

  public static final String CONF_COUNTRY = "country";
  public static final String CONF_ALLOW_FOREIGNERS = "allow-foreigners";
  public static final String CONF_NORMALIZE = "normalize";

  public static final boolean DEFAULT_ALLOW_FOREIGNERS = true;
  public static final boolean DEFAULT_NORMALIZE = true;

  private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

  private CountryStrategyRegistry strategyRegistry;

  static {
    List<ProviderConfigProperty> properties = new ArrayList<>();

    ProviderConfigProperty countryProp = new ProviderConfigProperty();
    countryProp.setName(CONF_COUNTRY);
    countryProp.setLabel("Country Code / Attribute");
    countryProp.setHelpText(
        "ISO 3166-1 alpha-2 country code (e.g. ES, IT, FR, BR, US, DE) or dynamic attribute reference (e.g. attribute:country).");
    countryProp.setType(ProviderConfigProperty.STRING_TYPE);
    properties.add(countryProp);

    ProviderConfigProperty allowForeignersProp = new ProviderConfigProperty();
    allowForeignersProp.setName(CONF_ALLOW_FOREIGNERS);
    allowForeignersProp.setLabel("Allow Foreigners");
    allowForeignersProp.setHelpText(
        "Whether to allow document formats issued to foreign residents (e.g. NIE in Spain).");
    allowForeignersProp.setType(ProviderConfigProperty.BOOLEAN_TYPE);
    allowForeignersProp.setDefaultValue(Boolean.TRUE);
    properties.add(allowForeignersProp);

    ProviderConfigProperty normalizeProp = new ProviderConfigProperty();
    normalizeProp.setName(CONF_NORMALIZE);
    normalizeProp.setLabel("Normalize Input");
    normalizeProp.setHelpText(
        "Automatically strip spaces, hyphens, and dots, and convert to uppercase before validating.");
    normalizeProp.setType(ProviderConfigProperty.BOOLEAN_TYPE);
    normalizeProp.setDefaultValue(Boolean.TRUE);
    properties.add(normalizeProp);

    CONFIG_PROPERTIES = Collections.unmodifiableList(properties);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public Validator create(KeycloakSession session) {
    return new NationalIdValidatorProvider(strategyRegistry);
  }

  @Override
  public void init(Config.Scope config) {
    this.strategyRegistry = new CountryStrategyRegistry();
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    // No post-initialization required
  }

  @Override
  public void close() {
    this.strategyRegistry = null;
  }

  @Override
  public ValidationResult validateConfig(KeycloakSession session, ValidatorConfig config) {
    if (config == null) {
      return new ValidationResult(Collections.emptySet());
    }

    Set<ValidationError> errors = new HashSet<>();

    if (config.containsKey(CONF_COUNTRY)) {
      String country = config.getString(CONF_COUNTRY);
      if (country == null || country.trim().isEmpty()) {
        errors.add(
            new ValidationError(
                ID, CONF_COUNTRY, "error-user-profile-national-id-config-empty-country"));
      } else if (!country.trim().startsWith("attribute:")) {
        CountryStrategyRegistry reg =
            strategyRegistry != null ? strategyRegistry : new CountryStrategyRegistry();
        if (!reg.isSupported(country.trim())) {
          errors.add(
              new ValidationError(
                  ID,
                  CONF_COUNTRY,
                  "error-user-profile-national-id-config-unsupported-country",
                  country.trim()));
        }
      }
    }

    return new ValidationResult(errors);
  }

  @Override
  public String getHelpText() {
    return "Validates national identity documents against official checksum algorithms (ES, IT, FR, BR, US, DE).";
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return CONFIG_PROPERTIES;
  }

  /**
   * Package-private accessor for tests.
   *
   * @return strategy registry
   */
  CountryStrategyRegistry getStrategyRegistry() {
    return strategyRegistry;
  }
}
