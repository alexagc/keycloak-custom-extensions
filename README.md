# Keycloak Custom Extensions

[![CI Pipeline](https://github.com/alexagc/keycloak-custom-extensions/actions/workflows/ci.yml/badge.svg)](https://github.com/alexagc/keycloak-custom-extensions/actions/workflows/ci.yml)
[![CodeQL](https://github.com/alexagc/keycloak-custom-extensions/actions/workflows/codeql.yml/badge.svg)](https://github.com/alexagc/keycloak-custom-extensions/actions/workflows/codeql.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Keycloak](https://img.shields.io/badge/Keycloak-26.x_(Quarkus)-red.svg)](https://www.keycloak.org/)
[![Java](https://img.shields.io/badge/Java-21_LTS-orange.svg)](https://adoptium.net/)

An enterprise-ready, modular suite of Service Provider Interfaces (SPIs) designed for modern **Keycloak 26.x (Quarkus runtime)**.

---

## 🎯 Purpose & Architecture

While configuring Keycloak via admin consoles or Helm charts is common, complex enterprise platforms require extending Keycloak's core lifecycle through custom SPIs. 

This repository provides production-tested, clean, and decoupled extensions organized hierarchically by SPI interface type.

### Extensions Catalog

| Category | Extension | Keycloak SPI | Description | Status |
| :--- | :--- | :--- | :--- | :--- |
| **`event-listeners`** | [`webhook`](extensions/event-listeners/webhook/) | `EventListenerProvider` | Dispatches Keycloak audit and admin events asynchronously to configurable HTTP Webhooks. | 🟢 Ready |
| **`validators`** | [`user-profile-rules`](extensions/validators/user-profile-rules/) | `ValidatorFactory` | Declarative User Profile rules: minimum age check, disposable email blocker, and global national ID validation. | 🟢 Ready |
| **`authenticators`** | `breached-password` | `Authenticator` | Real-time breached password detector using HaveIBeenPwned k-Anonymity API (NIST SP 800-63B / PCI-DSS 4.0). | ⚪ Planned |
| **`authenticators`** | `age-identity-proofing` | `Authenticator` | Privacy-preserving age verification challenge with public eID / eIDAS 2.0 / OpenID4VP gateways. | ⚪ Planned |
| **`authenticators`** | `distributed-rate-limiter` | `Authenticator` | Cluster-wide anti-brute-force rate limiter on Redis Sliding Window Counter for Kubernetes multi-pod setups. | ⚪ Planned |
| **`authenticators`** | `adaptive-risk` | `Authenticator` | Zero-Trust Impossible Travel velocity evaluator, IP reputation, and dynamic Step-Up MFA challenge. | ⚪ Planned |
| **`event-listeners`** | `kafka` | `EventListenerProvider` | Enterprise audit & security event streaming to Apache Kafka with user/realm partitioning. | ⚪ Planned |

---

## 📦 How to Install Extensions in Keycloak

Keycloak on Quarkus installs extensions by copying compiled `.jar` files into the `/opt/keycloak/providers/` folder.

### 1. Direct Download (Docker example)
```dockerfile
FROM quay.io/keycloak/keycloak:26.8.0 as builder

# Download the desired extension JAR directly from GitHub Releases
ADD https://github.com/alexagc/keycloak-custom-extensions/releases/latest/download/keycloak-event-listener-webhook-1.0.0.jar /opt/keycloak/providers/

# Re-build Keycloak with the custom provider
RUN /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start-dev"]
```

### 2. Kubernetes / Helm Chart
Mount the `.jar` into `/opt/keycloak/providers` using an `initContainer` or a custom container image as shown above.

---

## 🛠️ Development & Zero-Install Philosophy

This repository is designed with a **Zero-Install Local Policy**:
* Developers do not need Java, Maven, or Docker installed on their local machines.
* All validation, builds, and integration tests execute automatically in **GitHub Actions**.
* For full cloud development with zero setup, launch this repository in **GitHub Codespaces**.

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for contribution workflows and details.

---

## 📚 Documentation
* [Architecture & SPI Lifecycle](docs/ARCHITECTURE.md): Deep dive into Keycloak Quarkus SPI mechanics.
* [Development Guide](docs/DEVELOPMENT.md): GitHub Flow, Codespaces, and testing.
* [Contributing](CONTRIBUTING.md): Code of conduct and contribution guidelines.

---

## 📄 License
Distributed under the Apache 2.0 License. See [LICENSE](LICENSE) for details.
