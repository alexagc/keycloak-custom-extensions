package io.github.alexc.keycloak.validators.nationalid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alexc.keycloak.validators.nationalid.strategies.SpainDocumentStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpainDocumentStrategyTest {

  private SpainDocumentStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new SpainDocumentStrategy();
  }

  @Test
  @DisplayName("Country code must be 'ES'")
  void testCountryCode() {
    assertEquals("ES", strategy.getCountryCode());
  }

  @Test
  @DisplayName("Valid DNI documents must pass validation")
  void testValidDni() {
    DocumentValidationResult res1 = strategy.validate("12345678Z", true);
    assertTrue(res1.valid());
    assertEquals("DNI", res1.documentType());

    DocumentValidationResult res2 = strategy.validate("00000000T", true);
    assertTrue(res2.valid());
    assertEquals("DNI", res2.documentType());

    DocumentValidationResult res3 = strategy.validate("00000001R", true);
    assertTrue(res3.valid());
    assertEquals("DNI", res3.documentType());
  }

  @Test
  @DisplayName("Invalid DNI checksum must fail with checksum error")
  void testInvalidDniChecksum() {
    DocumentValidationResult res = strategy.validate("12345678A", true);
    assertFalse(res.valid());
    assertEquals("error-user-profile-national-id-checksum", res.errorMessageKey());
  }

  @Test
  @DisplayName("Valid NIE documents (X, Y, Z prefixes) must pass validation")
  void testValidNie() {
    // X -> 0: 01234567 % 23 = 19 ('H')
    DocumentValidationResult resX = strategy.validate("X1234567H", true);
    assertTrue(resX.valid());
    assertEquals("NIE", resX.documentType());

    // Y -> 1: 10000000 % 23 = 14 ('Z')
    DocumentValidationResult resY = strategy.validate("Y0000000Z", true);
    assertTrue(resY.valid());
    assertEquals("NIE", resY.documentType());

    // Z -> 2: 20000000 % 23 = 5 ('M')
    DocumentValidationResult resZ = strategy.validate("Z0000000M", true);
    assertTrue(resZ.valid());
    assertEquals("NIE", resZ.documentType());
  }

  @Test
  @DisplayName("Invalid NIE checksum must fail with checksum error")
  void testInvalidNieChecksum() {
    DocumentValidationResult res = strategy.validate("X1234567A", true);
    assertFalse(res.valid());
    assertEquals("error-user-profile-national-id-checksum", res.errorMessageKey());
  }

  @Test
  @DisplayName("NIE must be rejected when allowForeigners is false")
  void testNieDisallowedWhenAllowForeignersFalse() {
    DocumentValidationResult res = strategy.validate("X1234567H", false);
    assertFalse(res.valid());
    assertEquals("error-user-profile-national-id-foreigner-not-allowed", res.errorMessageKey());
  }

  @Test
  @DisplayName("Valid CIF documents with digit and letter control characters must pass")
  void testValidCif() {
    // B requires control digit (sum: 26 -> D = 4)
    DocumentValidationResult resB = strategy.validate("B12345674", true);
    assertTrue(resB.valid());
    assertEquals("CIF", resB.documentType());

    // P requires control letter (D = 4 -> 'D')
    DocumentValidationResult resP = strategy.validate("P1234567D", true);
    assertTrue(resP.valid());
    assertEquals("CIF", resP.documentType());
  }

  @Test
  @DisplayName("Invalid CIF checksum must fail")
  void testInvalidCifChecksum() {
    DocumentValidationResult resB = strategy.validate("B12345679", true);
    assertFalse(resB.valid());
    assertEquals("error-user-profile-national-id-checksum", resB.errorMessageKey());

    DocumentValidationResult resP = strategy.validate("P1234567A", true);
    assertFalse(resP.valid());
    assertEquals("error-user-profile-national-id-checksum", resP.errorMessageKey());
  }

  @Test
  @DisplayName("Malformed inputs must fail with invalid format error")
  void testInvalidFormat() {
    assertFalse(strategy.validate(null, true).valid());
    assertFalse(strategy.validate("", true).valid());
    assertFalse(strategy.validate("123", true).valid());
    assertFalse(strategy.validate("1234567890", true).valid());
    assertFalse(strategy.validate("ABCDEFGHI", true).valid());
  }
}
