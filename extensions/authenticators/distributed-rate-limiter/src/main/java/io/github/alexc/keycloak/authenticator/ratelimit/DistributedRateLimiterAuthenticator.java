package io.github.alexc.keycloak.authenticator.ratelimit;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.Objects;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Keycloak Authenticator implementing a distributed sliding window rate limiter
 * backed by Valkey or Redis.
 *
 * <p>Protects authentication endpoints from distributed brute-force and credential stuffing
 * attacks by enforcing rate limits per client IP and per targeted username before database queries.
 */
public class DistributedRateLimiterAuthenticator implements Authenticator {

  private static final Logger LOG = Logger.getLogger(DistributedRateLimiterAuthenticator.class);

  private final KeycloakSession session;
  private final RateLimiterClient client;
  private final RateLimiterConfig baseConfig;

  /**
   * Constructs the authenticator instance.
   *
   * @param session the current Keycloak session
   * @param client the shared Valkey/Redis client
   * @param baseConfig default or global SPI configuration
   */
  public DistributedRateLimiterAuthenticator(
      KeycloakSession session, RateLimiterClient client, RateLimiterConfig baseConfig) {
    this.session = Objects.requireNonNull(session, "session must not be null");
    this.client = Objects.requireNonNull(client, "client must not be null");
    this.baseConfig = Objects.requireNonNull(baseConfig, "baseConfig must not be null");
  }

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    RateLimiterConfig config = baseConfig.merge(context.getAuthenticatorConfig());
    String realmName = context.getRealm() != null ? context.getRealm().getName() : "master";
    String clientIp = resolveClientIp(context);

    // 1. Enforce IP-based Sliding Window Rate Limit
    String ipKey = "rl:ip:" + realmName + ":" + clientIp;
    RateLimitDecision ipDecision;
    try {
      ipDecision =
          client.evaluate(
              ipKey, config.ipWindowSeconds(), config.ipLimit(), "IP", config.failOpen());
    } catch (RateLimiterUnavailableException e) {
      context.challenge(buildServiceUnavailableResponse());
      return;
    }

    if (!ipDecision.allowed()) {
      LOG.warnv(
          "IP rate limit exceeded for IP {0} in realm {1}. Retry-After: {2}s",
          clientIp, realmName, ipDecision.retryAfterSeconds());
      context.challenge(buildRateLimitResponse(context, ipDecision, config.ipLimit()));
      return;
    }

    // 2. Enforce User-based Sliding Window Rate Limit (if username is available)
    String username = resolveUsername(context);
    if (username != null && !username.isBlank()) {
      String normalizedUser = username.trim().toLowerCase();
      String userKey = "rl:usr:" + realmName + ":" + normalizedUser;
      RateLimitDecision userDecision;
      try {
        userDecision =
            client.evaluate(
                userKey, config.userWindowSeconds(), config.userLimit(), "USER", config.failOpen());
      } catch (RateLimiterUnavailableException e) {
        context.challenge(buildServiceUnavailableResponse());
        return;
      }

      if (!userDecision.allowed()) {
        LOG.warnv(
            "User rate limit exceeded for account {0} (from IP {1}) in realm {2}. Retry-After: {3}s",
            username, clientIp, realmName, userDecision.retryAfterSeconds());
        context.challenge(buildRateLimitResponse(context, userDecision, config.userLimit()));
        return;
      }
    }

