package io.github.alexc.keycloak.validators.nationalid.strategies;

import io.github.alexc.keycloak.validators.nationalid.CountryDocumentStrategy;
import io.github.alexc.keycloak.validators.nationalid.DocumentValidationResult;
import java.util.regex.Pattern;

/**
 * Validates Italian Codice Fiscale (16 alphanumeric characters).
 *
 * <p>Enforces structure and official Italian Revenue Agency (Agenzia delle Entrate) control
 * character checksum algorithm across odd and even position conversion tables modulo 26.
 */
public class ItalyDocumentStrategy implements CountryDocumentStrategy {

  public static final String COUNTRY_CODE = "IT";

  private static final Pattern CODICE_FISCALE_PATTERN = Pattern.compile("^[A-Z0-9]{16}$");

  private static final int[] ODD_CONVERSION = {
    // '0'-'9' (ASCII 48-57)
    1, 0, 5, 7, 9, 13, 15, 17, 19, 21,
    // gap between '9' and 'A'
    0, 0, 0, 0, 0, 0, 0,
    // 'A'-'Z' (ASCII 65-90)
    1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23
  };

  @Override
  public String getCountryCode() {
    return COUNTRY_CODE;
  }

  @Override
  public DocumentValidationResult validate(String documentNumber, boolean allowForeigners) {
    if (documentNumber == null || !CODICE_FISCALE_PATTERN.matcher(documentNumber).matches()) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
    }

    int sum = 0;
    for (int i = 0; i < 15; i++) {
      char c = documentNumber.charAt(i);
      if (i % 2 == 0) {
        // Odd positions (1st, 3rd, 5th... in 1-based indexing)
        int index = c - '0';
        if (index < 0 || index >= ODD_CONVERSION.length) {
          return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
        }
        sum += ODD_CONVERSION[index];
      } else {
        // Even positions (2nd, 4th, 6th... in 1-based indexing)
        if (c >= '0' && c <= '9') {
          sum += (c - '0');
        } else if (c >= 'A' && c <= 'Z') {
          sum += (c - 'A');
        } else {
          return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
        }
      }
    }

    char expectedControlChar = (char) ('A' + (sum % 26));
    char actualControlChar = documentNumber.charAt(15);

    if (actualControlChar == expectedControlChar) {
      return DocumentValidationResult.valid("Codice Fiscale");
    }
    return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
  }
}
