# Production Deployment Guide: Keycloak Custom Extensions

This guide provides enterprise operators and platform engineers with complete instructions for deploying, configuring, and maintaining Keycloak 26.x (Quarkus runtime) with the **Keycloak Custom Extensions** suite.

---

## 📑 Table of Contents

1. [Architecture & Topology Overview](#1-architecture--topology-overview)
2. [Extension Suite Inventory](#2-extension-suite-inventory)
3. [Provider Installation Patterns](#3-provider-installation-patterns)
4. [Docker Compose Deployment (Local / Staging)](#4-docker-compose-deployment-local--staging)
5. [Kubernetes & Helm Deployment (Production Enterprise)](#5-kubernetes--helm-deployment-production-enterprise)
6. [Complete SPI Configuration Reference](#6-complete-spi-configuration-reference)
7. [High Availability & Clustering](#7-high-availability--clustering)
8. [Zero-Downtime Upgrades & Rollbacks](#8-zero-downtime-upgrades--rollbacks)
9. [Observability & Health Checks](#9-observability--health-checks)

---

## 1. Architecture & Topology Overview

Modern Keycloak (Quarkus distribution) operates as an immutable container. Custom Service Provider Interfaces (SPIs) are loaded at server build time or mounted into the `/opt/keycloak/providers/` directory.

```
                           +-------------------------------------+
                           |      Ingress / Load Balancer        |
                           |   (TLS Termination, CF Headers)     |
                           +------------------+------------------+
                                              |
                   +--------------------------+--------------------------+
                   |                                                     |
       +-----------v-----------+                             +-----------v-----------+
       |   Keycloak Pod 1      | <--- Infinispan JGroups --->|   Keycloak Pod 2      |
       |  /opt/keycloak/       |         (KUBE_PING)         |  /opt/keycloak/       |
       |  providers/*.jar      |                             |  providers/*.jar      |
       +-----+-----+-----+-----+                             +-----+-----+-----+-----+
             |     |     |                                         |     |     |
             |     |     +------------------+   +------------------+     |     |
             |     |                        |   |                        |     |
             v     v                        v   v                        v     v
     +---------+ +---------+             +---------+                  +---------+
     | Postgres| | Valkey/ |             | Apache  |                  | Webhook |
     | DB 16+  | | Redis   |             | Kafka   |                  | Targets |
     |         | | Cluster |             | Cluster |                  | (SIEM/  |
     | (User/  | | (Rate   |             | (Audit  |                  | Slack)  |
     | Realm)  | | Limiter)|             | Stream) |                  |         |
     +---------+ +---------+             +---------+                  +---------+
```

---

## 2. Extension Suite Inventory

The following 7 modular SPI extensions are provided in the release bundle:

| Artifact Name | SPI Category | Keycloak Interface | Description |
| :--- | :--- | :--- | :--- |
| `keycloak-event-listener-webhook-1.0.0.jar` | `event-listeners` | `EventListenerProvider` | Asynchronous HTTP webhook dispatcher for audit and admin events. |
| `keycloak-validator-user-profile-rules-1.0.0.jar` | `validators` | `ValidatorProvider` | Minimum age (GDPR), disposable email blocker, and national ID checksums (ES, IT, FR, BR, US, DE). |
| `keycloak-authenticator-breached-password-1.0.0.jar` | `authenticators` | `Authenticator` / `PasswordPolicy` | Real-time compromised credential detection via HIBP k-Anonymity (NIST SP 800-63B). |
| `keycloak-authenticator-age-identity-proofing-1.0.0.jar` | `authenticators` | `Authenticator` / `RequiredAction` | eIDAS 2.0 / OpenID4VP / eID Gateway challenge with Zero-PII storage. |
| `keycloak-authenticator-distributed-rate-limiter-1.0.0.jar` | `authenticators` | `Authenticator` | Perimetral IP and credential-stuffing sliding window rate limiter over Valkey/Redis. |
| `keycloak-authenticator-adaptive-risk-1.0.0.jar` | `authenticators` | `Authenticator` | Zero-Trust Impossible Travel velocity, IP reputation, and dynamic MFA step-up. |
| `keycloak-event-listener-kafka-1.0.0.jar` | `event-listeners` | `EventListenerProvider` | CloudEvents 1.0 JSON audit streaming to Apache Kafka with dynamic realm topic routing. |

---

## 3. Provider Installation Patterns

### Pattern A: Optimized Multi-Stage Container Image (Recommended for Production)

Baking extensions into a custom container image guarantees reproducible deployments and executes the Quarkus build step during CI image creation rather than at container startup:

```dockerfile
# Multi-stage custom Keycloak image
FROM quay.io/keycloak/keycloak:26.8.0 AS builder

# Download release assets and checksums
ENV EXTENSION_VERSION=1.0.0
ENV RELEASE_URL=https://github.com/alexagc/keycloak-custom-extensions/releases/download/v${EXTENSION_VERSION}

USER root
RUN microdnf install -y curl ca-certificates && microdnf clean all

WORKDIR /opt/keycloak/providers

# Download all required extension JARs and checksums
RUN curl -sSLO "${RELEASE_URL}/keycloak-event-listener-webhook-${EXTENSION_VERSION}.jar" && \
    curl -sSLO "${RELEASE_URL}/keycloak-validator-user-profile-rules-${EXTENSION_VERSION}.jar" && \
    curl -sSLO "${RELEASE_URL}/keycloak-authenticator-breached-password-${EXTENSION_VERSION}.jar" && \
    curl -sSLO "${RELEASE_URL}/keycloak-authenticator-age-identity-proofing-${EXTENSION_VERSION}.jar" && \
    curl -sSLO "${RELEASE_URL}/keycloak-authenticator-distributed-rate-limiter-${EXTENSION_VERSION}.jar" && \
    curl -sSLO "${RELEASE_URL}/keycloak-authenticator-adaptive-risk-${EXTENSION_VERSION}.jar" && \
    curl -sSLO "${RELEASE_URL}/keycloak-event-listener-kafka-${EXTENSION_VERSION}.jar" && \
    curl -sSLO "${RELEASE_URL}/checksums.sha256" && \
    sha256sum -c checksums.sha256 && \
    rm checksums.sha256

# Pre-build Keycloak with optimized features and database driver
ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build

# Production runtime stage
FROM quay.io/keycloak/keycloak:26.8.0
COPY --from=builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
```

### Pattern B: Runtime InitContainer or Volume Mount

Operators deploying standard upstream Helm charts can mount downloaded `.jar` files into `/opt/keycloak/providers/`. Keycloak detects new JARs and executes an automatic build on startup before starting the server.

---

## 4. Docker Compose Deployment (Local / Staging)

Below is a complete, self-contained `docker-compose.yml` configuring Keycloak 26.x with PostgreSQL, Valkey, and Apache Kafka (KRaft mode).

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: keycloak-postgres
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: keycloak_db_password
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U keycloak"]
      interval: 5s
      timeout: 5s
      retries: 5
    networks:
      - keycloak-net

  valkey:
    image: valkey/valkey:7.2-alpine
    container_name: keycloak-valkey
    command: ["valkey-server", "--protected-mode", "no", "--save", ""]
    healthcheck:
      test: ["CMD", "valkey-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
    networks:
      - keycloak-net

  kafka:
    image: bashj79/kafka-kraft:latest
    container_name: keycloak-kafka
    environment:
      KRAFT_CONTAINER_HOST_NAME: kafka
      SHARE_DIR: /tmp/kraft-combined-logs
    healthcheck:
      test: ["CMD", "nc", "-z", "localhost", "9092"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - keycloak-net

  webhook-sink:
    image: mendhak/http-https-echo:34
    container_name: keycloak-webhook-sink
    environment:
      HTTP_PORT: 8080
    networks:
      - keycloak-net

  keycloak:
    image: quay.io/keycloak/keycloak:26.8.0
    container_name: keycloak-server
    command: ["start-dev"]
    depends_on:
      postgres:
        condition: service_healthy
      valkey:
        condition: service_healthy
      kafka:
        condition: service_healthy
    volumes:
      - ./providers:/opt/keycloak/providers:ro
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin_password
      KC_DB: postgres
      KC_DB_URL_HOST: postgres
      KC_DB_URL_DATABASE: keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: keycloak_db_password
      KC_HEALTH_ENABLED: "true"
      KC_METRICS_ENABLED: "true"
      KC_HTTP_RELATIVE_PATH: "/"

      # 1. Webhook Event Listener SPI
      KC_SPI_EVENTS_LISTENER_WEBHOOK_ENDPOINT_URL: "http://webhook-sink:8080/events"
      KC_SPI_EVENTS_LISTENER_WEBHOOK_AUTH_HEADER: "Bearer dev-webhook-secret"
      KC_SPI_EVENTS_LISTENER_WEBHOOK_EXCLUDED_EVENTS: "REFRESH_TOKEN,CODE_TO_TOKEN"

      # 2. Breached Password Detector SPI
      KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_AUTHENTICATOR_ENFORCEMENT_MODE: "strict"
      KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_AUTHENTICATOR_FAIL_OPEN: "true"
      KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_AUTHENTICATOR_CACHE_TTL_MINUTES: "60"

      # 3. Distributed Rate Limiter SPI
      KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_VALKEY_URI: "redis://valkey:6379"
      KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_IP_MAX_REQUESTS: "50"
      KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_IP_WINDOW_SECONDS: "60"
      KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_USER_MAX_REQUESTS: "5"
      KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_USER_WINDOW_SECONDS: "60"
      KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_FAIL_OPEN: "true"

      # 4. Zero-Trust Adaptive Risk Evaluator SPI
      KC_SPI_AUTHENTICATOR_ADAPTIVE_RISK_MAX_VELOCITY_KMH: "900"
      KC_SPI_AUTHENTICATOR_ADAPTIVE_RISK_RISK_THRESHOLD: "70"
      KC_SPI_AUTHENTICATOR_ADAPTIVE_RISK_STEP_UP_FLOW: "true"

      # 5. Kafka Audit Streamer SPI
      KC_SPI_EVENTS_LISTENER_KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
      KC_SPI_EVENTS_LISTENER_KAFKA_EVENTS_TOPIC: "keycloak.events.{realm}"
      KC_SPI_EVENTS_LISTENER_KAFKA_ADMIN_EVENTS_TOPIC: "keycloak.admin-events.{realm}"
      KC_SPI_EVENTS_LISTENER_KAFKA_PARTITIONING_STRATEGY: "USER"
      KC_SPI_EVENTS_LISTENER_KAFKA_FAIL_OPEN: "true"
    ports:
      - "8080:8080"
      - "9000:9000"
    networks:
      - keycloak-net

volumes:
  postgres_data:

networks:
  keycloak-net:
    driver: bridge
```

### Starting the Local Environment

1. Download the release `.jar` files into the `./providers` directory:
   ```bash
   mkdir -p providers
   curl -sSL https://github.com/alexagc/keycloak-custom-extensions/releases/download/v1.0.0/keycloak-event-listener-webhook-1.0.0.jar -o providers/keycloak-event-listener-webhook-1.0.0.jar
   # Repeat for desired extensions
   ```
2. Start the multi-container stack:
   ```bash
   docker compose up -d
   ```
3. Open `http://localhost:8080` and log in with `admin` / `admin_password`.

---

## 5. Kubernetes & Helm Deployment (Production Enterprise)

When deploying with the official or Bitnami Keycloak Helm Chart (`bitnami/keycloak` or `codecentric/keycloak`), leverage Kubernetes `initContainers` to download and cryptographically verify all SPIs prior to Keycloak booting.

### Helm `values.yaml` Example

```yaml
replicaCount: 3

image:
  repository: quay.io/keycloak/keycloak
  tag: 26.8.0

command:
  - "/opt/keycloak/bin/kc.sh"
  - "start"
  - "--optimized"

production: true
proxy: edge

podDisruptionBudget:
  minAvailable: 2

affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchLabels:
              app.kubernetes.io/name: keycloak
          topologyKey: "kubernetes.io/hostname"

resources:
  requests:
    cpu: 1000m
    memory: 1536Mi
  limits:
    cpu: 2000m
    memory: 2048Mi

# Automatic provider download and checksum verification
extraInitContainers:
  - name: fetch-custom-extensions
    image: alpine:3.20
    command: ["/bin/sh", "-c"]
    args:
      - |
        set -eu
        VERSION="1.0.0"
        BASE_URL="https://github.com/alexagc/keycloak-custom-extensions/releases/download/v${VERSION}"
        TARGET_DIR="/providers"

        apk add --no-cache curl ca-certificates coreutils

        echo "Downloading Keycloak custom extensions v${VERSION}..."
        curl -sSL "${BASE_URL}/checksums.sha256" -o "${TARGET_DIR}/checksums.sha256"
        
        EXTENSIONS="
          keycloak-event-listener-webhook-${VERSION}.jar
          keycloak-validator-user-profile-rules-${VERSION}.jar
          keycloak-authenticator-breached-password-${VERSION}.jar
          keycloak-authenticator-age-identity-proofing-${VERSION}.jar
          keycloak-authenticator-distributed-rate-limiter-${VERSION}.jar
          keycloak-authenticator-adaptive-risk-${VERSION}.jar
          keycloak-event-listener-kafka-${VERSION}.jar
        "

        cd "${TARGET_DIR}"
        for jar in $EXTENSIONS; do
          echo "Fetching ${jar}..."
          curl -sSL "${BASE_URL}/${jar}" -o "${jar}"
        done

        echo "Verifying SHA-256 integrity..."
        sha256sum -c checksums.sha256
        rm checksums.sha256
        echo "All extensions verified and ready."
    volumeMounts:
      - name: custom-providers
        mountPath: /providers

extraVolumes:
  - name: custom-providers
    emptyDir: {}

extraVolumeMounts:
  - name: custom-providers
    mountPath: /opt/keycloak/providers

# Environment variables from Secrets & ConfigMaps
extraEnvVars:
  - name: KC_DB
    value: "postgres"
  - name: KC_HEALTH_ENABLED
    value: "true"
  - name: KC_METRICS_ENABLED
    value: "true"

  # Webhook SPI Config
  - name: KC_SPI_EVENTS_LISTENER_WEBHOOK_ENDPOINT_URL
    valueFrom:
      configMapKeyRef:
        name: keycloak-spi-config
        key: WEBHOOK_ENDPOINT_URL
  - name: KC_SPI_EVENTS_LISTENER_WEBHOOK_AUTH_HEADER
    valueFrom:
      secretKeyRef:
        name: keycloak-spi-secrets
        key: WEBHOOK_AUTH_HEADER

  # Distributed Rate Limiter (Valkey / Redis)
  - name: KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_VALKEY_URI
    valueFrom:
      secretKeyRef:
        name: keycloak-spi-secrets
        key: VALKEY_URI
  - name: KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_IP_MAX_REQUESTS
    value: "100"
  - name: KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_IP_WINDOW_SECONDS
    value: "60"
  - name: KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_USER_MAX_REQUESTS
    value: "5"
  - name: KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_USER_WINDOW_SECONDS
    value: "60"

  # Kafka Audit Streamer
  - name: KC_SPI_EVENTS_LISTENER_KAFKA_BOOTSTRAP_SERVERS
    valueFrom:
      configMapKeyRef:
        name: keycloak-spi-config
        key: KAFKA_BOOTSTRAP_SERVERS
  - name: KC_SPI_EVENTS_LISTENER_KAFKA_EVENTS_TOPIC
    value: "keycloak.events.{realm}"
  - name: KC_SPI_EVENTS_LISTENER_KAFKA_PARTITIONING_STRATEGY
    value: "USER"

  # Breached Password Detector
  - name: KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_AUTHENTICATOR_ENFORCEMENT_MODE
    value: "strict"
  - name: KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_AUTHENTICATOR_FAIL_OPEN
    value: "true"

  # Adaptive Risk
  - name: KC_SPI_AUTHENTICATOR_ADAPTIVE_RISK_MAX_VELOCITY_KMH
    value: "900"
  - name: KC_SPI_AUTHENTICATOR_ADAPTIVE_RISK_RISK_THRESHOLD
    value: "70"
```

---

## 6. Complete SPI Configuration Reference

All extensions can be configured using environment variables conforming to the Quarkus SPI property naming standard:
`KC_SPI_<CATEGORY>_<PROVIDER_ID>_<PROPERTY_NAME>`

### 1. Webhook Event Listener (`event-listeners/webhook`)
| Environment Variable | Default | Description |
| :--- | :--- | :--- |
| `KC_SPI_EVENTS_LISTENER_WEBHOOK_ENDPOINT_URL` | *(required)* | Target HTTPS URL for audit events. |
| `KC_SPI_EVENTS_LISTENER_WEBHOOK_AUTH_HEADER` | *(none)* | Optional `Authorization` header (e.g. `Bearer <token>`). |
| `KC_SPI_EVENTS_LISTENER_WEBHOOK_CONNECT_TIMEOUT_MS` | `3000` | Connection timeout in milliseconds. |
| `KC_SPI_EVENTS_LISTENER_WEBHOOK_READ_TIMEOUT_MS` | `5000` | Socket read timeout in milliseconds. |
| `KC_SPI_EVENTS_LISTENER_WEBHOOK_EXCLUDED_EVENTS` | `REFRESH_TOKEN` | Comma-separated list of event types to ignore. |

### 2. Declarative User Profile Rules (`validators/user-profile-rules`)
Configured declaratively in the **Keycloak Admin Console > Realm Settings > User Profile**:
* `minimum-age`: Parameter `min-age` (integer, e.g. `18`), `date-format` (default `yyyy-MM-dd`).
* `disposable-email`: Built-in zero-latency blocklist with zero network overhead.
* `national-id`: Parameter `country` (e.g. `ES`, `IT`, `FR`, `BR`, `US`, `DE`) or dynamic attribute resolution (`attribute:countryCode`).

### 3. Breached Password Detector (`authenticators/breached-password`)
| Environment Variable | Default | Description |
| :--- | :--- | :--- |
| `KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_AUTHENTICATOR_API_URL` | `https://api.pwnedpasswords.com/range/` | HIBP k-Anonymity API endpoint. |
| `KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_AUTHENTICATOR_API_KEY` | *(none)* | Optional HIBP API commercial license key. |
| `KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_AUTHENTICATOR_ENFORCEMENT_MODE` | `strict` | `strict` (blocks compromised passwords) or `audit-only`. |
| `KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_AUTHENTICATOR_FAIL_OPEN` | `true` | Continues user flow if HIBP API times out or is unreachable. |
| `KC_SPI_AUTHENTICATOR_BREACHED_PASSWORD_AUTHENTICATOR_CACHE_TTL_MINUTES` | `60` | In-memory prefix LRU cache TTL in minutes. |

### 4. Age & Identity Proofing (`authenticators/age-identity-proofing`)
| Environment Variable | Default | Description |
| :--- | :--- | :--- |
| `KC_SPI_AUTHENTICATOR_AGE_IDENTITY_PROOFING_GATEWAY_URL` | *(none)* | OpenID4VP / eID Gateway authorization endpoint. |
| `KC_SPI_AUTHENTICATOR_AGE_IDENTITY_PROOFING_JWKS_URL` | *(none)* | Public JWKS URL for cryptographic assertion verification. |
| `KC_SPI_AUTHENTICATOR_AGE_IDENTITY_PROOFING_REQUIRED_MIN_AGE` | `18` | Minimum verified age required. |
| `KC_SPI_AUTHENTICATOR_AGE_IDENTITY_PROOFING_STORAGE_POLICY` | `ZERO_PII` | Stores only cryptographic hash digest and verification timestamp. |

### 5. Distributed Rate Limiter (`authenticators/distributed-rate-limiter`)
| Environment Variable | Default | Description |
| :--- | :--- | :--- |
| `KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_VALKEY_URI` | `redis://localhost:6379` | Valkey or Redis connection URI. |
| `KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_IP_MAX_REQUESTS` | `100` | Maximum requests allowed per IP window. |
| `KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_IP_WINDOW_SECONDS` | `60` | Sliding window duration in seconds for IP rate limiting. |
| `KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_USER_MAX_REQUESTS` | `5` | Maximum failed attempts per username window. |
| `KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_USER_WINDOW_SECONDS` | `60` | Sliding window duration in seconds for user credential protection. |
| `KC_SPI_AUTHENTICATOR_DISTRIBUTED_RATE_LIMITER_FAIL_OPEN` | `true` | Allows authentication if Valkey/Redis cluster is unavailable. |

### 6. Zero-Trust Adaptive Risk Evaluator (`authenticators/adaptive-risk`)
| Environment Variable | Default | Description |
| :--- | :--- | :--- |
| `KC_SPI_AUTHENTICATOR_ADAPTIVE_RISK_MAX_VELOCITY_KMH` | `900.0` | Maximum plausible travel velocity in km/h. |
| `KC_SPI_AUTHENTICATOR_ADAPTIVE_RISK_RISK_THRESHOLD` | `70` | Risk score threshold (0-100) triggering Step-Up MFA. |
| `KC_SPI_AUTHENTICATOR_ADAPTIVE_RISK_STEP_UP_FLOW` | `true` | Dynamically executes Step-Up MFA when risk threshold is met. |
| `KC_SPI_AUTHENTICATOR_ADAPTIVE_RISK_TOR_DETECTION_ENABLED` | `true` | Flag to evaluate public TOR exit nodes. |

### 7. Enterprise Kafka Audit Streamer (`event-listeners/kafka`)
| Environment Variable | Default | Description |
| :--- | :--- | :--- |
| `KC_SPI_EVENTS_LISTENER_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker bootstrap list. |
| `KC_SPI_EVENTS_LISTENER_KAFKA_EVENTS_TOPIC` | `keycloak.events.{realm}` | User authentication events topic (`{realm}` dynamic routing). |
| `KC_SPI_EVENTS_LISTENER_KAFKA_ADMIN_EVENTS_TOPIC` | `keycloak.admin-events.{realm}` | Administrative realm audit events topic. |
| `KC_SPI_EVENTS_LISTENER_KAFKA_PARTITIONING_STRATEGY` | `USER` | Partition key strategy: `USER` (chronological order), `REALM`, or `NONE`. |
| `KC_SPI_EVENTS_LISTENER_KAFKA_FAIL_OPEN` | `true` | Never blocks authentication transaction if Kafka brokers are degraded. |
| `KC_SPI_EVENTS_LISTENER_KAFKA_BUFFER_CAPACITY` | `5000` | In-memory ring buffer capacity for asynchronous dispatch. |

---

## 7. High Availability & Clustering

In a multi-pod Kubernetes or clustered deployment:

1. **Session & Cache Clustering:** Keycloak nodes discover each other using Infinispan via `KUBE_PING` or `DNS_PING`. The distributed cache coordinates sessions and user actions.
2. **State Synchronization:**
   - **Distributed Rate Limiter:** The Valkey/Redis instance is queried atomically via Lua scripts. All Keycloak pods share the exact same sliding window counter regardless of pod routing.
   - **Kafka Streamer:** Producers run independently per pod. Semantic partitioning on `USER` guarantees that user event chronological order is preserved across Kafka partitions.
   - **Adaptive Risk & Impossible Travel:** User login coordinates and device fingerprints are stored directly in user session notes and user attributes (`last_login_latitude`, `last_login_longitude`), ensuring continuous tracking regardless of which Keycloak pod handles the subsequent login.

---

## 8. Zero-Downtime Upgrades & Rollbacks

Keycloak on Quarkus supports rolling updates with zero downtime when using compatible schema versions:

### Rolling Upgrade Procedure
1. **Update the Container Image / InitContainer Tag:** Update the Helm chart value `VERSION: "1.1.0"` (or new image tag).
2. **Kubernetes Rolling Deployment:** Kubernetes spins up new pods one by one:
   - The `initContainer` fetches new extension JARs and verifies checksums.
   - Keycloak builds the optimized runtime and enters the cluster.
   - The readiness probe (`/health/ready`) verifies the pod is healthy before terminating old pods.
3. **Rollback Strategy:**
   - If an SPI issue arises, revert the Helm deployment (`helm rollback <release>`).
   - Old JARs will be re-mounted and Keycloak will reload previous providers without requiring database schema changes.

---

## 9. Observability & Health Checks

Keycloak Quarkus exposes standard MicroProfile Health endpoints on port `9000` (or `8080` if management port is disabled):

* **Liveness Probe:** `GET http://<pod-ip>:9000/health/live`
  - Validates container process responsiveness.
* **Readiness Probe:** `GET http://<pod-ip>:9000/health/ready`
  - Validates database connectivity, Infinispan cluster discovery, and Quarkus initialization.
* **Prometheus Metrics:** `GET http://<pod-ip>:9000/metrics`
  - Exposes JVM, garbage collection, HTTP connection pool, and thread metrics.
* **SPI Audit Logs:** All extensions utilize `org.jboss.logging.Logger` with structured logging. Logs can be collected via FluentBit, Vector, or Promtail into Elasticsearch or Grafana Loki.
