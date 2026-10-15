package io.github.alexc.keycloak.validators.nationalid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.models.UserModel;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidationResult;
import org.keycloak.validate.ValidatorConfig;

class NationalIdValidatorTest {

  private static final ValidatorConfig EMPTY_CONFIG = new ValidatorConfig(Collections.emptyMap());

  private NationalIdValidatorProvider provider;
  private NationalIdValidatorProviderFactory factory;

  @BeforeEach
  void setUp() {
    factory = new NationalIdValidatorProviderFactory();
    factory.init(null);
    provider = (NationalIdValidatorProvider) factory.create(null);
  }

  @Test
  @DisplayName("Provider ID must be 'national-id'")
  void testProviderId() {
    assertEquals(NationalIdValidatorProviderFactory.ID, provider.getId());
    assertEquals(NationalIdValidatorProviderFactory.ID, factory.getId());
  }

  @Test
  @DisplayName("Fixed country configuration (e.g. ES) validates Spanish DNI")
  void testFixedCountryValidation() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(NationalIdValidatorProviderFactory.CONF_COUNTRY, "ES");
    ValidatorConfig config = new ValidatorConfig(configMap);

    ValidationContext context = new ValidationContext();
    provider.validate("12345678Z", "dni", context, config);

    assertTrue(context.isValid(), "Valid Spanish DNI must pass");
  }

  @Test
  @DisplayName("Input normalization strips hyphens, spaces, dots and converts to uppercase")
  void testInputNormalization() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(NationalIdValidatorProviderFactory.CONF_COUNTRY, "ES");
    configMap.put(NationalIdValidatorProviderFactory.CONF_NORMALIZE, true);
    ValidatorConfig config = new ValidatorConfig(configMap);

    ValidationContext context = new ValidationContext();
    provider.validate("  12.345.678 - z  ", "dni", context, config);

    assertTrue(context.isValid(), "Formatted DNI must be normalized and pass");
  }

  @Test
  @DisplayName("Disabling normalization requires exact unformatted input")
  void testNormalizationDisabled() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(NationalIdValidatorProviderFactory.CONF_COUNTRY, "ES");
    configMap.put(NationalIdValidatorProviderFactory.CONF_NORMALIZE, false);
    ValidatorConfig config = new ValidatorConfig(configMap);

    ValidationContext context = new ValidationContext();
    provider.validate("12-345-678-Z", "dni", context, config);

    assertFalse(context.isValid(), "Unnormalized formatted DNI must fail");
  }

  @Test
  @DisplayName("Dynamic country resolution from direct context attribute (attribute:country)")
  void testDynamicCountryResolutionDirect() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(NationalIdValidatorProviderFactory.CONF_COUNTRY, "attribute:country");
    ValidatorConfig config = new ValidatorConfig(configMap);

    ValidationContext context = new ValidationContext();
    context.getAttributes().put("country", "ES");

    provider.validate("12345678Z", "national_id", context, config);
    assertTrue(context.isValid(), "Dynamic resolution for ES must validate Spanish DNI");
  }

  @Test
  @DisplayName("Dynamic country resolution from collection attribute (attribute:country)")
  void testDynamicCountryResolutionCollection() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(NationalIdValidatorProviderFactory.CONF_COUNTRY, "attribute:country");
    ValidatorConfig config = new ValidatorConfig(configMap);

    ValidationContext context = new ValidationContext();
    context.getAttributes().put("country", List.of("DE"));

    provider.validate("36574261809", "tax_id", context, config);
    assertTrue(context.isValid(), "Dynamic resolution for DE must validate German Steuer-ID");
  }

  @Test
  @DisplayName("Dynamic country resolution from nested attributes map")
  void testDynamicCountryResolutionNestedMap() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(NationalIdValidatorProviderFactory.CONF_COUNTRY, "attribute:nationality");
    ValidatorConfig config = new ValidatorConfig(configMap);

    ValidationContext context = new ValidationContext();
    Map<String, Object> nested = new HashMap<>();
    nested.put("nationality", "FR");
    context.getAttributes().put("attributes", nested);

    provider.validate("185017500100150", "ssn", context, config);
    assertTrue(context.isValid(), "Dynamic resolution for FR from nested map must validate French NIR");
  }

  @Test
  @DisplayName("Dynamic country resolution from UserModel in context")
  void testDynamicCountryResolutionUserModel() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(NationalIdValidatorProviderFactory.CONF_COUNTRY, "attribute:residence");
    ValidatorConfig config = new ValidatorConfig(configMap);

    UserModel mockUser = mock(UserModel.class);
    when(mockUser.getFirstAttribute("residence")).thenReturn("BR");

    ValidationContext context = new ValidationContext();
    context.getAttributes().put(UserModel.class.getName(), mockUser);

    provider.validate("11144477735", "cpf", context, config);
    assertTrue(context.isValid(), "Dynamic resolution for BR from UserModel must validate Brazilian CPF");
  }

  @Test
  @DisplayName("Missing dynamic country attribute emits missing country error")
  void testMissingDynamicCountryAttribute() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(NationalIdValidatorProviderFactory.CONF_COUNTRY, "attribute:missing_country");
    ValidatorConfig config = new ValidatorConfig(configMap);

    ValidationContext context = new ValidationContext();
    provider.validate("12345678Z", "national_id", context, config);

    assertFalse(context.isValid());
    assertEquals(
        NationalIdValidatorProvider.ERROR_MISSING_COUNTRY,
        context.getErrors().iterator().next().getMessage());
  }

  @Test
  @DisplayName("Missing country configuration parameter emits missing country error")
  void testMissingCountryConfig() {
    ValidationContext context = new ValidationContext();
    provider.validate("12345678Z", "national_id", context, EMPTY_CONFIG);

    assertFalse(context.isValid());
    assertEquals(
        NationalIdValidatorProvider.ERROR_MISSING_COUNTRY,
        context.getErrors().iterator().next().getMessage());
  }

  @Test
  @DisplayName("Unsupported country code emits unsupported country error")
  void testUnsupportedCountry() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(NationalIdValidatorProviderFactory.CONF_COUNTRY, "XX");
    ValidatorConfig config = new ValidatorConfig(configMap);

    ValidationContext context = new ValidationContext();
    provider.validate("12345678Z", "national_id", context, config);

    assertFalse(context.isValid());
    assertEquals(
        NationalIdValidatorProvider.ERROR_UNSUPPORTED_COUNTRY,
        context.getErrors().iterator().next().getMessage());
  }

  @Test
  @DisplayName("Foreigner residency document rejected when allow-foreigners is false")
  void testAllowForeignersFalse() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(NationalIdValidatorProviderFactory.CONF_COUNTRY, "ES");
    configMap.put(NationalIdValidatorProviderFactory.CONF_ALLOW_FOREIGNERS, false);
    ValidatorConfig config = new ValidatorConfig(configMap);

    ValidationContext context = new ValidationContext();
    provider.validate("X1234567H", "nie", context, config);

    assertFalse(context.isValid());
    assertEquals(
        "error-user-profile-national-id-foreigner-not-allowed",
        context.getErrors().iterator().next().getMessage());
  }

  @Test
  @DisplayName("Factory configuration validation for country property")
  void testFactoryValidateConfig() {
    // Valid fixed country
    ValidationResult resValid =
        factory.validateConfig(
            null,
            new ValidatorConfig(
                Collections.singletonMap(NationalIdValidatorProviderFactory.CONF_COUNTRY, "ES")));
    assertTrue(resValid.isValid());

    // Valid dynamic attribute
    ValidationResult resDyn =
        factory.validateConfig(
            null,
            new ValidatorConfig(
                Collections.singletonMap(
                    NationalIdValidatorProviderFactory.CONF_COUNTRY, "attribute:country")));
    assertTrue(resDyn.isValid());

    // Invalid unsupported country
    ValidationResult resUnsupported =
        factory.validateConfig(
            null,
            new ValidatorConfig(
                Collections.singletonMap(NationalIdValidatorProviderFactory.CONF_COUNTRY, "ZZ")));
    assertFalse(resUnsupported.isValid());

    // Invalid empty country
    ValidationResult resEmpty =
        factory.validateConfig(
            null,
            new ValidatorConfig(
                Collections.singletonMap(NationalIdValidatorProviderFactory.CONF_COUNTRY, "")));
    assertFalse(resEmpty.isValid());
  }

  @Test
  @DisplayName("Factory lifecycle, config properties, and registry verification")
  void testFactoryLifecycle() {
    assertNotNull(factory.getHelpText());
    assertNotNull(factory.getConfigProperties());
    assertEquals(3, factory.getConfigProperties().size());
    assertNotNull(factory.getStrategyRegistry());
    assertEquals(6, factory.getStrategyRegistry().getSupportedCountries().size());

    factory.close();
  }
}
