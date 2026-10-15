package io.github.alexc.keycloak.validators.nationalid.strategies;

import io.github.alexc.keycloak.validators.nationalid.CountryDocumentStrategy;
import io.github.alexc.keycloak.validators.nationalid.DocumentValidationResult;
import java.util.regex.Pattern;

/**
 * Validates Spanish identity and tax identification documents:
 *
 * <ul>
 *   <li><b>DNI:</b> 8 digits followed by a modulo 23 control letter mapped against {@code
 *       "TRWAGMYFPDXBNJZSQVHLCKE"}.
 *   <li><b>NIE:</b> Foreigner identity number starting with X, Y, or Z followed by 7 digits and a
 *       modulo 23 control letter.
 *   <li><b>CIF:</b> Corporate tax identification code validated with Luhn-like sum and
 *       entity-specific control digit or letter.
 * </ul>
 */
public class SpainDocumentStrategy implements CountryDocumentStrategy {

  public static final String COUNTRY_CODE = "ES";

  private static final String DNI_CONTROL_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
  private static final String CIF_CONTROL_LETTERS = "JABCDEFGHI";

  private static final Pattern DNI_PATTERN = Pattern.compile("^[0-9]{8}[A-Z]$");
  private static final Pattern NIE_PATTERN = Pattern.compile("^[XYZ][0-9]{7}[A-Z]$");
  private static final Pattern CIF_PATTERN =
      Pattern.compile("^[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]$");

  @Override
  public String getCountryCode() {
    return COUNTRY_CODE;
  }

  @Override
  public DocumentValidationResult validate(String documentNumber, boolean allowForeigners) {
    if (documentNumber == null || documentNumber.isEmpty()) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
    }

    if (DNI_PATTERN.matcher(documentNumber).matches()) {
      return validateDni(documentNumber);
    }

    if (NIE_PATTERN.matcher(documentNumber).matches()) {
      if (!allowForeigners) {
        return DocumentValidationResult.invalid(
            "error-user-profile-national-id-foreigner-not-allowed");
      }
      return validateNie(documentNumber);
    }

    if (CIF_PATTERN.matcher(documentNumber).matches()) {
      return validateCif(documentNumber);
    }

    return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
  }

  private DocumentValidationResult validateDni(String dni) {
    int number = Integer.parseInt(dni.substring(0, 8));
    char expectedLetter = DNI_CONTROL_LETTERS.charAt(number % 23);
    char actualLetter = dni.charAt(8);

    if (actualLetter == expectedLetter) {
      return DocumentValidationResult.valid("DNI");
    }
    return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
  }

  private DocumentValidationResult validateNie(String nie) {
    char prefix = nie.charAt(0);
    int prefixNum =
        switch (prefix) {
          case 'X' -> 0;
          case 'Y' -> 1;
          case 'Z' -> 2;
          default -> -1;
        };

    if (prefixNum == -1) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
    }

    int number = Integer.parseInt(prefixNum + nie.substring(1, 8));
    char expectedLetter = DNI_CONTROL_LETTERS.charAt(number % 23);
    char actualLetter = nie.charAt(8);

    if (actualLetter == expectedLetter) {
      return DocumentValidationResult.valid("NIE");
    }
    return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
  }

  private DocumentValidationResult validateCif(String cif) {
    char firstChar = cif.charAt(0);
    String digits = cif.substring(1, 8);
    char controlChar = cif.charAt(8);

    int sumA = 0; // Sum of odd position digits multiplied by 2 (sum of product digits)
    int sumB = 0; // Sum of even position digits

    for (int i = 0; i < 7; i++) {
      int digit = digits.charAt(i) - '0';
      if (i % 2 == 0) { // 1st, 3rd, 5th, 7th position (0-indexed 0, 2, 4, 6)
        int prod = digit * 2;
        sumA += (prod / 10) + (prod % 10);
      } else { // 2nd, 4th, 6th position (0-indexed 1, 3, 5)
        sumB += digit;
      }
    }

    int totalSum = sumA + sumB;
    int controlDigit = (10 - (totalSum % 10)) % 10;
    char controlLetter = CIF_CONTROL_LETTERS.charAt(controlDigit);

    boolean requiresLetter = "PQSKW".indexOf(firstChar) != -1;
    boolean requiresDigit = "ABEH".indexOf(firstChar) != -1;

    if (requiresLetter) {
      if (controlChar == controlLetter) {
        return DocumentValidationResult.valid("CIF");
      }
      return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
    }

    if (requiresDigit) {
      if (controlChar == (char) ('0' + controlDigit)) {
        return DocumentValidationResult.valid("CIF");
      }
      return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
    }

    // Other entity types allow either control digit or control letter
    if (controlChar == (char) ('0' + controlDigit) || controlChar == controlLetter) {
      return DocumentValidationResult.valid("CIF");
    }

    return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
  }
}
