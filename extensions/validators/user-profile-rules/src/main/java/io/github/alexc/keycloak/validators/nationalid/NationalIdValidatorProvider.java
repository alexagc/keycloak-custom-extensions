package io.github.alexc.keycloak.validators.nationalid;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import org.keycloak.models.UserModel;
import org.keycloak.validate.AbstractStringValidator;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidatorConfig;

/**
 * Keycloak Declarative User Profile validator for global national identity documents using the
 * Strategy pattern.
 *
 * <p>Supports fixed country configurations (e.g. {@code ES}, {@code IT}, {@code FR}, {@code BR},
 * {@code US}, {@code DE}) or dynamic resolution from companion user profile attributes via {@code
 * attribute:<fieldName>} (e.g. {@code attribute:country}).
 */
public class NationalIdValidatorProvider extends AbstractStringValidator {

  public static final String ERROR_MISSING_COUNTRY = "error-user-profile-national-id-missing-country";
  public static final String ERROR_UNSUPPORTED_COUNTRY =
      "error-user-profile-national-id-unsupported-country";

  private final CountryStrategyRegistry strategyRegistry;

  /**
   * Constructs the validator instance with the country strategy registry.
   *
   * @param strategyRegistry registry containing supported country strategies
   */
  public NationalIdValidatorProvider(CountryStrategyRegistry strategyRegistry) {
    this.strategyRegistry =
        strategyRegistry != null ? strategyRegistry : new CountryStrategyRegistry();
  }

  @Override
  public String getId() {
    return NationalIdValidatorProviderFactory.ID;
  }

  @Override
  protected void doValidate(
      String value, String inputHint, ValidationContext context, ValidatorConfig config) {
    if (value == null || value.trim().isEmpty()) {
      return;
    }

    String countryConfig = null;
    boolean allowForeigners = NationalIdValidatorProviderFactory.DEFAULT_ALLOW_FOREIGNERS;
    boolean normalize = NationalIdValidatorProviderFactory.DEFAULT_NORMALIZE;

    if (config != null) {
      countryConfig = config.getString(NationalIdValidatorProviderFactory.CONF_COUNTRY);
      allowForeigners =
          config.getBooleanOrDefault(
              NationalIdValidatorProviderFactory.CONF_ALLOW_FOREIGNERS,
              NationalIdValidatorProviderFactory.DEFAULT_ALLOW_FOREIGNERS);
      normalize =
          config.getBooleanOrDefault(
              NationalIdValidatorProviderFactory.CONF_NORMALIZE,
              NationalIdValidatorProviderFactory.DEFAULT_NORMALIZE);
    }

    if (countryConfig == null || countryConfig.trim().isEmpty()) {
      context.addError(new ValidationError(getId(), inputHint, ERROR_MISSING_COUNTRY, "country"));
      return;
    }

    String resolvedCountry = resolveCountryCode(countryConfig.trim(), context);
    if (resolvedCountry == null || resolvedCountry.isEmpty()) {
      context.addError(
          new ValidationError(getId(), inputHint, ERROR_MISSING_COUNTRY, countryConfig));
      return;
    }

    CountryDocumentStrategy strategy = strategyRegistry.getStrategy(resolvedCountry);
    if (strategy == null) {
      context.addError(
          new ValidationError(getId(), inputHint, ERROR_UNSUPPORTED_COUNTRY, resolvedCountry));
      return;
    }

    String docToValidate =
        normalize ? value.replaceAll("[\\s\\-\\./]+", "").toUpperCase(Locale.ROOT) : value.trim();

    DocumentValidationResult result = strategy.validate(docToValidate, allowForeigners);
    if (!result.valid()) {
      context.addError(
          new ValidationError(
              getId(),
              inputHint,
              result.errorMessageKey(),
              resolvedCountry,
              result.documentType()));
    }
  }

  private String resolveCountryCode(String countryConfig, ValidationContext context) {
    if (!countryConfig.startsWith("attribute:")) {
      return countryConfig.toUpperCase(Locale.ROOT);
    }

    String attributeName = countryConfig.substring("attribute:".length()).trim();
    if (attributeName.isEmpty() || context == null) {
      return null;
    }

    Map<String, Object> attributes = context.getAttributes();
    if (attributes != null) {
      // 1. Direct attribute mapping
      Object directVal = attributes.get(attributeName);
      String resolved = extractFirstString(directVal);
      if (resolved != null && !resolved.isEmpty()) {
        return resolved.toUpperCase(Locale.ROOT);
      }

      // 2. Nested attributes map (common in User Profile context)
      Object nestedAttrs = attributes.get("attributes");
      if (nestedAttrs instanceof Map<?, ?> nestedMap) {
        Object nestedVal = nestedMap.get(attributeName);
        resolved = extractFirstString(nestedVal);
        if (resolved != null && !resolved.isEmpty()) {
          return resolved.toUpperCase(Locale.ROOT);
        }
      }

      // 3. Existing UserModel in context
      Object userObj = attributes.get(UserModel.class.getName());
      if (userObj instanceof UserModel userModel) {
        String userAttr = userModel.getFirstAttribute(attributeName);
        if (userAttr != null && !userAttr.trim().isEmpty()) {
          return userAttr.trim().toUpperCase(Locale.ROOT);
        }
      }
    }

    return null;
  }

  private String extractFirstString(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String s) {
      return s.trim();
    }
    if (value instanceof Collection<?> coll && !coll.isEmpty()) {
      Object first = coll.iterator().next();
      return first != null ? first.toString().trim() : null;
    }
    return value.toString().trim();
  }
}
