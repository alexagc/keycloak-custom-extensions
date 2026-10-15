package io.github.alexc.keycloak.validators.nationalid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alexc.keycloak.validators.nationalid.strategies.UnitedStatesDocumentStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnitedStatesDocumentStrategyTest {

  private UnitedStatesDocumentStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new UnitedStatesDocumentStrategy();
  }

  @Test
  @DisplayName("Country code must be 'US'")
  void testCountryCode() {
    assertEquals("US", strategy.getCountryCode());
  }

  @Test
  @DisplayName("Valid SSN numbers within permitted SSA ranges must pass validation")
  void testValidSsn() {
    DocumentValidationResult res1 = strategy.validate("123456789", true);
    assertTrue(res1.valid());
    assertEquals("SSN", res1.documentType());

    DocumentValidationResult res2 = strategy.validate("219099999", true);
    assertTrue(res2.valid());
    assertEquals("SSN", res2.documentType());
  }

  @Test
  @DisplayName("Prohibited SSA area codes (000, 666, 900-999) must fail")
  void testProhibitedAreaNumbers() {
    assertFalse(strategy.validate("000123456", true).valid());
    assertFalse(strategy.validate("666123456", true).valid());
    assertFalse(strategy.validate("900123456", true).valid());
    assertFalse(strategy.validate("999123456", true).valid());
  }

  @Test
  @DisplayName("Group number 00 must fail")
  void testProhibitedGroupNumber() {
    DocumentValidationResult res = strategy.validate("123004567", true);
    assertFalse(res.valid());
    assertEquals("error-user-profile-national-id-checksum", res.errorMessageKey());
  }

  @Test
  @DisplayName("Serial number 0000 must fail")
  void testProhibitedSerialNumber() {
    DocumentValidationResult res = strategy.validate("123450000", true);
    assertFalse(res.valid());
    assertEquals("error-user-profile-national-id-checksum", res.errorMessageKey());
  }

  @Test
  @DisplayName("Specific advertising dummy SSN (078051120) must fail")
  void testAdvertisingSsn() {
    assertFalse(strategy.validate("078051120", true).valid());
  }

  @Test
  @DisplayName("Invalid format (length != 9 or non-digits) must fail with invalid format error")
  void testInvalidFormat() {
    assertFalse(strategy.validate(null, true).valid());
    assertFalse(strategy.validate("", true).valid());
    assertFalse(strategy.validate("12345678", true).valid()); // 8 digits
    assertFalse(strategy.validate("1234567890", true).valid()); // 10 digits
    assertFalse(strategy.validate("12345678A", true).valid()); // letters
  }
}
