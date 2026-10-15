package io.github.alexc.keycloak.validators.nationalid.strategies;

import io.github.alexc.keycloak.validators.nationalid.CountryDocumentStrategy;
import io.github.alexc.keycloak.validators.nationalid.DocumentValidationResult;
import java.util.regex.Pattern;

/**
 * Validates French NIR (Numéro d'Inscription au Répertoire de l'INSEE / Numéro de Sécurité Sociale).
 *
 * <p>Validates 15 characters: 13-character base identity code plus a 2-digit modulo 97 control key
 * ({@code Key = 97 - (number % 97)}), including special handling for Corsican départements (2A and
 * 2B).
 */
public class FranceDocumentStrategy implements CountryDocumentStrategy {

  public static final String COUNTRY_CODE = "FR";

  private static final Pattern STANDARD_NIR_PATTERN = Pattern.compile("^[12][0-9]{14}$");
  private static final Pattern CORSICA_NIR_PATTERN =
      Pattern.compile("^[12][0-9]{4}(2A|2B)[0-9]{6}[0-9]{2}$");

  @Override
  public String getCountryCode() {
    return COUNTRY_CODE;
  }

  @Override
  public DocumentValidationResult validate(String documentNumber, boolean allowForeigners) {
    if (documentNumber == null) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
    }

    boolean isStandard = STANDARD_NIR_PATTERN.matcher(documentNumber).matches();
    boolean isCorsica = CORSICA_NIR_PATTERN.matcher(documentNumber).matches();

    if (!isStandard && !isCorsica) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
    }

    String baseStr = documentNumber.substring(0, 13);
    String keyStr = documentNumber.substring(13, 15);

    long baseNumber;
    int actualKey;
    try {
      if (isCorsica) {
        if (baseStr.contains("2A")) {
          baseStr = baseStr.replace("2A", "20");
          baseNumber = Long.parseLong(baseStr) - 1_000_000L;
        } else {
          baseStr = baseStr.replace("2B", "20");
          baseNumber = Long.parseLong(baseStr) - 2_000_000L;
        }
      } else {
        baseNumber = Long.parseLong(baseStr);
      }
      actualKey = Integer.parseInt(keyStr);
    } catch (NumberFormatException e) {
      return DocumentValidationResult.invalid("error-user-profile-national-id-invalid-format");
    }

    long expectedKey = 97 - (baseNumber % 97);

    if (actualKey == expectedKey) {
      return DocumentValidationResult.valid("NIR");
    }
    return DocumentValidationResult.invalid("error-user-profile-national-id-checksum");
  }
}
