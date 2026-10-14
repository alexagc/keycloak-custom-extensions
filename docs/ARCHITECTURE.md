# Keycloak Extensions Architecture & SPI Guide

## 1. The Keycloak SPI Model in Quarkus

Since Keycloak 18+, Keycloak runs on **Quarkus** rather than the legacy WildFly application server. This brings significant performance improvements, faster startup times, and ahead-of-time (AOT) optimizations.

Extensions are built using Java's `ServiceLoader` standard, extended by Keycloak's component architecture.

```mermaid
graph TD
    KC[Keycloak Server Core] --> SPI[SPI Declaration]
    SPI --> Factory[ProviderFactory (Singleton / Server Scope)]
    Factory -->|creates per request/session| Provider[Provider Instance (Request/Session Scope)]
    Provider --> Execution[Execute Custom Logic]
```

### The Two Core Abstractions:
1. **`ProviderFactory<P extends Provider>`**:
   * Initialized once during server startup.
   * Reads SPI configuration via `Config.Scope`.
   * Instantiates request-scoped `Provider` instances.
   * Defines a unique identifier (`getId()`).
2. **`Provider`**:
   * Request/session lifecycle.
   * Receives `KeycloakSession` to access realms, users, clients, and transaction contexts.
   * Must release any resources in `close()`.

---

## 2. SPI Registration via `META-INF/services`

For Keycloak to discover an extension, the `.jar` **must** include a service provider configuration file in `META-INF/services/`:

* **Path:** `src/main/resources/META-INF/services/<Fully-Qualified-Factory-Interface>`
* **Content:** Fully qualified name of your factory implementation.

Example for an Event Listener:
`META-INF/services/org.keycloak.events.EventListenerProviderFactory`
```text
io.github.alexc.keycloak.events.webhook.WebhookEventListenerProviderFactory
```

---

## 3. Resilience Guidelines

Because Keycloak handles critical identity and authentication transactions, extensions must follow strict resilience standards:

1. **Non-blocking Event Handlers:** Event listeners execute synchronously during the event firing phase unless handled asynchronously. Slow external HTTP endpoints can delay login or exhaust connection pools.
2. **Safe Exception Handling:** Catch network or remote server exceptions. Never propagate unhandled runtime exceptions that could abort user logins unless that is the explicit security design (e.g., an Authenticator intentionally failing).
3. **Native HTTP Client:** Use Java 21's `java.net.http.HttpClient` with non-blocking async dispatch (`sendAsync`) and explicit connect/read timeouts.
