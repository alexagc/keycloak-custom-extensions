package io.github.alexc.keycloak.risk.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Composite result of an adaptive risk evaluation session.
 *
 * @param overallScore Normalized composite risk score [0, 100]
 * @param level Risk classification level
 * @param factors Individual contributing risk factors
 * @param shouldStepUp Whether Step-Up MFA challenge is required
 * @param shouldBlock Whether authentication should be blocked
 */
public record RiskEvaluationResult(
    int overallScore,
    RiskLevel level,
    List<RiskFactor> factors,
    boolean shouldStepUp,
    boolean shouldBlock) implements Serializable {

  /**
   * Compact constructor ensuring immutable factors list.
   */
  public RiskEvaluationResult {
    Objects.requireNonNull(level, "level must not be null");
    factors = (factors != null) ? List.copyOf(factors) : List.of();
    if (overallScore < 0 || overallScore > 100) {
      throw new IllegalArgumentException("overallScore must be between 0 and 100, got: " + overallScore);
    }
  }

  /**
   * Checks if this evaluation is classified as low risk.
   *
   * @return true if risk level is LOW and no step-up or block is needed
   */
  public boolean isLowRisk() {
    return level == RiskLevel.LOW && !shouldStepUp && !shouldBlock;
  }
}
