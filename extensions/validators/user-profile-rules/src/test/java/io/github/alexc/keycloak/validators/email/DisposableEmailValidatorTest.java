package io.github.alexc.keycloak.validators.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidatorConfig;

class DisposableEmailValidatorTest {

  private static final ValidatorConfig EMPTY_CONFIG = new ValidatorConfig(Collections.emptyMap());

  private DisposableEmailValidatorProvider provider;
  private DisposableEmailValidatorProviderFactory factory;

  @BeforeEach
  void setUp() {
    factory = new DisposableEmailValidatorProviderFactory();
    factory.init(null);
    provider = (DisposableEmailValidatorProvider) factory.create(null);
  }

  @Test
  @DisplayName("Provider ID must be 'disposable-email'")
  void testProviderId() {
    assertEquals(DisposableEmailValidatorProviderFactory.ID, provider.getId());
    assertEquals(DisposableEmailValidatorProviderFactory.ID, factory.getId());
  }

  @Test
  @DisplayName("Preloaded domains from embedded resource must be loaded")
  void testPreloadedDomainsResource() {
    Set<String> domains = factory.getPreloadedBlockedDomains();
    assertNotNull(domains);
    assertTrue(domains.size() > 50, "Embedded disposable-domains.txt must load over 50 domains");
    assertTrue(domains.contains("mailinator.com"));
    assertTrue(domains.contains("tempmail.com"));
    assertTrue(domains.contains("guerrillamail.com"));
    assertTrue(domains.contains("10minutemail.com"));
    assertTrue(domains.contains("yopmail.com"));
  }

  @Test
  @DisplayName("Direct match on disposable domain must be rejected")
  void testDirectBlockedDomain() {
    ValidationContext context = new ValidationContext();
    provider.validate("user@mailinator.com", "email", context, EMPTY_CONFIG);

    assertFalse(context.isValid());
    assertEquals(1, context.getErrors().size());
    assertEquals(
        DisposableEmailValidatorProvider.ERROR_DISPOSABLE_EMAIL,
        context.getErrors().iterator().next().getMessage());
  }

  @Test
  @DisplayName("Subdomain of a blocked domain must be rejected by default")
  void testSubdomainBlockedByDefault() {
    ValidationContext context = new ValidationContext();
    provider.validate("test@sub.mailinator.com", "email", context, EMPTY_CONFIG);

    assertFalse(context.isValid(), "sub.mailinator.com must be blocked by default");

    ValidationContext deepContext = new ValidationContext();
    provider.validate("test@deep.nested.sub.mailinator.com", "email", deepContext, EMPTY_CONFIG);
    assertFalse(deepContext.isValid(), "deep.nested.sub.mailinator.com must be blocked by default");
  }

  @Test
  @DisplayName("Subdomain should be allowed when block-subdomains is false")
  void testSubdomainAllowedWhenBlockSubdomainsFalse() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(DisposableEmailValidatorProviderFactory.CONF_BLOCK_SUBDOMAINS, false);
    ValidatorConfig config = new ValidatorConfig(configMap);

    ValidationContext context = new ValidationContext();
    provider.validate("test@sub.mailinator.com", "email", context, config);

    assertTrue(context.isValid(), "subdomain should pass when block-subdomains is false");
  }

  @Test
  @DisplayName("Legitimate domain must be accepted")
  void testLegitimateDomainIsValid() {
    ValidationContext ctx1 = new ValidationContext();
    provider.validate("alice@company.com", "email", ctx1, EMPTY_CONFIG);
    assertTrue(ctx1.isValid());

    ValidationContext ctx2 = new ValidationContext();
    provider.validate("bob@gmail.com", "email", ctx2, EMPTY_CONFIG);
    assertTrue(ctx2.isValid());

    ValidationContext ctx3 = new ValidationContext();
    provider.validate("dev@proton.me", "email", ctx3, EMPTY_CONFIG);
    assertTrue(ctx3.isValid());
  }

  @Test
  @DisplayName("Custom blocked domains passed via ValidatorConfig must be rejected")
  void testCustomBlockedDomains() {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put(
        DisposableEmailValidatorProviderFactory.CONF_CUSTOM_BLOCKED_DOMAINS,
        "customjunk.org,  fake-burner.net ");
    ValidatorConfig config = new ValidatorConfig(configMap);

    ValidationContext ctxCustom1 = new ValidationContext();
    provider.validate("user@customjunk.org", "email", ctxCustom1, config);
    assertFalse(ctxCustom1.isValid());

    ValidationContext ctxCustomSub = new ValidationContext();
    provider.validate("user@corp.fake-burner.net", "email", ctxCustomSub, config);
    assertFalse(ctxCustomSub.isValid());
  }

  @Test
  @DisplayName("Email check must be case-insensitive")
  void testCaseInsensitiveMatching() {
    ValidationContext context = new ValidationContext();
    provider.validate("User@MAILINATOR.COM", "email", context, EMPTY_CONFIG);
    assertFalse(context.isValid());

    ValidationContext contextSub = new ValidationContext();
    provider.validate("Test@Sub.Temp-Mail.Org", "email", contextSub, EMPTY_CONFIG);
    assertFalse(contextSub.isValid());
  }

  @Test
  @DisplayName("Malformed, null, or empty email values must be ignored")
  void testMalformedOrNullEmailIgnored() {
    ValidationContext ctxNull = new ValidationContext();
    provider.validate(null, "email", ctxNull, EMPTY_CONFIG);
    assertTrue(ctxNull.isValid());

    ValidationContext ctxEmpty = new ValidationContext();
    provider.validate("", "email", ctxEmpty, EMPTY_CONFIG);
    assertTrue(ctxEmpty.isValid());

    ValidationContext ctxNoAt = new ValidationContext();
    provider.validate("plainaddress", "email", ctxNoAt, EMPTY_CONFIG);
    assertTrue(ctxNoAt.isValid());

    ValidationContext ctxEmptyDomain = new ValidationContext();
    provider.validate("user@", "email", ctxEmptyDomain, EMPTY_CONFIG);
    assertTrue(ctxEmptyDomain.isValid());
  }

  @Test
  @DisplayName("Factory lifecycle, config properties, and close")
  void testFactoryLifecycle() {
    assertNotNull(factory.getHelpText());
    assertNotNull(factory.getConfigProperties());
    assertEquals(2, factory.getConfigProperties().size());
    assertTrue(factory.validateConfig(null, EMPTY_CONFIG).isValid());

    factory.close();
    assertTrue(factory.getPreloadedBlockedDomains().isEmpty());

    // Inject custom domains for test
    factory.setPreloadedBlockedDomains(Set.of("testblock.com"));
    assertEquals(1, factory.getPreloadedBlockedDomains().size());
  }
}
