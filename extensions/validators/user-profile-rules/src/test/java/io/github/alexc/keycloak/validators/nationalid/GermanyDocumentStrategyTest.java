package io.github.alexc.keycloak.validators.nationalid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alexc.keycloak.validators.nationalid.strategies.GermanyDocumentStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GermanyDocumentStrategyTest {

  private GermanyDocumentStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new GermanyDocumentStrategy();
  }

  @Test
  @DisplayName("Country code must be 'DE'")
  void testCountryCode() {
    assertEquals("DE", strategy.getCountryCode());
  }

  @Test
  @DisplayName("Valid German Steuer-ID numbers (ISO 7064 MOD 11, 10) must pass validation")
  void testValidSteuerId() {
    DocumentValidationResult res1 = strategy.validate("36574261809", true);
    assertTrue(res1.valid());
    assertEquals("Steuer-ID", res1.documentType());

    DocumentValidationResult res2 = strategy.validate("26954301825", true);
    assertTrue(res2.valid());
    assertEquals("Steuer-ID", res2.documentType());
  }

  @Test
  @DisplayName("Steuer-ID starting with 0 must fail with invalid format error")
  void testLeadingZeroProhibited() {
    DocumentValidationResult res = strategy.validate("06574261809", true);
    assertFalse(res.valid());
    assertEquals("error-user-profile-national-id-invalid-format", res.errorMessageKey());
  }

  @Test
  @DisplayName("Steuer-ID with incorrect checksum digit must fail")
  void testIncorrectChecksumDigit() {
    DocumentValidationResult res = strategy.validate("36574261808", true);
    assertFalse(res.valid());
    assertEquals("error-user-profile-national-id-checksum", res.errorMessageKey());
  }

  @Test
  @DisplayName("Steuer-ID without internal digit repetition among first 10 digits must fail")
  void testNoRepeatedDigitFails() {
    // 1234567890 has 10 all-distinct digits
    DocumentValidationResult res = strategy.validate("12345678901", true);
    assertFalse(res.valid());
    assertEquals("error-user-profile-national-id-checksum", res.errorMessageKey());
  }

  @Test
  @DisplayName("Steuer-ID with 4 occurrences of the same digit must fail")
  void testFourOccurrencesFails() {
    // Digit '1' occurs 4 times
    DocumentValidationResult res = strategy.validate("11112345678", true);
    assertFalse(res.valid());
    assertEquals("error-user-profile-national-id-checksum", res.errorMessageKey());
  }

  @Test
  @DisplayName("Invalid format (length != 11, letters, null) must fail")
  void testInvalidFormat() {
    assertFalse(strategy.validate(null, true).valid());
    assertFalse(strategy.validate("", true).valid());
    assertFalse(strategy.validate("3657426180", true).valid()); // 10 digits
    assertFalse(strategy.validate("365742618099", true).valid()); // 12 digits
    assertFalse(strategy.validate("3657426180A", true).valid()); // letters
  }
}
