package io.github.alexc.keycloak.validators.age;

import com.google.auto.service.AutoService;
import java.time.format.DateTimeFormatter;
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
 * Factory and metadata provider for {@link MinimumAgeValidatorProvider}.
 *
 * <p>Registered under ID {@value #ID} and annotated with {@link AutoService} to automatically
 * generate {@code META-INF/services/org.keycloak.validate.ValidatorFactory}.
 */
@AutoService(ValidatorFactory.class)
public class MinimumAgeValidatorProviderFactory implements ValidatorFactory, ConfiguredProvider {

  public static final String ID = "minimum-age";
  public static final String CONF_MIN_AGE = "min-age";
  public static final String CONF_DATE_FORMAT = "date-format";

  public static final int DEFAULT_MIN_AGE = 18;
  public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";

  private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

  static {
    List<ProviderConfigProperty> properties = new ArrayList<>();

    ProviderConfigProperty minAgeProperty = new ProviderConfigProperty();
    minAgeProperty.setName(CONF_MIN_AGE);
    minAgeProperty.setLabel("Minimum Age");
    minAgeProperty.setHelpText("The minimum required age in full years (e.g. 18 for adulthood).");
    minAgeProperty.setType(ProviderConfigProperty.STRING_TYPE);
    minAgeProperty.setDefaultValue(String.valueOf(DEFAULT_MIN_AGE));
    properties.add(minAgeProperty);

    ProviderConfigProperty dateFormatProperty = new ProviderConfigProperty();
    dateFormatProperty.setName(CONF_DATE_FORMAT);
    dateFormatProperty.setLabel("Date Format");
    dateFormatProperty.setHelpText(
        "Java DateTimeFormatter pattern for the birthdate field (e.g. yyyy-MM-dd or dd/MM/yyyy).");
    dateFormatProperty.setType(ProviderConfigProperty.STRING_TYPE);
    dateFormatProperty.setDefaultValue(DEFAULT_DATE_FORMAT);
    properties.add(dateFormatProperty);

    CONFIG_PROPERTIES = Collections.unmodifiableList(properties);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public Validator create(KeycloakSession session) {
    return new MinimumAgeValidatorProvider();
  }

  @Override
  public void init(Config.Scope config) {
    // No global configuration required; options are configured per attribute
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {
    // No post-initialization required
  }

  @Override
  public void close() {
    // No resources to clean up
  }

  @Override
  public ValidationResult validateConfig(KeycloakSession session, ValidatorConfig config) {
    if (config == null) {
      return new ValidationResult(Collections.emptySet());
    }

    Set<ValidationError> errors = new HashSet<>();

    if (config.containsKey(CONF_MIN_AGE)) {
      Integer minAge = config.getInt(CONF_MIN_AGE);
      if (minAge == null || minAge <= 0) {
        errors.add(
            new ValidationError(
                ID, CONF_MIN_AGE, "error-user-profile-minimum-age-config-invalid-min-age"));
      }
    }

    if (config.containsKey(CONF_DATE_FORMAT)) {
      String format = config.getString(CONF_DATE_FORMAT);
      if (format != null && !format.trim().isEmpty()) {
        try {
          DateTimeFormatter.ofPattern(format.trim());
        } catch (IllegalArgumentException e) {
          errors.add(
              new ValidationError(
                  ID, CONF_DATE_FORMAT, "error-user-profile-minimum-age-config-invalid-format"));
        }
      }
    }

    return new ValidationResult(errors);
  }

  @Override
  public String getHelpText() {
    return "Validates that a date of birth attribute meets a minimum age threshold.";
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return CONFIG_PROPERTIES;
  }
}
