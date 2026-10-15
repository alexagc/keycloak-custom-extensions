package io.github.alexc.keycloak.validators.nationalid.strategies;

import io.github.alexc.keycloak.validators.nationalid.CountryDocumentStrategy;
import io.github.alexc.keycloak.validators.nationalid.DocumentValidationResult;
import java.util.regex.Pattern;

/**
 * Validates German Tax Identification Number (Steuerliche Identifikationsnummer / Steuer-ID / IdNr).
 *
 * <p>Enforces the 11-digit format, leading zero prohibition, internal digit frequency rules
 * (exactly one digit repeated either twice or three times among the first 10 digits), and the
 * official ISO/IEC 7064 MOD 11, 10 checksum algorithm.
 */
public class GermanyDocumentStrategy implements CountryDocumentStrategy {

  public static final String COUNTRY_CODE = "DE";

  private static final Pattern STEUER_ID_PATTERN = Pattern.compile("^[0-9]{11}$");

  @Override
  public String getCountryCode() {
    return COUNTRY_CODE;
  }

  @Override
  public DocumentValidationResult validate(String documentNumber, boolean allowForeigners) {
    if (documentNumber == null || !STEUER_ID_PATTERN.matcher(documentNumber).matches()) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
    }

    // Leading zero is strictly prohibited
    if (documentNumber.charAt(0) == '0') {
      return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
    }

    // Frequency rule: In the first 10 digits, exactly one digit appears 2 or 3 times
    int[] counts = new int[10];
    for (int i = 0; i < 10; i++) {
      counts[documentNumber.charAt(i) - '0']++;
    }

    int multipleOccurrencesCount = 0;
    int maxCount = 0;
    for (int count : counts) {
      if (count > 1) {
        multipleOccurrencesCount++;
      }
      if (count > maxCount) {
        maxCount = count;
      }
    }

    // Must have exactly one digit occurring more than once, and no digit occurring 4+ times
    if (multipleOccurrencesCount != 1 || maxCount > 3) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
    }

    // ISO 7064 MOD 11, 10 Checksum Calculation over the first 10 digits
    int product = 10;
    for (int i = 0; i < 10; i++) {
      int digit = documentNumber.charAt(i) - '0';
      int sum = (digit + product) % 10;
      if (sum == 0) {
        sum = 10;
      }
      product = (sum * 2) % 11;
    }

    int check = 11 - product;
    int expectedCheckDigit;
    if (check == 10) {
      expectedCheckDigit = 0;
    } else if (check == 11) {
      expectedCheckDigit = 0;
    } else {
      expectedCheckDigit = check;
    }

    int actualCheckDigit = documentNumber.charAt(10) - '0';
    if (actualCheckDigit == expectedCheckDigit) {
      return DocumentValidationResult.valid("Steuer-ID");
    }

    return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
  }
}
