# Antigravity Agent Configuration (`.agents/`)

This directory contains the skills, rules, and subagent blueprints tailored for developing Keycloak 26.x SPIs in this repository.

---

## 🛠️ Skills (`.agents/skills/`)

1. **[`keycloak-spi-reviewer`](keycloak-spi-reviewer/SKILL.md):**
   * Comprehensive architectural and quality auditor for Keycloak SPIs.
   * Enforces non-blocking execution, zero-leak lifecycles, and `@AutoService` registration.
2. **[`keycloak-spi-scaffolder`](keycloak-spi-scaffolder/SKILL.md):**
   * Rapidly scaffolds new SPI submodules following the Maven multi-module architecture, Keycloak 26.8.0 provided scopes, and Testcontainers E2E test blueprints.
3. **[`ci-pr-manager`](ci-pr-manager/SKILL.md):**
   * Manages the Zero-Install PR workflow: status check monitoring, JaCoCo coverage analysis, and GraphQL resolution of CodeQL bot review threads.

---

## 🤖 Subagents

The following subagent archetypes are designed for this repository:

* **`spi-auditor` (Read-only):**
  * Dedicated code auditor that inspects SPI diffs against the `keycloak-spi-reviewer` checklist and returns structured verdicts (`PASSED` / `CHANGES_REQUIRED`).
* **`ci-watcher` (CLI execution):**
  * Asynchronously monitors GitHub Actions workflow runs (`gh run watch`, `gh pr checks`) and reports build status without blocking interactive coding turns.

---

## 📏 Directory Rules

* Root **`AGENTS.md`**: Master repository guidelines (Zero-install policy, Git workflow, Definition of Done).
* **`extensions/AGENTS.md`**: Scoped rules applied automatically to all SPI submodules (Provider/Factory separation, native HTTP client timeouts, defensive exception handling).
