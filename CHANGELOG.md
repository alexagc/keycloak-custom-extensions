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
- Bound `spotless:check` to `compile` phase in Maven build to enforce Google Java Format.
- Integrated `javadoc:jar` validation in CI pipeline to verify documentation integrity.
- Status badges for CI, CodeQL, Keycloak 26.x, Java 21 LTS, and Apache 2.0 in `README.md`.

### Changed
- Modernized GitHub Actions workflows to eliminate Node.js 20 deprecation warnings (`actions/checkout@v7`, `actions/setup-java@v6`, `github/codeql-action/*@v4`, `softprops/action-gh-release@v3`).
- Configured `-XX:+EnableDynamicAgentLoading` in Maven Surefire and Failsafe plugins to prevent Java 21 ByteBuddy dynamic agent loading warnings during test runs.


