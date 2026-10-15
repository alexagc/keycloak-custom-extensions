package io.github.alexc.keycloak.validators.nationalid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alexc.keycloak.validators.nationalid.strategies.FranceDocumentStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FranceDocumentStrategyTest {

  private FranceDocumentStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new FranceDocumentStrategy();
  }

  @Test
  @DisplayName("Country code must be 'FR'")
  void testCountryCode() {
    assertEquals("FR", strategy.getCountryCode());
  }

  @Test
  @DisplayName("Valid standard French NIR (male and female) must pass validation")
  void testValidStandardNir() {
    // Male NIR
    DocumentValidationResult res1 = strategy.validate("185017500100150", true);
    assertTrue(res1.valid());
    assertEquals("NIR", res1.documentType());

    // Female NIR
    DocumentValidationResult res2 = strategy.validate("285017500100197", true);
    assertTrue(res2.valid());
    assertEquals("NIR", res2.documentType());
  }

  @Test
  @DisplayName("Valid Corsican NIRs (2A and 2B départements) must pass validation")
  void testValidCorsicaNir() {
    // 2A Corse-du-Sud
    DocumentValidationResult res2A = strategy.validate("185012A00100110", true);
    assertTrue(res2A.valid());
    assertEquals("NIR", res2A.documentType());

    // 2B Haute-Corse
    DocumentValidationResult res2B = strategy.validate("185012B00100167", true);
    assertTrue(res2B.valid());
    assertEquals("NIR", res2B.documentType());
  }

  @Test
  @DisplayName("Invalid control key must fail with checksum error")
  void testInvalidKeyChecksum() {
    DocumentValidationResult res = strategy.validate("185017500100199", true);
    assertFalse(res.valid());
    assertEquals("error-user-profile-national-id-checksum", res.errorMessageKey());
  }

  @Test
  @DisplayName("Invalid format (length != 15, invalid sex digit != 1 or 2) must fail")
  void testInvalidFormat() {
    assertFalse(strategy.validate(null, true).valid());
    assertFalse(strategy.validate("", true).valid());
    assertFalse(strategy.validate("185017500100", true).valid()); // 12 digits
    assertFalse(strategy.validate("1850175001001500", true).valid()); // 16 digits
    assertFalse(strategy.validate("385017500100150", true).valid()); // starts with 3
  }
}
