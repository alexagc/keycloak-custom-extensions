# Keycloak Custom Extensions

[![CI Pipeline](https://github.com/alexc/keycloak-custom-extensions/actions/workflows/ci.yml/badge.svg)](https://github.com/alexc/keycloak-custom-extensions/actions/workflows/ci.yml)
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
| **`event-listeners`** | `webhook` | `EventListenerProvider` | Dispatches Keycloak audit and admin events asynchronously to configurable HTTP Webhooks. | 🟡 In Development |
| **`protocol-mappers`** | `dynamic-claims` | `OIDCAttributeMapper` | Injects calculated or external metadata claims into JWT access tokens. | ⚪ Planned |
| **`authenticators`** | `email-domain` | `Authenticator` | Routes users automatically to federated IdPs based on email domain. | ⚪ Planned |

---

## 📦 How to Install Extensions in Keycloak

Keycloak on Quarkus installs extensions by copying compiled `.jar` files into the `/opt/keycloak/providers/` folder.

### 1. Direct Download (Docker example)
```dockerfile
FROM quay.io/keycloak/keycloak:26.0.0 as builder

# Download the desired extension JAR directly from GitHub Releases
ADD https://github.com/alexc/keycloak-custom-extensions/releases/latest/download/keycloak-event-listener-webhook-1.0.0.jar /opt/keycloak/providers/

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
