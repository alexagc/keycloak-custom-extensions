package io.github.alexc.keycloak.validators.nationalid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alexc.keycloak.validators.nationalid.strategies.ItalyDocumentStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItalyDocumentStrategyTest {

  private ItalyDocumentStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new ItalyDocumentStrategy();
  }

  @Test
  @DisplayName("Country code must be 'IT'")
  void testCountryCode() {
    assertEquals("IT", strategy.getCountryCode());
  }

  @Test
  @DisplayName("Valid Italian Codice Fiscale must pass validation")
  void testValidCodiceFiscale() {
    DocumentValidationResult res1 = strategy.validate("RSSMRA85A01H501F", true);
    assertTrue(res1.valid());
    assertEquals("Codice Fiscale", res1.documentType());

    DocumentValidationResult res2 = strategy.validate("AAAAAAAAAAAAAAAI", true);
    assertTrue(res2.valid());
    assertEquals("Codice Fiscale", res2.documentType());
  }

  @Test
  @DisplayName("Invalid control character must fail with checksum error")
  void testInvalidControlCharChecksum() {
    DocumentValidationResult res = strategy.validate("RSSMRA85A01H501Z", true);
    assertFalse(res.valid());
    assertEquals("error-user-profile-national-id-checksum", res.errorMessageKey());
  }

  @Test
  @DisplayName("Invalid length or characters must fail with invalid format error")
  void testInvalidFormat() {
    assertFalse(strategy.validate(null, true).valid());
    assertFalse(strategy.validate("", true).valid());
    assertFalse(strategy.validate("RSSMRA85A01H50", true).valid()); // 14 chars
    assertFalse(strategy.validate("RSSMRA85A01H501F0", true).valid()); // 17 chars
    assertFalse(strategy.validate("RSSMRA85A01H501?", true).valid()); // special char
  }
}
