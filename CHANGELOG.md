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
- Comprehensive unit test suite (JUnit 5 + Mockito) with boundary condition and mutation testing coverage, and Testcontainers End-to-End integration test (`UserProfileRulesE2EIT`) against Keycloak 26.x.
- Antigravity workflow optimizations including modular directory rules (`extensions/AGENTS.md`), project skills (`keycloak-spi-scaffolder`, `ci-pr-manager`), and subagent blueprints.

### Changed
- Modernized GitHub Actions workflows to eliminate Node.js 20 deprecation warnings (`actions/checkout@v7`, `actions/setup-java@v6`, `github/codeql-action/*@v4`, `softprops/action-gh-release@v3`).
- Configured `-XX:+EnableDynamicAgentLoading` in Maven Surefire and Failsafe plugins to prevent Java 21 ByteBuddy dynamic agent loading warnings during test runs.


