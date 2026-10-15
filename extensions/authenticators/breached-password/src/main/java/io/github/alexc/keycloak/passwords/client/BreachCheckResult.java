package io.github.alexc.keycloak.passwords.client;

/**
 * Immutable result of a breached password lookup using the HIBP k-Anonymity model.
 *
 * @param breached    true if the password was found in breaches with count >= threshold
 * @param breachCount the number of times this password appeared in known breaches (0 if clean)
 * @param degraded    true if the check was degraded due to network/API failure under fail-open
 */
public record BreachCheckResult(boolean breached, int breachCount, boolean degraded) {

    /**
     * Creates a clean result indicating the password is not breached.
     *
     * @return clean BreachCheckResult
     */
    public static BreachCheckResult clean() {
        return new BreachCheckResult(false, 0, false);
    }

    /**
     * Creates a breached result with the observed breach count.
     *
     * @param count number of observed breaches
     * @return breached BreachCheckResult
     */
    public static BreachCheckResult breached(int count) {
        return new BreachCheckResult(true, count, false);
    }

    /**
     * Creates a degraded fail-open result when the external service is unavailable.
     *
     * @return degraded fail-open BreachCheckResult
     */
    public static BreachCheckResult degradedFailOpen() {
        return new BreachCheckResult(false, 0, true);
    }
}
