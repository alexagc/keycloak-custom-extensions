package io.github.alexc.keycloak.validators.nationalid;

/**
 * Strategy interface defining identity document validation algorithms for a specific country.
 *
 * <p>Implementations encapsulate official government checksum algorithms, structural formatting
 * constraints, and foreigner status handling.
 */
public interface CountryDocumentStrategy {

  /**
   * Returns the ISO 3166-1 alpha-2 country code handled by this strategy (e.g. "ES", "IT", "FR").
   *
   * @return uppercase two-letter country code
   */
  String getCountryCode();

  /**
   * Validates a national identity document number against official checksum algorithms.
   *
   * @param documentNumber normalized document number string (uppercase, stripped of formatting)
   * @param allowForeigners whether foreigner resident document formats are accepted (e.g. NIE in Spain)
   * @return {@link DocumentValidationResult} containing validity status and detected document type
   */
  DocumentValidationResult validate(String documentNumber, boolean allowForeigners);
}
