package io.github.alexc.keycloak.events.webhook;

import com.google.auto.service.AutoService;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Singleton factory responsible for lifecycle management, configuration loading,
 * and HTTP client initialization for the Webhook Event Listener SPI.
 */
@AutoService(EventListenerProviderFactory.class)
public class WebhookEventListenerProviderFactory implements EventListenerProviderFactory {

    private static final Logger LOG = Logger.getLogger(WebhookEventListenerProviderFactory.class);

    public static final String PROVIDER_ID = "webhook";

    public static final String CONFIG_TARGET_URL = "target-url";
    public static final String CONFIG_AUTH_HEADER = "authorization-header";
    public static final String CONFIG_INCLUDED_EVENTS = "include-events";
    public static final String CONFIG_INCLUDE_ADMIN_EVENTS = "include-admin-events";
    public static final String CONFIG_CONNECT_TIMEOUT_MS = "connect-timeout-ms";
    public static final String CONFIG_READ_TIMEOUT_MS = "read-timeout-ms";

    private WebhookEventListenerConfig config;
    private HttpClient httpClient;

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new WebhookEventListenerProvider(session, httpClient, config);
    }

    @Override
    public void init(Config.Scope configScope) {
        String targetUrlStr = resolveProperty(configScope, CONFIG_TARGET_URL, "KC_SPI_EVENTS_LISTENER_WEBHOOK_TARGET_URL");
        URI targetUri = null;
        if (targetUrlStr != null && !targetUrlStr.isBlank()) {
            try {
                targetUri = URI.create(targetUrlStr.trim());
                LOG.infov("Webhook Event Listener target endpoint initialized: {0}", targetUri);
            } catch (IllegalArgumentException e) {
                LOG.errorv(e, "Invalid target-url configured for webhook event listener: {0}", targetUrlStr);
            }
        } else {
            LOG.warn("Webhook Event Listener loaded without target-url; outbound webhook dispatch is disabled.");
        }

        String authHeader = resolveProperty(configScope, CONFIG_AUTH_HEADER, "KC_SPI_EVENTS_LISTENER_WEBHOOK_AUTHORIZATION_HEADER");

        String includedEventsStr = resolveProperty(configScope, CONFIG_INCLUDED_EVENTS, "KC_SPI_EVENTS_LISTENER_WEBHOOK_INCLUDE_EVENTS");
        Set<String> includedEvents = Collections.emptySet();
        if (includedEventsStr != null && !includedEventsStr.isBlank() && !includedEventsStr.trim().equals("*")) {
            includedEvents = Arrays.stream(includedEventsStr.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }

        String adminEventsStr = resolveProperty(configScope, CONFIG_INCLUDE_ADMIN_EVENTS, "KC_SPI_EVENTS_LISTENER_WEBHOOK_INCLUDE_ADMIN_EVENTS");
        boolean includeAdminEvents = adminEventsStr == null || Boolean.parseBoolean(adminEventsStr.trim());

        long connectTimeoutMs = parseLongOrDefault(
                resolveProperty(configScope, CONFIG_CONNECT_TIMEOUT_MS, "KC_SPI_EVENTS_LISTENER_WEBHOOK_CONNECT_TIMEOUT_MS"),
                3000L
        );

        long readTimeoutMs = parseLongOrDefault(
                resolveProperty(configScope, CONFIG_READ_TIMEOUT_MS, "KC_SPI_EVENTS_LISTENER_WEBHOOK_READ_TIMEOUT_MS"),
                5000L
        );

        Duration connectTimeout = Duration.ofMillis(connectTimeoutMs);
        Duration readTimeout = Duration.ofMillis(readTimeoutMs);

        this.config = new WebhookEventListenerConfig(
                targetUri,
                authHeader,
                includedEvents,
                includeAdminEvents,
                connectTimeout,
                readTimeout
        );

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization required
    }

    @Override
    public void close() {
        if (httpClient != null) {
            httpClient.close();
        }
        LOG.debug("Closing WebhookEventListenerProviderFactory.");
    }

    private String resolveProperty(Config.Scope scope, String configKey, String envVarKey) {
        if (scope != null) {
            String val = scope.get(configKey);
            if (val != null && !val.isBlank()) {
                return val;
            }
        }
        String envVal = System.getenv(envVarKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }
        return System.getProperty(configKey);
    }

    private long parseLongOrDefault(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LOG.warnv("Invalid numeric timeout value: '{0}', falling back to default: {1}ms", value, defaultValue);
            return defaultValue;
        }
    }

    // Visible for testing
    void setHttpClient(HttpClient client) {
        this.httpClient = client;
    }

    WebhookEventListenerConfig getConfig() {
        return this.config;
    }
}
