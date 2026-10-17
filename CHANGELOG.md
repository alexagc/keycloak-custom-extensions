# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Configured sequential Dependabot grouping rules in `.github/dependabot.yml` (`maven-plugins`, `test-dependencies`, `keycloak-runtime`, and `other-dependencies` catch-all) preventing unmanaged branch proliferation.
- Established architectural standard in `AGENTS.md` (Section 3.4) requiring explicit Dependabot group classification when adding or updating dependencies.
- Credential sanitization utility (`CredentialSanitizer`) in `extensions/event-listeners/webhook` masking sensitive keys (`password`, `secret`, `token`, `totp`, etc.) in event details and JSON representations.
- Cryptographic anti-replay nonce validation in `extensions/authenticators/age-identity-proofing`.
- Shared singleton prefix cache (`HibpPrefixCache.getOrCreateShared`) unified across `BreachedPasswordAuthenticatorFactory` and `BreachedPasswordPolicyProviderFactory`.

### Changed
- Consolidated full project dependency and plugin updates to latest available versions:
  - `keycloak` runtime artifacts bumped from `26.8.0` to `26.8.1` (including SMTP StartTLS security patch CVE-2026-107121).
  - `org.apache.kafka:kafka-clients` bumped from `3.9.2` to `4.4.0` in shaded event listener module.
  - `testcontainers` bumped from `1.20.4` to `1.21.4` (latest in 1.x line ensuring binary compatibility with `testcontainers-keycloak:4.4.0`).
  - `org.mockito:mockito-core` and `mockito-junit-jupiter` bumped from `5.14.2` to `5.24.0`.
  - `slf4j` bumped from `2.0.16` to `2.0.20`.
  - `maven-compiler-plugin` bumped from `3.13.0` to `3.16.0`.
  - `maven-jar-plugin` bumped from `3.4.2` to `3.5.1`.
  - `maven-shade-plugin` bumped from `3.6.0` to `3.6.2`.
  - `pitest-maven` bumped from `1.18.2` to `1.30.0`.
  - `google-java-format` code formatting engine bumped from `1.25.0` to `1.37.0`.
  - Updated Keycloak container image in all E2E Testcontainers integration tests to `quay.io/keycloak/keycloak:26.8.1`.
  - `actions/dependency-review-action` bumped from `v4` to `v5`.
  - `maven-surefire-plugin` and `maven-failsafe-plugin` bumped from `3.5.1` to `3.6.0`.
  - `maven-javadoc-plugin` bumped from `3.11.2` to `3.12.0`.
  - `jacoco-maven-plugin` bumped from `0.8.12` to `0.8.15`.
  - `pitest-junit5-plugin` bumped from `1.2.2` to `1.2.3`.
  - `spotless-maven-plugin` bumped from `2.44.3` to `3.10.4`.
  - `jboss-logging` bumped from `3.6.1.Final` to `3.6.3.Final`.
  - `junit-jupiter` bumped from `5.11.2` to `6.1.3`.
  - `mockserver-client-java` bumped from `5.15.0` to `8.0.0`.
- Optimized disposable email subdomain scanning in `extensions/validators/user-profile-rules` from linear scan $O(N)$ to $O(k)$ set lookup of parent domain labels.
- Optimized `MinimumAgeValidatorProvider` to reuse `DateTimeFormatter.ISO_LOCAL_DATE` and cache custom formatters.
- Modernized SHA-256 hex encoding in `age-identity-proofing` and `adaptive-risk` using Java 21 `HexFormat.of().formatHex(...)`.
- Enforced graceful `HttpClient.close()` on provider factory shutdown across `webhook`, `breached-password`, and related factories.

### Fixed
- Fixed Mermaid diagram syntax in `docs/ARCHITECTURE.md` and upgraded it with the complete Keycloak 26.x Quarkus SPI lifecycle architecture.
- Fixed critical reverse proxy IP evaluation order in `extensions/authenticators/distributed-rate-limiter` (`resolveClientIp` prioritizing `CF-Connecting-IP`, `X-Real-IP`, `X-Forwarded-For` over socket connection remoteAddr), preventing global DoS in Kubernetes, Cloudflare, and NGINX deployments.
- Fixed critical FIFO eviction bug in `extensions/authenticators/adaptive-risk` (`DeviceFingerprintEvaluator.recordDeviceFingerprint`), evicting oldest fingerprints from head rather than discarding newest devices when capacity exceeds 10.
- Fixed representation exposure in `extensions/event-listeners/kafka`, respecting `includeRepresentation` flag in `CloudEventsRecordMapper.toAdminCloudEvent`.

