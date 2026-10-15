package io.github.alexc.keycloak.risk.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * An individual risk factor contributing to the composite adaptive risk score.
 *
 * @param category Functional category of the factor (e.g., IMPOSSIBLE_TRAVEL, IP_REPUTATION)
 * @param points Score contribution assigned by this factor [0, 100]
 * @param description Detailed contextual description or explanation
 */
public record RiskFactor(
    String category,
    int points,
    String description) implements Serializable {

  /**
   * Compact constructor validating constraints.
   */
  public RiskFactor {
    Objects.requireNonNull(category, "category must not be null");
    Objects.requireNonNull(description, "description must not be null");
  }
}
