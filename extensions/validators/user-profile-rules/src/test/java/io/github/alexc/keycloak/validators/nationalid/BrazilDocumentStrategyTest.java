package io.github.alexc.keycloak.validators.nationalid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alexc.keycloak.validators.nationalid.strategies.BrazilDocumentStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BrazilDocumentStrategyTest {

  private BrazilDocumentStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new BrazilDocumentStrategy();
  }

  @Test
  @DisplayName("Country code must be 'BR'")
  void testCountryCode() {
    assertEquals("BR", strategy.getCountryCode());
  }

  @Test
  @DisplayName("Valid Brazilian CPF numbers must pass validation")
  void testValidCpf() {
    DocumentValidationResult res1 = strategy.validate("11144477735", true);
    assertTrue(res1.valid());
    assertEquals("CPF", res1.documentType());

    DocumentValidationResult res2 = strategy.validate("52998224725", true);
    assertTrue(res2.valid());
    assertEquals("CPF", res2.documentType());
  }

  @Test
  @DisplayName("Repeated digit sequences must fail with checksum error")
  void testRepeatedSequencesFail() {
    assertFalse(strategy.validate("00000000000", true).valid());
    assertFalse(strategy.validate("11111111111", true).valid());
    assertFalse(strategy.validate("99999999999", true).valid());
  }

  @Test
  @DisplayName("Altered first or second verifier digit must fail with checksum error")
  void testAlteredVerifierDigits() {
    // 1st digit altered: expected 3, given 4
    DocumentValidationResult res1 = strategy.validate("11144477745", true);
    assertFalse(res1.valid());
    assertEquals("error-user-profile-national-id-checksum", res1.errorMessageKey());

    // 2nd digit altered: expected 5, given 6
    DocumentValidationResult res2 = strategy.validate("11144477736", true);
    assertFalse(res2.valid());
    assertEquals("error-user-profile-national-id-checksum", res2.errorMessageKey());
  }

  @Test
  @DisplayName("Invalid format (length != 11 or non-digits) must fail with invalid format error")
  void testInvalidFormat() {
    assertFalse(strategy.validate(null, true).valid());
    assertFalse(strategy.validate("", true).valid());
    assertFalse(strategy.validate("1114447773", true).valid()); // 10 digits
    assertFalse(strategy.validate("111444777350", true).valid()); // 12 digits
    assertFalse(strategy.validate("1114447773A", true).valid()); // letters
  }
}
