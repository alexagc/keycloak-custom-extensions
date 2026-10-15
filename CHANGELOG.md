# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Initial project foundation and Maven multi-module root POM.
- Zero-install workflow guidelines and Dev Container for Codespaces.
- GitHub Actions CI/CD pipelines for validation and automated releases.
- Universal AI & developer guidelines (`AGENTS.md`).
- Issue and Pull Request templates.
- Webhook Event Listener SPI (`extensions/event-listeners/webhook`) implementing non-blocking asynchronous event delivery via Java 21 `HttpClient`.
- Updated Keycloak target to `26.8.0` and Testcontainers to `1.20.4`.
- Dependabot configuration for automated weekly dependency updates for Maven and GitHub Actions (`.github/dependabot.yml`).
- Weekly and on-demand PITest mutation testing workflow (`.github/workflows/mutation-testing.yml`).
- Automated CI test & coverage reporting via GitHub Step Summary and auto-updating sticky Pull Request comments.
- Build artifact archiving for JaCoCo coverage HTML and Surefire test reports in CI.
- GitHub Dependency Review action (`.github/workflows/dependency-review.yml`) blocking vulnerable dependencies.
- Automated SHA-256 checksum generation (`checksums.sha256`) for all released binary artifacts in `release.yml`.
- Configured Spotless plugin for Google Java Format in DevContainer and manual code formatting.
- Integrated `javadoc:jar` validation in CI pipeline to verify documentation integrity.
- Status badges for CI, CodeQL, Keycloak 26.x, Java 21 LTS, and Apache 2.0 in `README.md`.
- Declarative User Profile Rules SPI (`extensions/validators/user-profile-rules`) featuring three decoupled validators:
  - `minimum-age`: Legal majority / GDPR age verification with configurable `min-age` and `date-format`.
  - `disposable-email`: In-memory blocker for disposable and throwaway email domains preloaded from embedded resource with zero network latency.
  - `national-id`: Multi-country identity document validator using the Strategy Pattern with official mathematical checksums for Spain (DNI, NIE, CIF mod 23), Italy (Codice Fiscale mod 26), France (NIR mod 97), Brazil (CPF mod 11), United States (SSN SSA ranges), and Germany (Steuer-ID ISO 7064 MOD 11, 10), supporting fixed country or dynamic user profile attribute resolution (`attribute:<field>`).
- Breached Password Detector SPI (`extensions/authenticators/breached-password`) implementing HaveIBeenPwned k-Anonymity protection (NIST SP 800-63B, PCI-DSS v4.0, DORA):
  - `BreachedPasswordAuthenticator`: Real-time compromised credential detection in Keycloak browser authentication flows with `strict` and `audit-only` modes.
  - `BreachedPasswordPolicyProvider`: Preventative password policy checking in user registration, reset credentials, and profile management flows.
  - `CheckBreachedPasswordAction`: Seamless required action enforcement (`UPDATE_PASSWORD`) without session termination.
  - Native asynchronous Java 21 `HttpClient` with side-channel mitigation (`Add-Padding: true`), prefix LRU cache with TTL, and fail-open resilience.
  - Comprehensive unit test suite (JUnit 5 + Mockito) and Testcontainers E2E integration test (`BreachedPasswordE2EIT`) against Keycloak 26.x and MockServer.
- Age & Identity Proofing SPI (`extensions/authenticators/age-identity-proofing`) implementing eIDAS 2.0 / OpenID4VP / eID gateway verification with Zero-PII privacy architecture:
  - `AgeProofingAuthenticator`: Authentication flow step supporting conditional step-up authentication with responsive FreeMarker challenge UI (OpenID4VP QR code and public eID gateway redirect).
  - `AgeProofingRequiredAction`: Post-login onboarding and account compliance action with O(1) trigger evaluation.
  - Zero-PII storage policy: Stores only SHA-256 evidence digest (`age_proof_hash`), issuer identifier (`age_proof_issuer`), and timestamp (`age_verified_at`) in compliance with GDPR Articles 5 and 25.
  - Native cryptographic validation for JWS assertions (RS256/384/512, ES256/384/512) and in-memory JWKS public key cache using Java 21 `HttpClient`.
  - Comprehensive unit test suite (`PublicTrustProviderResolverTest`, `VerifiablePresentationValidatorTest`, `AgeProofingAuthenticatorTest`, `AgeProofingRequiredActionTest`) and Testcontainers E2E integration test (`AgeProofingE2EIT`).
- Distributed Rate Limiter SPI (`extensions/authenticators/distributed-rate-limiter`) implementing distributed sliding window protection over Valkey and Redis:
  - `DistributedRateLimiterAuthenticator`: Dual sliding window protection enforcing client IP limits perimetrally and targeted username credential stuffing limits before relational DB queries.
  - `DistributedRateLimiterAuthenticatorFactory`: Singleton lifecycle manager with automatic `@AutoService` registration and full Keycloak Admin Console configuration properties.
  - Atomic Lua script execution inside Valkey/Redis managing `ZSET` sliding window timestamps, TTL refreshes, and precise `Retry-After` seconds calculation.
  - Bundled with official Linux Foundation `io.valkey:valkey-java` (5.5.0) shaded with package relocation to prevent Quarkus classloader conflicts.
  - RFC-compliant HTTP 429 Too Many Requests response handling with `Retry-After` headers for both REST API JSON clients and Keycloak browser UI error pages.
  - Comprehensive unit test suite (`RateLimiterConfigTest`, `SlidingWindowScriptTest`, `ValkeyRateLimiterClientTest`, `DistributedRateLimiterAuthenticatorTest`, `DistributedRateLimiterAuthenticatorFactoryTest`) and Testcontainers E2E integration test (`DistributedRateLimiterE2EIT`).
- Antigravity workflow optimizations including modular directory rules (`extensions/AGENTS.md`), project skills (`keycloak-spi-scaffolder`, `ci-pr-manager`), and subagent blueprints.

### Changed
- Modernized GitHub Actions workflows to eliminate Node.js 20 deprecation warnings (`actions/checkout@v7`, `actions/setup-java@v6`, `github/codeql-action/*@v4`, `softprops/action-gh-release@v3`).
- Configured `-XX:+EnableDynamicAgentLoading` in Maven Surefire and Failsafe plugins to prevent Java 21 ByteBuddy dynamic agent loading warnings during test runs.

### Fixed
- Canonicalized Git author mappings via `.mailmap` to consolidate legacy initial scaffold commits under a single canonical identity.


