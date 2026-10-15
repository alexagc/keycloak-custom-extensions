package io.github.alexc.keycloak.validators.age;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidationResult;
import org.keycloak.validate.ValidatorConfig;

class MinimumAgeValidatorTest {

  private MinimumAgeValidatorProvider provider;
  private MinimumAgeValidatorProviderFactory factory;

  @BeforeEach
  void setUp() {
    factory = new MinimumAgeValidatorProviderFactory();
    provider = (MinimumAgeValidatorProvider) factory.create(null);
  }

  @Test
  @DisplayName("Provider ID must be 'minimum-age'")
  void testProviderId() {
    assertEquals(MinimumAgeValidatorProviderFactory.ID, provider.getId());
    assertEquals(MinimumAgeValidatorProviderFactory.ID, factory.getId());
  }

  @Test
  @DisplayName("User who turned 18 today must be valid (boundary test)")
  void testTurned18TodayIsValid() {
    LocalDate dob = LocalDate.now().minusYears(18);
    String dobStr = dob.format(DateTimeFormatter.ISO_LOCAL_DATE);

    ValidationContext context = new ValidationContext();
    provider.validate(dobStr, "birthdate", context, ValidatorConfig.EMPTY_CONFIG);

    assertTrue(context.isValid(), "Turning 18 today must satisfy minimum age 18");
  }

  @Test
  @DisplayName("User who is 18 years and 1 day old must be valid")
  void testOlderThan18IsValid() {
    LocalDate dob = LocalDate.now().minusYears(18).minusDays(1);
    String dobStr = dob.format(DateTimeFormatter.ISO_LOCAL_DATE);

    ValidationContext context = new ValidationContext();
    provider.validate(dobStr, "birthdate", context, ValidatorConfig.EMPTY_CONFIG);

    assertTrue(context.isValid());
  }

  @Test
  @DisplayName("User who turns 18 tomorrow must be rejected (underage)")
  void testTurns18TomorrowIsRejected() {
    LocalDate dob = LocalDate.now().minusYears(18).plusDays(1);
    String dobStr = dob.format(DateTimeFormatter.ISO_LOCAL_DATE);

    ValidationContext context = new ValidationContext();
    provider.validate(dobStr, "birthdate", context, ValidatorConfig.EMPTY_CONFIG);

    assertFalse(context.isValid(), "Turning 18 tomorrow must be rejected");
    assertEquals(1, context.getErrors().size());
    assertEquals(MinimumAgeValidatorProvider.ERROR_MINIMUM_AGE, context.getErrors().iterator().next().getMessage());
  }

  @Test
  @DisplayName("Future date of birth must be rejected")
  void testFutureBirthdateIsRejected() {
    LocalDate dob = LocalDate.now().plusDays(1);
    String dobStr = dob.format(DateTimeFormatter.ISO_LOCAL_DATE);

    ValidationContext context = new ValidationContext();
    provider.validate(dobStr, "birthdate", context, ValidatorConfig.EMPTY_CONFIG);

    assertFalse(context.isValid());
    assertEquals(MinimumAgeValidatorProvider.ERROR_MINIMUM_AGE, context.getErrors().iterator().next().getMessage());
  }

  @Test
  @DisplayName("Custom min-age (e.g. 21) must be respected")
  void testCustomMinAge() {
    LocalDate dob20 = LocalDate.now().minusYears(20);
    String dobStr = dob20.format(DateTimeFormatter.ISO_LOCAL_DATE);

    Map<String, Object> configMap = new HashMap<>();
    configMap.put(MinimumAgeValidatorProviderFactory.CONF_MIN_AGE, 21);
    ValidatorConfig config = new ValidatorConfig(configMap);

    ValidationContext context = new ValidationContext();
    provider.validate(dobStr, "birthdate", context, config);

    assertFalse(context.isValid(), "20-year-old must fail when minimum age is 21");

    LocalDate dob21 = LocalDate.now().minusYears(21);
    ValidationContext context2 = new ValidationContext();
    provider.validate(dob21.format(DateTimeFormatter.ISO_LOCAL_DATE), "birthdate", context2, config);
    assertTrue(context2.isValid(), "21-year-old must pass when minimum age is 21");
  }

