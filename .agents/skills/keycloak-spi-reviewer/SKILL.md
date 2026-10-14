---
name: keycloak-spi-reviewer
description: Comprehensive architectural and code quality auditor for Keycloak 26.x Quarkus Service Provider Interfaces (SPIs). Enforces thread-safety, non-blocking fault tolerance, zero-leak lifecycles, and Red Hat enterprise standards. Must be executed after implementing or modifying any Keycloak SPI extension.
---

# Keycloak SPI Reviewer Skill

Use this skill to conduct a rigorous, automated-style architectural review on any Keycloak Service Provider Interface (SPI) extension before submitting a Pull Request or merging code into `main`.

---

## 1. Architectural Integrity & Lifecycle Audit

Every Keycloak SPI has two distinct lifecycles. Verify that they are strictly separated:

* [ ] **Factory as Server Singleton (`ProviderFactory`):**
  * Initialized once at server boot in `init(Config.Scope config)` and `postInit(KeycloakSessionFactory factory)`.
  * Must **NEVER** hold request-scoped data, user details, or `KeycloakSession` references in instance fields.
  * Manages shared connection pools, HTTP clients, and global configuration.
* [ ] **Provider as Request/Session Scope (`Provider`):**
  * Created per transaction or request in `factory.create(KeycloakSession session)`.
  * Operates strictly within the boundary of the provided `KeycloakSession`.
  * Cleans up per-request resources in `close()`.
* [ ] **Anti-Pattern Check (Single Instance Anti-Pattern):**
  * Verify that the factory does NOT return `this` in `create()` unless the component is 100% stateless and thread-safe.

---

## 2. Resilience, Concurrency & Fault Tolerance

Keycloak is mission-critical identity infrastructure. SPIs must never compromise server stability:

* [ ] **Non-Blocking Execution:**
  * Outbound network operations (HTTP webhooks, message queues) must execute asynchronously (e.g., native Java 21 `HttpClient.sendAsync(...)`).
  * No unbounded blocking calls (`.get()` without timeout, `Thread.sleep()`).
* [ ] **Strict Timeout Bounds:**
  * Every network connection must have explicit connect and read timeouts (e.g., 3s connect, 5s read).
* [ ] **Transaction Protection (No Accidental Rollback):**
  * Event Listeners and audit probes must wrap external calls in robust `try/catch`.
  * Remote endpoint downtime or 5xx errors must **NEVER** abort the user authentication transaction.

---

## 3. Quarkus Classloading & Dependency Hygiene

Keycloak 26.x runs on the Quarkus runtime with closed-world build augmentation:

* [ ] **Provided Scope on Keycloak APIs:**
  * `keycloak-core`, `keycloak-server-spi`, `keycloak-server-spi-private`, and `keycloak-services` must have `<scope>provided</scope>`.
* [ ] **Zero Redundant Runtimes:**
  * No Spring, Spring Boot, or conflicting Jakarta EE engines embedded in the JAR.
* [ ] **Service Registration via `@AutoService`:**
  * Provider factory must be decorated with `@AutoService(<FactoryInterface>.class)`.
  * Compile-time annotation processor generates the descriptor in `META-INF/services/`.

---

## 4. Modern Java 21 Standards

* [ ] **Immutable Records:**
  * Use Java 21 `record` for all DTOs, event schemas, and configuration holders.
* [ ] **Idiomatic Logging:**
  * Use JBoss Logging (`org.jboss.logging.Logger.getLogger(...)`) provided by Keycloak.
  * No `System.out.println`, `System.err.println`, or unconfigured SLF4J loggers.
* [ ] **Security & Data Sanitization:**
  * Ensure passwords, hashes, TOTP secrets, or authorization tokens are never logged or leaked in outbound payloads.

---

## 5. Verification & Testing Suite

* [ ] **Unit Tests (JUnit 5 + Mockito):**
  * Mock `KeycloakSession`, `RealmModel`, and configuration scopes.
  * Verify edge cases, filtering, and exception handling without network dependencies.
* [ ] **End-to-End Testcontainer Test (`*IT.java`):**
  * Real Keycloak 26.x container test verifying that the JAR augments cleanly via `kc.sh build` and starts without classloader collisions.
* [ ] **Mutation Testing (Pitest):**
  * Run `mvn test-compile org.pitest:pitest-maven:mutationCoverage` to verify test suite assertion strength.
* [ ] **Code Formatting (Spotless):**
  * Verify `mvn spotless:check` passes without style violations.

---

## 6. Documentation & Release Standards

* [ ] **Submodule `README.md`:**
  * Overview, configuration variables (`KC_SPI_...`), JSON event payload schemas, and installation instructions.
* [ ] **Changelog Updated:**
  * `CHANGELOG.md` entry under `[Unreleased]` following Keep a Changelog.
* [ ] **Catalog Updated:**
  * Root `README.md` catalog table reflects the current implementation status.

---

## Review Execution Command

To execute this audit, inspect the git diff:
```bash
git diff main...HEAD
```
Verify every item in this checklist and produce a structured audit report with:
* **Verdict:** `PASSED` / `CHANGES_REQUIRED`
* **Findings:** Specific file links and line numbers for any violations.
