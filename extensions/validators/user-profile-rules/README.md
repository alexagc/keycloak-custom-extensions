# Declarative User Profile Rules SPI for Keycloak 26.x

Enterprise-grade validator suite for Keycloak 26.x Declarative User Profile, providing native attribute validation for legal age verification, disposable email blocking, and multi-country identity document checksums.

---

## 🚀 Included Validators

This module provides three decoupled, high-performance validators configurable directly from the Keycloak Admin Console under **Realm Settings > User Profile > Attributes**:

| Validator ID | Purpose | Supported Formats / Algorithms |
| :--- | :--- | :--- |
| `minimum-age` | Age verification (GDPR / COPPA) | Custom date parsing (`LocalDate.parse`, `Period.between`) |
| `disposable-email` | Block throwaway/temporary email providers | In-memory lookup from embedded blocklist (zero I/O latency) |
| `national-id` | Multi-country legal document validator | Mathematical checksums for ES, IT, FR, BR, US, DE |

---

## ⚙️ Validator Configurations & Parameters

### 1. `minimum-age`
Verifies that a user date of birth meets a required legal age threshold.

* **Target Attribute:** Typically `birthdate`
* **Configuration Parameters:**
  * `min-age` (Integer, default: `18`): Required minimum age in full years.
  * `date-format` (String, default: `yyyy-MM-dd`): Expected date format pattern.
* **Error Keys:**
  * `error-user-profile-minimum-age`: Emitted when the user is underage or born in the future.
  * `error-user-profile-minimum-age-invalid-date`: Emitted if the input does not conform to `date-format`.

### 2. `disposable-email`
Blocks registration attempts from throwaway, temporary, or burner email domain providers (e.g. Mailinator, TempMail, GuerrillaMail, Yopmail).

* **Target Attribute:** `email`
* **Configuration Parameters:**
  * `block-subdomains` (Boolean, default: `true`): Whether subdomains of disposable providers (e.g. `sub.mailinator.com`) are also blocked.
  * `custom-blocked-domains` (String): Optional comma-separated list of additional domains to reject.
* **Error Key:**
  * `error-user-profile-disposable-email`: Emitted when the email domain belongs to a blocked provider.

### 3. `national-id`
Validates national identity and tax identification document numbers using official government checksum algorithms and the Strategy design pattern.

* **Target Attribute:** `national_id`, `dni`, `tax_id`, `ssn`, `cpf`, etc.
* **Configuration Parameters:**
  * `country` (String, required): ISO 3166-1 alpha-2 code (e.g. `ES`, `IT`, `FR`, `BR`, `US`, `DE`) **OR** dynamic attribute reference: `attribute:<fieldName>` (e.g. `attribute:country`).
  * `allow-foreigners` (Boolean, default: `true`): Allows resident foreigner document types (e.g. Spanish NIE).
  * `normalize` (Boolean, default: `true`): Strips whitespace, hyphens, and dots, and converts characters to uppercase prior to checksum execution.
* **Official Checksum Algorithms Supported:**
  * **Spain (`ES`):**
    * `DNI`: 8 digits + modulo 23 control letter mapped to `TRWAGMYFPDXBNJZSQVHLCKE`.
    * `NIE`: Foreigner identifier with `X/Y/Z` prefix substitution + modulo 23.
    * `CIF`: Corporate tax ID with Luhn-like sum and entity-specific control digit or letter.
  * **Italy (`IT`):**
    * `Codice Fiscale`: 16 alphanumeric characters evaluated across official Agenzia delle Entrate odd and even position conversion tables modulo 26.
  * **France (`FR`):**
    * `NIR` (Numéro de Sécurité Sociale): 13 digits + 2-digit control key (`97 - (number % 97)`), including Corsican départements `2A` and `2B`.
  * **Brazil (`BR`):**
    * `CPF`: 11 digits with strict two-digit modulo 11 descending weight verification and repeated number rejection.
  * **United States (`US`):**
    * `SSN`: 9 digits conforming to SSA issuance criteria (excludes area codes `000`, `666`, `900-999`, group `00`, serial `0000`, and known advertising dummies).
  * **Germany (`DE`):**
    * `Steuer-ID` (IdNr): 11 digits with leading-zero prohibition, internal digit frequency rules, and official ISO/IEC 7064 MOD 11, 10 checksum verification.

---

## 📋 Declarative User Profile Configuration Example (JSON)

Save the following configuration inside Keycloak's User Profile JSON layout:

```json
{
  "attributes": [
    {
      "name": "birthdate",
      "displayName": "${birthdate}",
      "validations": {
        "minimum-age": {
          "min-age": 18,
          "date-format": "yyyy-MM-dd"
        }
      },
      "permissions": {
        "view": ["admin", "user"],
        "edit": ["admin", "user"]
      }
    },
    {
      "name": "email",
      "validations": {
        "email": {},
        "disposable-email": {
          "block-subdomains": true,
          "custom-blocked-domains": "spamcorp.net,trashmail.xyz"
        }
      }
    },
    {
      "name": "country",
      "displayName": "${country}",
      "permissions": {
        "view": ["admin", "user"],
        "edit": ["admin", "user"]
      }
    },
    {
      "name": "national_id",
      "displayName": "${nationalId}",
      "validations": {
        "national-id": {
          "country": "attribute:country",
          "allow-foreigners": true,
          "normalize": true
        }
      },
      "permissions": {
        "view": ["admin", "user"],
        "edit": ["admin", "user"]
      }
    }
  ]
}
```

---

## 🛠️ Installation and Deployment

### 1. Copy the Extension JAR into Keycloak
Drop the compiled `keycloak-validator-user-profile-rules-1.0.0.jar` into Keycloak's providers directory:
```bash
cp target/keycloak-validator-user-profile-rules-1.0.0.jar /opt/keycloak/providers/
```

### 2. Start Keycloak with Declarative User Profile
Ensure the declarative user profile feature is active (enabled by default in Keycloak 26.x):
```bash
/opt/keycloak/bin/kc.sh start-dev --features=declarative-user-profile
```

### 3. Verify in Admin Console
1. Log in to the Admin Console.
2. Select your target realm.
3. Navigate to **Realm Settings** > **User Profile** tab.
4. Edit or create an attribute, click **Add Validator**, and observe `minimum-age`, `disposable-email`, and `national-id` listed as available validation providers.
