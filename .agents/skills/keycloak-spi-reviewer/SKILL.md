---
name: keycloak-spi-reviewer
description: Comprehensive architectural and code quality auditor for Keycloak 26.x Quarkus Service Provider Interfaces (SPIs). Enforces thread-safety, non-blocking fault tolerance, zero-leak lifecycles, and Red Hat enterprise standards. Must be executed after implementing or modifying any Keycloak SPI extension.
---

# Keycloak SPI Reviewer Skill

Use this skill to conduct a rigorous, comprehensive architectural and code quality review on any Keycloak Service Provider Interface (SPI) extension before submitting a Pull Request or merging code into `main`.

This reviewer covers all SPI families in this repository:
* **Event Listeners** (`EventListenerProvider`)
* **Protocol Mappers & Claim Enrichers** (`AbstractOIDCProtocolMapper`, `OIDCAccessTokenMapper`)
* **Authenticators & Form Actions** (`Authenticator`, `AuthenticatorFactory`)
* **Required Actions** (`RequiredActionProvider`, `RequiredActionFactory`)
* **User Storage & Federation** (`UserStorageProvider`, `UserLookupProvider`)

---

## 1. General Architectural Foundations & Lifecycle Boundaries

Every Keycloak SPI strictly separates server boot lifecycle from request execution lifecycle:

* [ ] **`ProviderFactory` as Server Singleton:**
  * Must be initialized once at startup in `init(Config.Scope config)` and `postInit(KeycloakSessionFactory factory)`.
  * Must **NEVER** hold request-scoped data, user references, or `KeycloakSession` in instance variables.
  * Manages shared connection pools, HTTP clients, and global SPI configuration.
* [ ] **`Provider` as Request/Session Scope:**
  * Created per transaction or request in `factory.create(KeycloakSession session)`.
  * Operates strictly within the boundary of the provided `KeycloakSession`.
  * Cleans up per-request resources in `close()`.
* [ ] **Anti-Pattern Check (Single Instance / `return this;`):**
  * Reject any implementation where `ProviderFactory.create()` returns `this` unless the component is 100% stateless across all realms, users, and concurrent threads.
* [ ] **Automatic Service Registration (`@AutoService`):**
  * The factory class must be annotated with `@AutoService(<FactoryInterface>.class)`.
  * Eliminates manual `META-INF/services/` maintenance and guarantees compile-time validation.

---

## 2. SPI-Specific Architectural Deep Dives

### A. Event Listener SPIs (`EventListenerProvider`)
* [ ] **Non-Blocking Asynchronous Dispatch:** Outbound I/O (HTTP webhooks, message queues) must execute asynchronously (e.g. `HttpClient.sendAsync(...)`). Never block the event thread.
* [ ] **Strict Timeout Bounds:** Explicit connect (e.g. 3s) and read (e.g. 5s) timeouts. Unbounded timeouts are strictly prohibited.
* [ ] **Transaction Resilience:** Outbound errors (timeouts, 500s) must be wrapped in `try/catch`. They must **NEVER** abort or roll back a user's login or registration transaction.
* [ ] **Data Sanitization:** Passwords, credentials, and sensitive tokens must be filtered and never leaked in event payload details.

### B. Protocol Mappers & Token Enrichers (`AbstractOIDCProtocolMapper`)
* [ ] **Token Issuance Latency:** `transformAccessToken` and `transformIDToken` are invoked on every token issuance and refresh. They must be ultra-fast: **NO synchronous blocking external HTTP or slow DB queries without caching**.
* [ ] **Token Bloat Prevention:** JWT tokens travel in HTTP `Authorization` headers. Verify the mapper does not inject oversized payloads (e.g. >4KB) that would trigger HTTP 431 / 502 in reverse proxies.
* [ ] **Configuration Metadata:** Implement `getConfigProperties()` properly returning `ProviderConfigProperty` definitions with clear tooltips and default values.