  @Test
  @DisplayName("Custom date format (e.g. dd/MM/yyyy) must parse and validate correctly")
  void testCustomDateFormat() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(MinimumAgeValidatorProviderFactory.CONF_DATE_FORMAT, "dd/MM/yyyy");
    configMap.put(MinimumAgeValidatorProviderFactory.CONF_MIN_AGE, 18);
    ValidatorConfig config = new ValidatorConfig(configMap);

    LocalDate dob = LocalDate.now().minusYears(25);
    String dobFormatted = dob.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

    ValidationContext context = new ValidationContext();
    provider.validate(dobFormatted, "birthdate", context, config);

    assertTrue(context.isValid());
  }

  @Test
  @DisplayName("Invalid date format or unparseable text must emit invalid date error")
  void testUnparseableDateEmitsError() {
    ValidationContext context = new ValidationContext();
    provider.validate("not-a-date", "birthdate", context, ValidatorConfig.EMPTY_CONFIG);

    assertFalse(context.isValid());
    assertEquals(MinimumAgeValidatorProvider.ERROR_INVALID_DATE, context.getErrors().iterator().next().getMessage());
  }

  @Test
  @DisplayName("Null, empty or blank strings must be ignored (handled by required validator)")
  void testNullOrEmptyIgnored() {
    ValidationContext ctxNull = new ValidationContext();
    provider.validate(null, "birthdate", ctxNull, ValidatorConfig.EMPTY_CONFIG);
    assertTrue(ctxNull.isValid());

    ValidationContext ctxEmpty = new ValidationContext();
    provider.validate("", "birthdate", ctxEmpty, ValidatorConfig.EMPTY_CONFIG);
    assertTrue(ctxEmpty.isValid());

    ValidationContext ctxBlank = new ValidationContext();
    provider.validate("   ", "birthdate", ctxBlank, ValidatorConfig.EMPTY_CONFIG);
    assertTrue(ctxBlank.isValid());
  }

  @Test
  @DisplayName("Factory config validation validates min-age and date-format parameters")
  void testFactoryValidateConfig() {
    // Valid config
    Map<String, Object> validMap = new HashMap<>();
    validMap.put(MinimumAgeValidatorProviderFactory.CONF_MIN_AGE, 18);
    validMap.put(MinimumAgeValidatorProviderFactory.CONF_DATE_FORMAT, "yyyy-MM-dd");
    ValidationResult resValid = factory.validateConfig(null, new ValidatorConfig(validMap));
    assertTrue(resValid.isValid());

    // Invalid min-age (<= 0)
    Map<String, Object> invalidAgeMap = new HashMap<>();
    invalidAgeMap.put(MinimumAgeValidatorProviderFactory.CONF_MIN_AGE, 0);
    ValidationResult resInvalidAge = factory.validateConfig(null, new ValidatorConfig(invalidAgeMap));
    assertFalse(resInvalidAge.isValid());

    // Invalid date pattern
    Map<String, Object> invalidFormatMap = new HashMap<>();
    invalidFormatMap.put(MinimumAgeValidatorProviderFactory.CONF_DATE_FORMAT, "invalid-pattern-$$$");
    ValidationResult resInvalidFormat = factory.validateConfig(null, new ValidatorConfig(invalidFormatMap));
    assertFalse(resInvalidFormat.isValid());

    // Null config is valid
    assertTrue(factory.validateConfig(null, null).isValid());
  }

  @Test
  @DisplayName("Factory metadata and config properties check")
  void testFactoryMetadata() {
    assertNotNull(factory.getHelpText());
    assertNotNull(factory.getConfigProperties());
    assertEquals(2, factory.getConfigProperties().size());

    // Lifecycle methods should execute cleanly
    factory.init(null);
    factory.postInit(null);
    factory.close();
  }
}