    // Within rate limits
    context.success();
  }

  @Override
  public void action(AuthenticationFlowContext context) {
    authenticate(context);
  }

  @Override
  public boolean requiresUser() {
    return false;
  }

  @Override
  public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
    return true;
  }

  @Override
  public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    // No required actions needed for rate limiting
  }

  @Override
  public void close() {
    // Connection pool lifecycle is managed by AuthenticatorFactory
  }

  /**
   * Resolves the remote client IP, respecting proxy headers.
   *
   * @param context the flow context
   * @return sanitized client IP string
   */
  public static String resolveClientIp(AuthenticationFlowContext context) {
    if (context == null) {
      return "127.0.0.1";
    }

    if (context.getHttpRequest() != null && context.getHttpRequest().getHttpHeaders() != null) {
      var headers = context.getHttpRequest().getHttpHeaders();
      String cfIp = headers.getHeaderString("CF-Connecting-IP");
      if (isValidIp(cfIp)) {
        return cfIp.trim();
      }

      String xRealIp = headers.getHeaderString("X-Real-IP");
      if (isValidIp(xRealIp)) {
        return xRealIp.trim();
      }

      String xff = headers.getHeaderString("X-Forwarded-For");
      if (xff != null && !xff.isBlank()) {
        String[] parts = xff.split(",");
        if (parts.length > 0 && isValidIp(parts[0])) {
          return parts[0].trim();
        }
      }
    }

    if (context.getConnection() != null) {
      String remoteAddr = context.getConnection().getRemoteAddr();
      if (remoteAddr != null && !remoteAddr.isBlank()) {
        return remoteAddr.trim();
      }
    }

    return "127.0.0.1";
  }

  private static boolean isValidIp(String ip) {
    return ip != null && !ip.isBlank() && !ip.equalsIgnoreCase("unknown");
  }

  /**
   * Resolves target username from context if already authenticated, stored in auth session, or
   * submitted in form.
   *
   * @param context the flow context
   * @return username or null if not available yet
   */
  public static String resolveUsername(AuthenticationFlowContext context) {
    if (context == null) {
      return null;
    }
    if (context.getUser() != null && context.getUser().getUsername() != null) {
      return context.getUser().getUsername().trim();
    }
    if (context.getAuthenticationSession() != null) {
      String authNote = context.getAuthenticationSession().getAuthNote("ATTEMPTED_USERNAME");
      if (authNote != null && !authNote.isBlank()) {
        return authNote.trim();
      }
    }
    if (context.getHttpRequest() != null) {
      try {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        if (form != null) {
          String user = form.getFirst("username");
          if (user != null && !user.isBlank()) {
            return user.trim();
          }
        }
      } catch (Exception ignored) {
        // Form parameters not present or not decodable
      }
    }
    return null;
  }

  private Response buildRateLimitResponse(
      AuthenticationFlowContext context, RateLimitDecision decision, int limit) {
    boolean isJson = isJsonRequest(context);
    if (isJson) {
      String json =
          """
          {"error":"too_many_requests","error_description":"Rate limit exceeded. Please retry after %d seconds.","retry_after":%d}
          """
              .formatted(decision.retryAfterSeconds(), decision.retryAfterSeconds());
      return Response.status(429)
          .header("Retry-After", String.valueOf(decision.retryAfterSeconds()))
          .header("X-RateLimit-Limit", String.valueOf(limit))
          .header("X-RateLimit-Remaining", "0")
          .header("X-RateLimit-Reset", String.valueOf(decision.retryAfterSeconds()))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .entity(json)
          .build();
    } else {
      Response formResponse =
          context
              .form()
              .setError("rateLimitExceeded", String.valueOf(decision.retryAfterSeconds()))
              .createErrorPage(Response.Status.TOO_MANY_REQUESTS);
      return Response.fromResponse(formResponse)
          .status(429)
          .header("Retry-After", String.valueOf(decision.retryAfterSeconds()))
          .header("X-RateLimit-Limit", String.valueOf(limit))
          .header("X-RateLimit-Remaining", "0")
          .header("X-RateLimit-Reset", String.valueOf(decision.retryAfterSeconds()))
          .build();
    }
  }

  private Response buildServiceUnavailableResponse() {
    String json =
        """
        {"error":"service_unavailable","error_description":"Rate limiting service temporarily unavailable."}
        """;
    return Response.status(Response.Status.SERVICE_UNAVAILABLE)
        .header("Retry-After", "30")
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(json)
        .build();
  }

  static boolean isJsonRequest(AuthenticationFlowContext context) {
    if (context == null || context.getHttpRequest() == null) {
      return false;
    }
    String accept = context.getHttpRequest().getHttpHeaders().getHeaderString("Accept");
    if (accept != null && (accept.contains("application/json") || accept.contains("+json"))) {
      return true;
    }
    String contentType =
        context.getHttpRequest().getHttpHeaders().getHeaderString("Content-Type");
    if (contentType != null && contentType.contains("application/json")) {
      return true;
    }
    if (context.getUriInfo() != null && context.getUriInfo().getPath() != null) {
      String path = context.getUriInfo().getPath();
      if (path.contains("/token") || path.contains("/certs") || path.contains("/userinfo")) {
        return true;
      }
    }
    return false;
  }
}
