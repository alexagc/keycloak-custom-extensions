package io.github.alexc.keycloak.validators.age;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.keycloak.validate.AbstractStringValidator;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidatorConfig;

/**
 * Keycloak Declarative User Profile validator enforcing minimum age requirements (e.g. for GDPR,
 * COPPA, or adult content compliance).
 *
 * <p>Parses an ISO or custom formatted date of birth string and validates that the user is at least
 * {@code min-age} years old as of the current system date.
 */
public class MinimumAgeValidatorProvider extends AbstractStringValidator {

  public static final String ERROR_MINIMUM_AGE = "error-user-profile-minimum-age";
  public static final String ERROR_INVALID_DATE = "error-user-profile-minimum-age-invalid-date";

  @Override
  public String getId() {
    return MinimumAgeValidatorProviderFactory.ID;
  }

  @Override
  protected void doValidate(
      String value, String inputHint, ValidationContext context, ValidatorConfig config) {
    if (value == null || value.trim().isEmpty()) {
      return;
    }

    int minAge = MinimumAgeValidatorProviderFactory.DEFAULT_MIN_AGE;
    String dateFormat = MinimumAgeValidatorProviderFactory.DEFAULT_DATE_FORMAT;

    if (config != null) {
      Integer configMinAge = config.getInt(MinimumAgeValidatorProviderFactory.CONF_MIN_AGE);
      if (configMinAge != null && configMinAge > 0) {
        minAge = configMinAge;
      }
      String configDateFormat =
          config.getString(MinimumAgeValidatorProviderFactory.CONF_DATE_FORMAT);
      if (configDateFormat != null && !configDateFormat.trim().isEmpty()) {
        dateFormat = configDateFormat.trim();
      }
    }

    DateTimeFormatter formatter;
    try {
      formatter = DateTimeFormatter.ofPattern(dateFormat);
    } catch (IllegalArgumentException e) {
      formatter =
          DateTimeFormatter.ofPattern(MinimumAgeValidatorProviderFactory.DEFAULT_DATE_FORMAT);
    }

    LocalDate dob;
    try {
      dob = LocalDate.parse(value.trim(), formatter);
    } catch (DateTimeParseException e) {
      context.addError(new ValidationError(getId(), inputHint, ERROR_INVALID_DATE, dateFormat));
      return;
    }

    LocalDate now = LocalDate.now();
    if (dob.isAfter(now)) {
      context.addError(new ValidationError(getId(), inputHint, ERROR_MINIMUM_AGE, minAge));
      return;
    }

    int calculatedAge = Period.between(dob, now).getYears();
    if (calculatedAge < minAge) {
      context.addError(new ValidationError(getId(), inputHint, ERROR_MINIMUM_AGE, minAge));
    }
  }
}
