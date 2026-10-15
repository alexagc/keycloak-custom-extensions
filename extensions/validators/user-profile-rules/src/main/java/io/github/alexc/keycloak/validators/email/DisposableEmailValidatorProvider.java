package io.github.alexc.keycloak.validators.email;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.validate.AbstractStringValidator;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidatorConfig;

/**
 * Keycloak Declarative User Profile validator preventing the use of disposable, throwaway, or
 * temporary email addresses (e.g. Mailinator, TempMail, GuerrillaMail).
 *
 * <p>Operates entirely in-memory using an immutable preloaded domain set for zero network latency
 * and maximum throughput during user registration.
 */
public class DisposableEmailValidatorProvider extends AbstractStringValidator {

  public static final String ERROR_DISPOSABLE_EMAIL = "error-user-profile-disposable-email";

  private final Set<String> defaultBlockedDomains;

  /**
   * Constructs a new validator instance with the preloaded blocked domains set.
   *
   * @param defaultBlockedDomains immutable set of lowercase domains to block
   */
  public DisposableEmailValidatorProvider(Set<String> defaultBlockedDomains) {
    this.defaultBlockedDomains =
        defaultBlockedDomains != null ? defaultBlockedDomains : Collections.emptySet();
  }

  @Override
  public String getId() {
    return DisposableEmailValidatorProviderFactory.ID;
  }

  @Override
  protected boolean skipValidation(Object value, ValidatorConfig config) {
    if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
      return true;
    }
    return super.skipValidation(value, config);
  }

  @Override
  protected void doValidate(
      String value, String inputHint, ValidationContext context, ValidatorConfig config) {
    if (value == null || value.trim().isEmpty()) {
      return;
    }

    String email = value.trim();
    int atIndex = email.lastIndexOf('@');
    if (atIndex <= 0 || atIndex >= email.length() - 1) {
      // Not a parseable email; let standard email format validators handle it
      return;
    }

    String domain = email.substring(atIndex + 1).toLowerCase(Locale.ROOT).trim();
    if (domain.isEmpty()) {
      return;
    }

    boolean blockSubdomains = DisposableEmailValidatorProviderFactory.DEFAULT_BLOCK_SUBDOMAINS;
    Set<String> customBlocked = Collections.emptySet();

    if (config != null) {
      blockSubdomains =
          config.getBooleanOrDefault(
              DisposableEmailValidatorProviderFactory.CONF_BLOCK_SUBDOMAINS,
              DisposableEmailValidatorProviderFactory.DEFAULT_BLOCK_SUBDOMAINS);

      String customDomainsStr =
          config.getString(DisposableEmailValidatorProviderFactory.CONF_CUSTOM_BLOCKED_DOMAINS);
      if (customDomainsStr != null && !customDomainsStr.trim().isEmpty()) {
        customBlocked =
            Arrays.stream(customDomainsStr.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
      }
    }

    if (isDomainBlocked(domain, blockSubdomains, customBlocked)) {
      context.addError(new ValidationError(getId(), inputHint, ERROR_DISPOSABLE_EMAIL, domain));
    }
  }

  private boolean isDomainBlocked(
      String domain, boolean blockSubdomains, Set<String> customBlocked) {
    if (defaultBlockedDomains.contains(domain) || customBlocked.contains(domain)) {
      return true;
    }

    if (!blockSubdomains) {
      return false;
    }

    // Check if domain is a subdomain of any blocked domain (e.g. sub.mailinator.com -> mailinator.com)
    for (String blocked : defaultBlockedDomains) {
      if (domain.endsWith("." + blocked)) {
        return true;
      }
    }

    for (String blocked : customBlocked) {
      if (domain.endsWith("." + blocked)) {
        return true;
      }
    }

    return false;
  }
}