## [1.0.0] - 2026-10-15
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
- Zero-Trust Adaptive Risk Evaluator SPI (`extensions/authenticators/adaptive-risk`) implementing continuous contextual risk evaluation (CARTA):
  - `AdaptiveRiskAuthenticator`: Evaluates contextual risk factors (impossible travel velocity, IP reputation, device anomalies) to trigger dynamic Step-Up MFA challenges or preventive blocks.
  - `AdaptiveRiskAuthenticatorFactory`: Singleton lifecycle manager with compile-time `@AutoService` registration and Keycloak Admin Console SPI configuration properties.
  - `ImpossibleTravelCalculator`: Great-circle Haversine distance and transit velocity calculator flagging supersonic velocity anomalies exceeding commercial flight limits (>900 km/h).
  - `IpReputationChecker`: High-performance in-memory detection of verified public TOR exit nodes and anonymous proxies with private/local network bypass.
  - `DeviceFingerprintEvaluator`: Privacy-preserving SHA-256 client fingerprint generator with automatic historical device recognition and eviction on the user model.
  - `GeoLocationResolver`: Resilient HTTP header resolution extracting client IP and geographic coordinates from Cloudflare and standard reverse proxy headers (`CF-Connecting-IP`, `X-Forwarded-For`, `CF-IPLatitude`, `CF-IPLongitude`).
  - `RiskScoringEngine`: Normalized composite risk aggregator (0-100) setting standardized authentication and user session notes (`adaptive_risk_score`, `adaptive_risk_level`, `adaptive_risk_step_up`).
  - Responsive FreeMarker challenge UI template (`adaptive-risk-challenge.ftl`) with multi-language i18n support (English and Spanish).
  - Comprehensive unit test suite (`ImpossibleTravelCalculatorTest`, `IpReputationCheckerTest`, `DeviceFingerprintEvaluatorTest`, `GeoLocationResolverTest`, `RiskScoringEngineTest`, `AdaptiveRiskAuthenticatorTest`, `AdaptiveRiskAuthenticatorFactoryTest`) and Testcontainers E2E integration test (`AdaptiveRiskE2EIT`).
- Enterprise Kafka Audit Streamer SPI (`extensions/event-listeners/kafka`) implementing asynchronous, non-blocking audit event streaming to Apache Kafka:
  - `KafkaEventListenerProvider`: Asynchronous event listener translating user authentication and realm administration events into CloudEvents 1.0 JSON payloads.
  - `KafkaEventListenerProviderFactory`: Singleton lifecycle manager with `@AutoService` registration, SPI configuration parsing, and graceful producer shutdown.
  - `KafkaProducerManager`: Thread-safe Kafka producer manager with non-blocking dispatch (`max.block.ms = 500`) and background retry draining.
  - `BoundedRetryRingBuffer`: Resilient, bounded in-memory buffer (5000 records capacity with FIFO drop) ensuring zero blocking and fail-open tolerance during broker degradation.
  - `TopicResolver`: Dynamic realm routing supporting `{realm}` interpolation for multi-tenant topic isolation (e.g. `keycloak.events.{realm}` and `keycloak.admin-events.{realm}`).
  - `CredentialSanitizer`: Automatic recursive masking (`******`) of sensitive credentials (passwords, tokens, client secrets, TOTP codes) in event details and admin JSON representations.
  - `CloudEventsRecordMapper`: Standard CloudEvents 1.0 JSON serialization (`specversion: 1.0`, canonical types, ISO-8601 UTC timestamps).
  - Configurable semantic partitioning strategy (`USER` by user ID for strict chronological order, `REALM` by realm ID, `NONE` for round-robin).
  - Bundled with `org.apache.kafka:kafka-clients` shaded with package relocation (`io.github.alexc.keycloak.events.kafka.shaded.kafka`) and `slf4j-api` excluded to avoid Quarkus classloader conflicts.
  - Comprehensive unit test suite (`TopicResolverTest`, `CredentialSanitizerTest`, `CloudEventsRecordMapperTest`, `BoundedRetryRingBufferTest`, `KafkaProducerManagerTest`, `KafkaEventListenerProviderTest`, `KafkaEventListenerProviderFactoryTest`) and Testcontainers E2E integration test (`KafkaEventListenerE2EIT`).
- Antigravity workflow optimizations including modular directory rules (`extensions/AGENTS.md`), project skills (`keycloak-spi-scaffolder`, `ci-pr-manager`), and subagent blueprints.
- Production Deployment Guide (`docs/DEPLOYMENT.md`) covering Docker Compose multi-container stack (Keycloak, Postgres, Valkey, Kafka KRaft) and Kubernetes Helm deployment with automated provider download, checksum verification, and HA clustering.

### Changed
- Modernized GitHub Actions workflows to eliminate Node.js 20 deprecation warnings (`actions/checkout@v7`, `actions/setup-java@v6`, `github/codeql-action/*@v4`, `softprops/action-gh-release@v3`).
- Configured `-XX:+EnableDynamicAgentLoading` in Maven Surefire and Failsafe plugins to prevent Java 21 ByteBuddy dynamic agent loading warnings during test runs.

### Fixed
- Canonicalized Git author mappings via `.mailmap` to consolidate legacy initial scaffold commits under a single canonical identity.

[Unreleased]: https://github.com/alexagc/keycloak-custom-extensions/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/alexagc/keycloak-custom-extensions/releases/tag/v1.0.0


