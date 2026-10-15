package io.github.alexc.keycloak.validators.nationalid.strategies;

import io.github.alexc.keycloak.validators.nationalid.CountryDocumentStrategy;
import io.github.alexc.keycloak.validators.nationalid.DocumentValidationResult;
import java.util.regex.Pattern;

/**
 * Validates Brazilian CPF (Cadastro de Pessoas Físicas).
 *
 * <p>Enforces the 11-digit structure, eliminates known repeated fake numbers (e.g. 00000000000), and
 * computes the strict two-digit modulo 11 verification algorithm with descending multipliers.
 */
public class BrazilDocumentStrategy implements CountryDocumentStrategy {

  public static final String COUNTRY_CODE = "BR";

  private static final Pattern CPF_PATTERN = Pattern.compile("^[0-9]{11}$");

  @Override
  public String getCountryCode() {
    return COUNTRY_CODE;
  }

  @Override
  public DocumentValidationResult validate(String documentNumber, boolean allowForeigners) {
    if (documentNumber == null || !CPF_PATTERN.matcher(documentNumber).matches()) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
    }

    // Reject sequences with all identical digits (00000000000, 11111111111, etc.)
    boolean allSame = true;
    for (int i = 1; i < 11; i++) {
      if (documentNumber.charAt(i) != documentNumber.charAt(0)) {
        allSame = false;
        break;
      }
    }
    if (allSame) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
    }

    // 1st Verifier Digit Calculation
    int sum1 = 0;
    for (int i = 0; i < 9; i++) {
      sum1 += (documentNumber.charAt(i) - '0') * (10 - i);
    }
    int rem1 = sum1 % 11;
    int v1 = rem1 < 2 ? 0 : 11 - rem1;

    if (documentNumber.charAt(9) - '0' != v1) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
    }

    // 2nd Verifier Digit Calculation
    int sum2 = 0;
    for (int i = 0; i < 9; i++) {
      sum2 += (documentNumber.charAt(i) - '0') * (11 - i);
    }
    sum2 += v1 * 2;
    int rem2 = sum2 % 11;
    int v2 = rem2 < 2 ? 0 : 11 - rem2;

    if (documentNumber.charAt(10) - '0' != v2) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
    }

    return DocumentValidationResult.valid("CPF");
  }
}
