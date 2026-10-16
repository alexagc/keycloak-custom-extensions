# AGENTS.md - Developer & AI Instructions for `keycloak-custom-extensions`

This document defines the architectural standards, technical guidelines, and workflow rules for any AI agent or developer contributing to this repository.

---

## 1. Project Overview & Philosophy

* **Repository:** `keycloak-custom-extensions`
* **Purpose:** A curated, enterprise-grade suite of modular Service Provider Interfaces (SPIs) for **Keycloak 26.x** running on the modern **Quarkus** runtime.
* **Architecture Pattern:** Maven Multi-Module monorepo categorized strictly by SPI type:
  `extensions/<spi-category>/<extension-name>/` (e.g., `extensions/event-listeners/webhook/`).
* **Packaging & Distribution:** Each extension compiles into a self-contained `.jar` published to **GitHub Releases**. Keycloak operators install it by copying the `.jar` to `/opt/keycloak/providers/`.

---

## 2. Zero-Install Workflow (Crucial Rule)

* **Local Machine Policy:** Do **NOT** assume or require local installation of Java, Maven, or Docker on the developer's workstation.
* **Build & Validation Authority:** All compilation, automated checks, and container-based tests (via Testcontainers) run in **GitHub Actions**.
* **Cloud Debugging:** When an interactive environment is needed, use **GitHub Codespaces** (`.devcontainer/devcontainer.json`).
* **Contribution Flow:** Edit files locally -> Commit/Push to a feature branch -> Open PR -> Verify via GitHub Actions CI pipeline.

---

## 3. Keycloak SPI Engineering Standards

When creating or modifying any Keycloak SPI in this repository, you **MUST** follow these rules:

### 3.1. Provider & Factory Separation
Keycloak SPIs strictly distinguish between the factory and provider lifecycles:
* **`ProviderFactory`:** A singleton initialized once when the Keycloak server boots. It handles configuration (`init`, `postInit`), creates provider instances, and registers the provider ID (`getId()`).
* **`Provider`:** Request-scoped or session-scoped object created by `ProviderFactory.create(KeycloakSession session)`. It implements the actual logic.
* **Lifecycle Cleanup:** Always clean up resources in `Provider.close()`.

### 3.2. Service Registration (`@AutoService`)
Every SPI factory implementation **must** be annotated with `@AutoService(<FullyQualifiedFactoryInterface>.class)` from Google AutoService.
* Example: `@AutoService(EventListenerProviderFactory.class)` on the factory class.
* Compile-time annotation processing automatically generates the `META-INF/services/` provider descriptor without manual file maintenance.

### 3.3. Fault Tolerance & Non-Blocking Execution
* **Event Listeners:** Failures when sending audit events to external targets (HTTP webhooks, message queues) must **never** roll back the user's authentication transaction or break the login flow. Wrap outbound operations in robust `try/catch` and log errors cleanly.
* **HTTP Clients:** Always use Java 21 native `java.net.http.HttpClient` with strict connection timeouts and read timeouts. Never block the event thread indefinitely.

### 3.4. Dependency Constraints
* All Keycloak dependencies (`keycloak-core`, `keycloak-server-spi`, etc.) must have `<scope>provided</scope>` because Keycloak provides them at runtime.
* Avoid embedding heavy third-party libraries into extension JARs to prevent classloader conflicts with Quarkus. Prefer Java standard library APIs.
* **Dependabot Group Classification:** Whenever adding or updating an external library, test dependency, or Maven plugin in any `pom.xml`, verify `.github/dependabot.yml`. Ensure its package pattern is explicitly registered in the appropriate group (`maven-plugins`, `test-dependencies`, `keycloak-runtime`) rather than defaulting to the `other-dependencies` catch-all.

---

## 4. Git & Release Workflow (GitHub Flow)

1. **Branches:**
   * `main`: Always production-ready, clean, and tested.
   * `feat/<name>`: For new SPIs or enhancements.
   * `fix/<name>`: For bug fixes.
   * `docs/<name>`: For documentation updates.
   * `chore/<name>`: For maintenance, dependency updates, and configuration tasks.
2. **Pull Requests:**
   * Every PR triggers `.github/workflows/ci.yml`.
   * All CI checks must pass before merging.
3. **Releases & Publishing:**
   * Releases are **strictly published from `main`**. Feature branches never publish artifacts.
   * Pushing a version tag on `main` (e.g., `v1.0.0`) triggers `.github/workflows/release.yml`, which compiles all submodule JARs, generates release notes, and attaches the binary assets to a GitHub Release.

---

## 5. Documentation & Changelog Standards (Strictly Enforced)

Any agent or developer modifying or adding code MUST maintain documentation integrity:
1. **`CHANGELOG.md`:** Every PR/change that introduces a feature, fix, or breaking change must add an entry under `[Unreleased]` following the [Keep a Changelog](https://keepachangelog.com/) standard (`Added`, `Changed`, `Fixed`, `Removed`).
2. **Submodule `README.md`:** Every extension inside `extensions/<category>/<name>/` must have its own standalone `README.md` containing:
   * Architecture overview and flow.
   * Configuration options (SPI config properties and environment variables `KC_SPI_...`).
   * Event/claim examples (JSON schemas).
   * Keycloak installation and verification instructions.
3. **Root `README.md` Catalog:** When a new extension is created, its status in the root `README.md` catalog table must be updated (e.g., from Planned to In Development or Released).
4. **Code-Level Documentation:** All public methods, `ProviderFactory` implementations, and configuration parameters must contain JavaDoc explaining Keycloak-specific lifecycles and thread-safety considerations.

---

## 6. Task Tracking & State Management

* **Private Task Tracking:** A local `ROADMAP.md` file (ignored by Git via `.gitignore`) maintains the granular progress checklist. Any agent in a new session must read `ROADMAP.md` to identify current progress and next items.
* **Current Development Priority:** Session 2 — `extensions/authenticators/breached-password` (Breached Password Detector Authenticator SPI).

---

## 7. Autonomous Session Kickoff Protocol (Zero-Slash Native Flow)

When starting a new session or implementing a new extension:
1. **Context & State Sync:** Read `ROADMAP.md` and the matching `.plan/<session>.md` to identify scope, requirements, and acceptance criteria.
2. **Native Architectural Interview:** Proactively analyze critical design decisions, trade-offs, and fallbacks (e.g. fail-open vs. fail-close, timeout thresholds, error mappings). Ask clarifying questions to align with the developer before generating code.
3. **Phased Plan Presentation:** Present a concise, phased implementation plan artifact before executing changes.
4. **Autonomous Execution:** Orchestrate the workflow seamlessly using project skills (`keycloak-spi-scaffolder` for module setup, `keycloak-spi-reviewer` for audit, and `ci-pr-manager` for PR validation).

