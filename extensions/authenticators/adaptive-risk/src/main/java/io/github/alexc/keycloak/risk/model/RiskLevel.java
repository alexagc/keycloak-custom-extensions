package io.github.alexc.keycloak.risk.model;

/**
 * Risk classification levels determined by the adaptive risk evaluation engine.
 */
public enum RiskLevel {
  /**
   * Risk score is below the step-up threshold. Authentication proceeds normally.
   */
  LOW,

  /**
   * Risk score is between the step-up and block thresholds. Dynamic Step-Up MFA is challenged.
   */
  MEDIUM,

  /**
   * Risk score meets or exceeds the critical block threshold. Authentication is rejected.
   */
  CRITICAL
}
