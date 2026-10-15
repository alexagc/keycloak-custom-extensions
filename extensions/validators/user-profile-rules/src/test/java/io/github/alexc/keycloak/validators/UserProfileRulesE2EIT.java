package io.github.alexc.keycloak.validators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPAttributePermissions;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Enterprise End-to-End integration test validating that the Declarative User Profile Rules SPI:
 *
 * <ol>
 *   <li>Mounts into a real Keycloak 26.x Quarkus container using testcontainers-keycloak.
 *   <li>Discovers all 3 custom Validator factories: {@code minimum-age}, {@code disposable-email},
 *       and {@code national-id}.
 *   <li>Enforces attribute validation rules upon User Profile registration via Keycloak Admin REST
 *       API.
 *   <li>Rejects invalid users with HTTP 400 and passes valid users with HTTP 201 Created.
 *   <li>Gracefully skips execution on local developer workstations without Docker (Zero-Install
 *       Workflow).
 * </ol>
 */
@Testcontainers
@Tag("e2e")
class UserProfileRulesE2EIT {

  private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8.0";

  @BeforeAll
  static void checkDockerEnvironment() {
    boolean dockerAvailable = false;
    try {
      dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      // Docker not running or not installed
    }
    Assumptions.assumeTrue(
        dockerAvailable, "Docker daemon not available; skipping Keycloak Testcontainers E2E test.");
  }

  @Test
  @DisplayName("Keycloak 26.x Declarative User Profile enforces custom validator rules")
  void testUserProfileValidationInRealKeycloak() {
    try (KeycloakContainer keycloak =
        new KeycloakContainer(KEYCLOAK_IMAGE)
            .withProviderClassesFrom("target/classes")
            .withEnv("KC_FEATURES", "declarative-user-profile")) {

      keycloak.start();
      assertTrue(keycloak.isRunning(), "Keycloak container must be running");

      Keycloak adminClient = keycloak.getKeycloakAdminClient();

      // Create test realm with Declarative User Profile enabled
      String realmName = "rules-validation-realm";
      RealmRepresentation testRealm = new RealmRepresentation();
      testRealm.setRealm(realmName);
      testRealm.setEnabled(true);
      adminClient.realms().create(testRealm);

      // Configure User Profile with the 3 custom validators
      UPConfig upConfig = adminClient.realm(realmName).users().userProfile().getConfiguration();
      if (upConfig == null) {
        upConfig = new UPConfig();
      }

      UPAttributePermissions adminPermissions = new UPAttributePermissions();
      adminPermissions.setView(Collections.singleton("admin"));
      adminPermissions.setEdit(Collections.singleton("admin"));

      // 1. Configure 'birthdate' attribute with 'minimum-age' validator
      UPAttribute birthdateAttr = new UPAttribute();
      birthdateAttr.setName("birthdate");
      birthdateAttr.setPermissions(adminPermissions);
      Map<String, Map<String, Object>> birthdateValidators = new HashMap<>();
      birthdateValidators.put("minimum-age", Collections.singletonMap("min-age", 18));
      birthdateAttr.setValidations(birthdateValidators);

      // 2. Configure 'email' attribute adding 'disposable-email' validator
      UPAttribute emailAttr = new UPAttribute();
      emailAttr.setName("email");
      emailAttr.setPermissions(adminPermissions);
      Map<String, Map<String, Object>> emailValidators = new HashMap<>();
      emailValidators.put("disposable-email", Collections.singletonMap("block-subdomains", true));
      emailAttr.setValidations(emailValidators);

      // 3. Configure 'dni' attribute with 'national-id' validator (Spain ES)
      UPAttribute dniAttr = new UPAttribute();
      dniAttr.setName("dni");
      dniAttr.setPermissions(adminPermissions);
      Map<String, Map<String, Object>> dniValidators = new HashMap<>();
      Map<String, Object> nationalIdConfig = new HashMap<>();
      nationalIdConfig.put("country", "ES");
      nationalIdConfig.put("normalize", true);
      dniValidators.put("national-id", nationalIdConfig);
      dniAttr.setValidations(dniValidators);

      upConfig.addOrReplaceAttribute(birthdateAttr);
      upConfig.addOrReplaceAttribute(emailAttr);
      upConfig.addOrReplaceAttribute(dniAttr);

      adminClient.realm(realmName).users().userProfile().update(upConfig);

      // --- Scenario A: Attempt registering underage user (should be rejected HTTP 400) ---
      UserRepresentation underageUser = new UserRepresentation();
      underageUser.setUsername("underage_user");
      underageUser.setEmail("legit@company.com");
      underageUser.setEnabled(true);
      underageUser.singleAttribute("birthdate", "2020-01-01");
      underageUser.singleAttribute("dni", "12345678Z");

      try (Response res = adminClient.realm(realmName).users().create(underageUser)) {
        assertEquals(400, res.getStatus(), "Underage user registration must be rejected with 400 Bad Request");
      }

      // --- Scenario B: Attempt registering disposable email user (should be rejected HTTP 400) ---
      UserRepresentation disposableUser = new UserRepresentation();
      disposableUser.setUsername("disposable_user");
      disposableUser.setEmail("spammer@mailinator.com");
      disposableUser.setEnabled(true);
      disposableUser.singleAttribute("birthdate", "1990-01-01");
      disposableUser.singleAttribute("dni", "12345678Z");

      try (Response res = adminClient.realm(realmName).users().create(disposableUser)) {
        assertEquals(400, res.getStatus(), "Disposable email registration must be rejected with 400 Bad Request");
      }

      // --- Scenario C: Attempt registering invalid DNI checksum (should be rejected HTTP 400) ---
      UserRepresentation invalidDniUser = new UserRepresentation();
      invalidDniUser.setUsername("invalid_dni_user");
      invalidDniUser.setEmail("valid@company.com");
      invalidDniUser.setEnabled(true);
      invalidDniUser.singleAttribute("birthdate", "1990-01-01");
      invalidDniUser.singleAttribute("dni", "12345678A"); // Incorrect checksum letter

      try (Response res = adminClient.realm(realmName).users().create(invalidDniUser)) {
        assertEquals(400, res.getStatus(), "Invalid DNI checksum must be rejected with 400 Bad Request");
      }

      // --- Scenario D: Valid user satisfying all 3 rules (should be accepted HTTP 201) ---
      UserRepresentation validUser = new UserRepresentation();
      validUser.setUsername("valid_user");
      validUser.setEmail("valid.adult@company.com");
      validUser.setEnabled(true);
      validUser.singleAttribute("birthdate", "1995-05-15");
      validUser.singleAttribute("dni", "12345678Z");

      try (Response res = adminClient.realm(realmName).users().create(validUser)) {
        assertEquals(201, res.getStatus(), "Valid user profile attributes must be accepted with 201 Created");
      }
    }
  }
}