### C. Authenticators & Flow Steps (`Authenticator`, `AuthenticatorFactory`)
* [ ] **Thread-Safe State Management:** Authenticators must **NEVER** store user state, PINs, or step data in class fields. State must strictly reside in `context.getAuthenticationSession().setAuthNote("key", "val")` or `setUserSessionNote()`.
* [ ] **Explicit Flow Outcomes:** Every execution path must explicitly terminate in one of:
  * `context.success()`
  * `context.challenge(response)`
  * `context.failure(AuthenticationFlowError, response)`
  * `context.attempted()`
* [ ] **Theme & i18n Resolution:** Error messages and form templates must use Keycloak message bundles (`context.form().setError("msgKey")`) instead of hardcoded English strings.

### D. Required Actions (`RequiredActionProvider`)
* [ ] **O(1) Trigger Evaluation:** `evaluateTriggers(RequiredActionContext)` executes on **every single user login**. It must be instant (checking user attributes/models), never performing heavy external scans.
* [ ] **Idempotent Completion:** `processAction(RequiredActionContext)` must cleanly remove the required action flag from the user model upon successful verification.

### E. User Storage Providers (`UserStorageProvider`)
* [ ] **Cache Coordination:** Coordinate properly with Keycloak's `UserCache` to prevent stale reads and redundant queries.
* [ ] **Credential Security:** Never store or log plaintext passwords. Use standard Keycloak credential hashing mechanisms.

---

## 3. Quarkus Classloading & Dependency Hygiene

* [ ] **Provided Scope on Keycloak Dependencies:**
  * `keycloak-core`, `keycloak-server-spi`, `keycloak-server-spi-private`, and `keycloak-services` must have `<scope>provided</scope>`.
* [ ] **No Classloader Conflicts:**
  * No embedded logging backends (Logback, Slf4j Simple). Always use `org.jboss.logging.Logger`.
  * No conflicting Jakarta EE or Spring runtime jars packaged into the extension.

---

## 4. Modern Java 21 Standards

* [ ] **Immutable Records:** DTOs, payloads, and configuration objects must use Java 21 `record`.
* [ ] **Native HTTP Client:** Use `java.net.http.HttpClient` configured at factory singleton level.
* [ ] **Null Safety & Exceptions:** Meaningful log messages with context (realm name, client ID) using structured `LOG.infov(...)` / `LOG.errorv(...)`.

---

## 5. Testing, Mutation Score & E2E Suite

* [ ] **Unit Tests (JUnit 5 + Mockito):**
  * Mock `KeycloakSession`, `RealmModel`, and configuration scopes.
  * Verify edge cases, negative scenarios, and timeout handling.
* [ ] **Mutation Testing (Pitest):**
  * Verify mutation score: `mvn test-compile org.pitest:pitest-maven:mutationCoverage`. Ensure no weak assertions.
* [ ] **End-to-End Testcontainers (`*IT.java`):**
  * Uses `com.github.dasniko:testcontainers-keycloak` with `.withProviderClassesFrom("target/classes")`.
  * Connected to dedicated mock containers (e.g. MockServer) on an isolated Docker network. Zero external internet calls.
  * Automatically skips on local machines without Docker (`DockerClientFactory.instance().isDockerAvailable()`) while running fully in GitHub Actions CI.
* [ ] **Code Formatting (Spotless):**
  * Passes `mvn spotless:check` with Google Java Format.

---

## 6. Documentation & Release Standards

* [ ] **Submodule `README.md`:** Overview, architecture diagram, configuration variables table (`KC_SPI_...`), and payload examples.
* [ ] **Changelog Integrity:** `CHANGELOG.md` entry under `[Unreleased]` following Keep a Changelog.
* [ ] **Root Catalog:** `README.md` catalog table reflects the extension's status.

---

## Review Execution Command

To execute this audit on the current branch:
```bash
git diff main...HEAD
```
Verify every item in this checklist and produce a structured audit report with:
* **Verdict:** `PASSED` / `CHANGES_REQUIRED`
* **Findings:** Specific file links and line numbers for any violations or suggestions.
