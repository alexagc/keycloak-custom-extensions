package io.github.alexc.keycloak.validators.nationalid;

/**
 * Immutable validation result produced by a {@link CountryDocumentStrategy}.
 *
 * @param valid {@code true} if the document passed all structural and checksum tests
 * @param documentType the detected identity document subtype (e.g. "DNI", "NIE", "CIF", "CPF")
 * @param errorMessageKey i18n message key explaining validation failure, or {@code null} if valid
 */
public record DocumentValidationResult(
    boolean valid, String documentType, String errorMessageKey) {

  /**
   * Constructs a successful validation result.
   *
   * @param documentType the recognized document type
   * @return valid result instance
   */
  public static DocumentValidationResult valid(String documentType) {
    return new DocumentValidationResult(true, documentType, null);
  }

  /**
   * Constructs a failed validation result.
   *
   * @param errorMessageKey i18n error key
   * @return invalid result instance
   */
  public static DocumentValidationResult invalid(String errorMessageKey) {
    return new DocumentValidationResult(false, null, errorMessageKey);
  }
}
