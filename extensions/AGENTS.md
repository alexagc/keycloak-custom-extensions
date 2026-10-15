# SPI Engineering & Architecture Rules (`extensions/`)

These instructions apply to all code, classes, and submodules residing under `extensions/`.

---

## 1. Provider & Factory Architecture
* **Factory as Singleton:** Must be annotated with `@AutoService(<Interface>Factory.class)`. Must never store request-scoped data or `KeycloakSession`.
* **Provider as Request/Session Scope:** Created per transaction via `Factory.create(KeycloakSession session)`. Clean up state in `close()`.
* **Statelessness:** Never use single-instance (`return this;`) in `ProviderFactory.create()` unless 100% stateless across all realms, users, and threads.

---

## 2. Fault Tolerance & Outbound I/O
* **Fail-Open Policy:** Outbound HTTP requests, external API checks (e.g. HIBP, webhooks), and remote message queues must NEVER crash user authentication flows or abort database transactions unless specifically designed as an intentional hard blocker. Wrap operations in robust `try/catch`.
* **Native HTTP Client:** Use `java.net.http.HttpClient` configured at factory initialization. Always specify explicit connection timeouts (e.g. 1.5s to 3s) and request timeouts (e.g. 3s to 5s).
* **Defensive Parsing:** Always wrap string-to-number or string-to-date conversions (`Integer.parseInt`, `LocalDate.parse`) in defensive `try/catch` to prevent uncaught runtime exceptions.

---

## 3. Dependency Scope & Classloading
* All Keycloak dependencies (`keycloak-core`, `keycloak-server-spi`, `keycloak-services`, etc.) must be marked `<scope>provided</scope>`.
* Never embed conflicting logging backends. Use `org.jboss.logging.Logger`.

---

## 4. Testcontainers Integration Tests
* All `*IT.java` integration tests must use `dasniko:testcontainers-keycloak` targeting `quay.io/keycloak/keycloak:26.8.0`.
* Tests must include `@EnabledIf("isDockerAvailable")` using `DockerClientFactory.instance().isDockerAvailable()` to allow zero-install environments to compile cleanly without Docker.
