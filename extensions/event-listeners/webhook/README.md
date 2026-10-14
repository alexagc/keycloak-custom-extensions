# Webhook Event Listener SPI for Keycloak

Dispatches Keycloak user authentication and administrative events in real-time to external HTTP/HTTPS webhooks asynchronously and resiliently.

---

## 🚀 Key Features

* **Non-Blocking Asynchronous Delivery:** Uses native Java 21 `HttpClient.sendAsync(...)` to ensure audit event dispatches never block or delay the user's login or registration transaction.
* **Fault-Tolerant:** Remote network or HTTP errors (timeouts, 500s) are caught and logged without disrupting Keycloak.
* **Configurable Filtering:** Filter specific user event types (`LOGIN`, `LOGOUT`, `REGISTER`, etc.) or include administrative events (`CREATE`, `UPDATE`, `DELETE`).
* **Secure Headers:** Supports custom `Authorization` headers (Bearer tokens, API keys).
* **Zero External Dependencies:** Built with pure Keycloak SPI and Java 21 standard library APIs.

---

## ⚙️ Configuration Options

Configure the provider using standard Keycloak Quarkus configuration formats (either CLI arguments, `conf/keycloak.conf`, or environment variables).

| Parameter | Environment Variable | Default | Description |
| :--- | :--- | :--- | :--- |
| `target-url` | `KC_SPI_EVENTS_LISTENER_WEBHOOK_TARGET_URL` | *None* (Required) | Destination HTTP/HTTPS endpoint to receive POST events. |
| `authorization-header` | `KC_SPI_EVENTS_LISTENER_WEBHOOK_AUTHORIZATION_HEADER` | *None* | Optional Authorization header (e.g. `Bearer <token>` or `ApiKey <key>`). |
| `include-events` | `KC_SPI_EVENTS_LISTENER_WEBHOOK_INCLUDE_EVENTS` | `*` (All) | Comma-separated list of event types to forward (e.g. `LOGIN,LOGIN_ERROR,LOGOUT,REGISTER`). |
| `include-admin-events` | `KC_SPI_EVENTS_LISTENER_WEBHOOK_INCLUDE_ADMIN_EVENTS` | `true` | Whether to forward administrative operations. |
| `connect-timeout-ms` | `KC_SPI_EVENTS_LISTENER_WEBHOOK_CONNECT_TIMEOUT_MS` | `3000` | Connection timeout in milliseconds. |
| `read-timeout-ms` | `KC_SPI_EVENTS_LISTENER_WEBHOOK_READ_TIMEOUT_MS` | `5000` | HTTP request read timeout in milliseconds. |

---

## 📋 Event Payloads (JSON Schema Examples)

### 1. User Audit Event Payload
Sent with HTTP header `X-Keycloak-Event-Category: user`
```json
{
  "eventId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "time": 1728987654321,
  "type": "LOGIN",
  "realmId": "my-enterprise-realm",
  "realmName": "Production Realm",
  "clientId": "web-portal",
  "userId": "99f8e7d6-5c4b-3a21-0fed-cba987654321",
  "sessionId": "a1b2c3d4-e5f6-7890-1234-56789abcdef0",
  "ipAddress": "192.168.1.100",
  "error": null,
  "details": {
    "auth_method": "openid-connect",
    "username": "alex.gomez",
    "redirect_uri": "https://portal.company.com/callback"
  }
}
```

### 2. Administrative Event Payload
Sent with HTTP header `X-Keycloak-Event-Category: admin`
```json
{
  "eventId": "f1e2d3c4-b5a6-7890-fedc-ba9876543210",
  "time": 1728987699999,
  "realmId": "my-enterprise-realm",
  "realmName": "Production Realm",
  "authRealmId": "master",
  "authClientId": "admin-cli",
  "authUserId": "00000000-0000-0000-0000-000000000001",
  "authIpAddress": "10.0.0.1",
  "operationType": "CREATE",
  "resourceType": "USER",
  "resourcePath": "users/99f8e7d6-5c4b-3a21-0fed-cba987654321",
  "representation": "{\"username\":\"new.employee\",\"email\":\"new.employee@company.com\",\"enabled\":true}",
  "error": null
}
```

---

## 🛠️ How to Install and Activate

### Step 1: Copy JAR into Keycloak
Place the compiled `keycloak-event-listener-webhook-1.0.0.jar` into `/opt/keycloak/providers/`.

### Step 2: Enable the Event Listener in the Realm
1. Log in to the Keycloak Admin Console.
2. Navigate to **Realm Settings** -> **Events** tab.
3. In **Event Listeners**, add `webhook` to the list.
4. Click **Save**.

### Docker Example with Environment Variables:
```bash
docker run -d --name keycloak \
  -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -e KC_SPI_EVENTS_LISTENER_WEBHOOK_TARGET_URL=https://webhook.site/your-uuid \
  -e KC_SPI_EVENTS_LISTENER_WEBHOOK_AUTHORIZATION_HEADER="Bearer my-secret" \
  -v ./keycloak-event-listener-webhook-1.0.0.jar:/opt/keycloak/providers/keycloak-event-listener-webhook.jar \
  quay.io/keycloak/keycloak:26.8.0 \
  start-dev
```
