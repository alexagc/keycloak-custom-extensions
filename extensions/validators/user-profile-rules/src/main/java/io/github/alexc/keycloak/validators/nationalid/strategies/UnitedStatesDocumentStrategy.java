package io.github.alexc.keycloak.validators.nationalid.strategies;

import io.github.alexc.keycloak.validators.nationalid.CountryDocumentStrategy;
import io.github.alexc.keycloak.validators.nationalid.DocumentValidationResult;
import java.util.regex.Pattern;

/**
 * Validates United States Social Security Numbers (SSN).
 *
 * <p>Validates 9 digits conforming to official Social Security Administration (SSA) issuance
 * ranges and exclusions:
 *
 * <ul>
 *   <li>Area number (first 3 digits) cannot be {@code 000}, {@code 666}, or within {@code 900-999}.
 *   <li>Group number (middle 2 digits) cannot be {@code 00}.
 *   <li>Serial number (last 4 digits) cannot be {@code 0000}.
 * </ul>
 */
public class UnitedStatesDocumentStrategy implements CountryDocumentStrategy {

  public static final String COUNTRY_CODE = "US";

  private static final Pattern SSN_PATTERN = Pattern.compile("^[0-9]{9}$");

  @Override
  public String getCountryCode() {
    return COUNTRY_CODE;
  }

  @Override
  public DocumentValidationResult validate(String documentNumber, boolean allowForeigners) {
    if (documentNumber == null || !SSN_PATTERN.matcher(documentNumber).matches()) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
    }

    String areaStr = documentNumber.substring(0, 3);
    String groupStr = documentNumber.substring(3, 5);
    String serialStr = documentNumber.substring(5, 9);

    int area = Integer.parseInt(areaStr);
    int group = Integer.parseInt(groupStr);
    int serial = Integer.parseInt(serialStr);

    if (area == 0 || area == 666 || area >= 900) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
    }

    if (group == 0) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
    }

    if (serial == 0) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
    }

    // Specific well-known fake advertising SSN (078-05-1120)
    if ("078051120".equals(documentNumber)) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
    }

    return DocumentValidationResult.valid("SSN");
  }
}
