package io.github.alexc.keycloak.validators.nationalid;

import io.github.alexc.keycloak.validators.nationalid.strategies.BrazilDocumentStrategy;
import io.github.alexc.keycloak.validators.nationalid.strategies.FranceDocumentStrategy;
import io.github.alexc.keycloak.validators.nationalid.strategies.GermanyDocumentStrategy;
import io.github.alexc.keycloak.validators.nationalid.strategies.ItalyDocumentStrategy;
import io.github.alexc.keycloak.validators.nationalid.strategies.SpainDocumentStrategy;
import io.github.alexc.keycloak.validators.nationalid.strategies.UnitedStatesDocumentStrategy;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry maintaining the supported {@link CountryDocumentStrategy} instances
 * categorized by ISO 3166-1 alpha-2 country codes.
 */
public class CountryStrategyRegistry {

  private final Map<String, CountryDocumentStrategy> strategies = new ConcurrentHashMap<>();

  /**
   * Initializes the registry and registers default country document strategies (ES, IT, FR, BR, US,
   * DE).
   */
  public CountryStrategyRegistry() {
    register(new SpainDocumentStrategy());
    register(new ItalyDocumentStrategy());
    register(new FranceDocumentStrategy());
    register(new BrazilDocumentStrategy());
    register(new UnitedStatesDocumentStrategy());
    register(new GermanyDocumentStrategy());
  }

  /**
   * Registers a new or custom country document strategy.
   *
   * @param strategy strategy implementation
   */
  public void register(CountryDocumentStrategy strategy) {
    if (strategy != null && strategy.getCountryCode() != null) {
      strategies.put(strategy.getCountryCode().toUpperCase(Locale.ROOT), strategy);
    }
  }

  /**
   * Retrieves a strategy by country code.
   *
   * @param countryCode two-letter ISO country code (case-insensitive)
   * @return strategy instance, or {@code null} if unsupported
   */
  public CountryDocumentStrategy getStrategy(String countryCode) {
    if (countryCode == null) {
      return null;
    }
    return strategies.get(countryCode.trim().toUpperCase(Locale.ROOT));
  }

  /**
   * Checks whether a country code is supported by a registered strategy.
   *
   * @param countryCode two-letter ISO country code
   * @return {@code true} if supported
   */
  public boolean isSupported(String countryCode) {
    if (countryCode == null) {
      return false;
    }
    return strategies.containsKey(countryCode.trim().toUpperCase(Locale.ROOT));
  }

  /**
   * Returns an unmodifiable set of all currently supported country codes.
   *
   * @return set of two-letter uppercase country codes
   */
  public Set<String> getSupportedCountries() {
    return Collections.unmodifiableSet(strategies.keySet());
  }
}
