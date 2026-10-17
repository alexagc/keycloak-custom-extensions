package io.github.alexc.keycloak.authenticator.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.common.ClientConnection;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.mockito.ArgumentCaptor;

class DistributedRateLimiterAuthenticatorTest {

  private KeycloakSession session;
  private RateLimiterClient client;
  private RateLimiterConfig config;
  private DistributedRateLimiterAuthenticator authenticator;
  private AuthenticationFlowContext context;
  private RealmModel realm;
  private ClientConnection connection;
  private HttpRequest httpRequest;
  private HttpHeaders httpHeaders;

  @BeforeEach
  void setUp() {
    session = mock(KeycloakSession.class);
    client = mock(RateLimiterClient.class);
    config = RateLimiterConfig.defaultConfiguration();
    authenticator = new DistributedRateLimiterAuthenticator(session, client, config);

    context = mock(AuthenticationFlowContext.class);
    realm = mock(RealmModel.class);
    connection = mock(ClientConnection.class);
    httpRequest = mock(HttpRequest.class);
    httpHeaders = mock(HttpHeaders.class);

    when(context.getRealm()).thenReturn(realm);
    when(realm.getName()).thenReturn("test-realm");
    when(context.getConnection()).thenReturn(connection);
    when(connection.getRemoteAddr()).thenReturn("192.168.1.100");
    when(context.getHttpRequest()).thenReturn(httpRequest);
    when(httpRequest.getHttpHeaders()).thenReturn(httpHeaders);
    when(context.getAuthenticatorConfig()).thenReturn(null);
  }

  @Test
  @DisplayName("resolveClientIp extracts remote address from connection when no proxy headers present")
  void testResolveClientIpDirect() {
    assertEquals("192.168.1.100", DistributedRateLimiterAuthenticator.resolveClientIp(context));
  }

  @Test
  @DisplayName("resolveClientIp prioritizes proxy headers over connection remoteAddr")
  void testResolveClientIpForwarded() {
    when(httpHeaders.getHeaderString("X-Forwarded-For"))
        .thenReturn("203.0.113.195, 70.41.3.18, 150.172.238.178");
    // Even when connection remoteAddr is present (proxy IP), X-Forwarded-For wins
    assertEquals("203.0.113.195", DistributedRateLimiterAuthenticator.resolveClientIp(context));

    when(httpHeaders.getHeaderString("X-Real-IP")).thenReturn("198.51.100.42");
    assertEquals("198.51.100.42", DistributedRateLimiterAuthenticator.resolveClientIp(context));

    when(httpHeaders.getHeaderString("CF-Connecting-IP")).thenReturn("198.51.100.99");
    assertEquals("198.51.100.99", DistributedRateLimiterAuthenticator.resolveClientIp(context));
  }

  @Test
  @DisplayName("resolveClientIp returns 127.0.0.1 when context or addresses are null")
  void testResolveClientIpNull() {
    assertEquals("127.0.0.1", DistributedRateLimiterAuthenticator.resolveClientIp(null));

    when(context.getConnection()).thenReturn(null);
    when(httpHeaders.getHeaderString("X-Forwarded-For")).thenReturn(null);
    assertEquals("127.0.0.1", DistributedRateLimiterAuthenticator.resolveClientIp(context));
  }

  @Test
  @DisplayName("resolveUsername extracts from UserModel when user is identified")
  void testResolveUsernameFromUserModel() {
    UserModel user = mock(UserModel.class);
    when(user.getUsername()).thenReturn("alice");
    when(context.getUser()).thenReturn(user);

    assertEquals("alice", DistributedRateLimiterAuthenticator.resolveUsername(context));
  }

  @Test
  @DisplayName("resolveUsername extracts from auth session ATTEMPTED_USERNAME note")
  void testResolveUsernameFromAuthSession() {
    AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
    when(context.getUser()).thenReturn(null);
    when(context.getAuthenticationSession()).thenReturn(authSession);
    when(authSession.getAuthNote("ATTEMPTED_USERNAME")).thenReturn("bob");

    assertEquals("bob", DistributedRateLimiterAuthenticator.resolveUsername(context));
  }

  @Test
  @DisplayName("resolveUsername extracts from form parameters")
  void testResolveUsernameFromFormParameters() {
    when(context.getUser()).thenReturn(null);
    when(context.getAuthenticationSession()).thenReturn(null);
    MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
    form.putSingle("username", "carol");
    when(httpRequest.getDecodedFormParameters()).thenReturn(form);

    assertEquals("carol", DistributedRateLimiterAuthenticator.resolveUsername(context));
  }

  @Test
  @DisplayName("resolveUsername returns null when no user context is present")
  void testResolveUsernameNull() {
    when(context.getUser()).thenReturn(null);
    when(context.getAuthenticationSession()).thenReturn(null);
    when(httpRequest.getDecodedFormParameters()).thenReturn(new MultivaluedHashMap<>());

    assertNull(DistributedRateLimiterAuthenticator.resolveUsername(context));
    assertNull(DistributedRateLimiterAuthenticator.resolveUsername(null));
  }

  @Test
  @DisplayName("authenticate succeeds when IP and user are within quota")
  void testAuthenticateWithinQuota() {
    when(client.evaluate(eq("rl:ip:test-realm:192.168.1.100"), eq(60), eq(30), eq("IP"), eq(true)))
        .thenReturn(RateLimitDecision.allow(29, "IP"));

    UserModel user = mock(UserModel.class);
    when(user.getUsername()).thenReturn("alice");
    when(context.getUser()).thenReturn(user);
    when(client.evaluate(eq("rl:usr:test-realm:alice"), eq(120), eq(5), eq("USER"), eq(true)))
        .thenReturn(RateLimitDecision.allow(4, "USER"));

    authenticator.authenticate(context);

    verify(context).success();
    verify(context, never()).challenge(any());
  }

  @Test
  @DisplayName("authenticate challenges with 429 JSON response when IP limit is exceeded on API request")
  void testAuthenticateIpExceededJson() {
    when(client.evaluate(eq("rl:ip:test-realm:192.168.1.100"), anyInt(), anyInt(), eq("IP"), anyBoolean()))
        .thenReturn(RateLimitDecision.reject(45, "IP"));

    when(httpHeaders.getHeaderString("Accept")).thenReturn("application/json");

    authenticator.authenticate(context);

    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(context).challenge(responseCaptor.capture());
    verify(context, never()).success();

    Response response = responseCaptor.getValue();
    assertEquals(429, response.getStatus());
    assertEquals("45", response.getHeaderString("Retry-After"));
    assertEquals("30", response.getHeaderString("X-RateLimit-Limit"));
    assertEquals("0", response.getHeaderString("X-RateLimit-Remaining"));
    assertEquals("45", response.getHeaderString("X-RateLimit-Reset"));
    assertTrue(response.getEntity().toString().contains("too_many_requests"));
  }

  @Test
  @DisplayName("authenticate challenges with 429 HTML error page when IP limit is exceeded on browser request")
  void testAuthenticateIpExceededHtml() {
    when(client.evaluate(eq("rl:ip:test-realm:192.168.1.100"), anyInt(), anyInt(), eq("IP"), anyBoolean()))
        .thenReturn(RateLimitDecision.reject(30, "IP"));

    when(httpHeaders.getHeaderString("Accept")).thenReturn("text/html,application/xhtml+xml");

    LoginFormsProvider formsProvider = mock(LoginFormsProvider.class);
    when(context.form()).thenReturn(formsProvider);
    when(formsProvider.setError("rateLimitExceeded", "30")).thenReturn(formsProvider);
    when(formsProvider.createErrorPage(Response.Status.TOO_MANY_REQUESTS))
        .thenReturn(Response.status(Response.Status.TOO_MANY_REQUESTS).entity("Rate limited page").build());

    authenticator.authenticate(context);

    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(context).challenge(responseCaptor.capture());
    Response response = responseCaptor.getValue();
    assertEquals(429, response.getStatus());
    assertEquals("30", response.getHeaderString("Retry-After"));
  }

  @Test
  @DisplayName("authenticate challenges with 429 when user limit is exceeded")
  void testAuthenticateUserExceeded() {
    when(client.evaluate(eq("rl:ip:test-realm:192.168.1.100"), anyInt(), anyInt(), eq("IP"), anyBoolean()))
        .thenReturn(RateLimitDecision.allow(25, "IP"));

    UserModel user = mock(UserModel.class);
    when(user.getUsername()).thenReturn("bob");
    when(context.getUser()).thenReturn(user);

    when(client.evaluate(eq("rl:usr:test-realm:bob"), anyInt(), anyInt(), eq("USER"), anyBoolean()))
        .thenReturn(RateLimitDecision.reject(90, "USER"));

    when(httpHeaders.getHeaderString("Accept")).thenReturn("application/json");

    authenticator.authenticate(context);

    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(context).challenge(responseCaptor.capture());
    Response response = responseCaptor.getValue();
    assertEquals(429, response.getStatus());
    assertEquals("90", response.getHeaderString("Retry-After"));
  }

  @Test
  @DisplayName("authenticate returns 503 when store is unavailable and fail-open is disabled")
  void testAuthenticateStoreUnavailableFailClose() {
    AuthenticatorConfigModel configModel = mock(AuthenticatorConfigModel.class);
    when(configModel.getConfig()).thenReturn(java.util.Map.of(RateLimiterConfig.CONF_FAIL_OPEN, "false"));
    when(context.getAuthenticatorConfig()).thenReturn(configModel);

    when(client.evaluate(anyString(), anyInt(), anyInt(), anyString(), eq(false)))
        .thenThrow(new RateLimiterUnavailableException("Valkey connection down"));

    authenticator.authenticate(context);

    ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
    verify(context).challenge(responseCaptor.capture());
    Response response = responseCaptor.getValue();
    assertEquals(503, response.getStatus());
    assertTrue(response.getEntity().toString().contains("service_unavailable"));
  }

  @Test
  @DisplayName("Authenticator lifecycle and configuration methods behave correctly")
  void testAuthenticatorLifecycle() {
    assertFalse(authenticator.requiresUser());
    assertTrue(authenticator.configuredFor(session, realm, null));
    authenticator.setRequiredActions(session, realm, null);
    authenticator.close();

    // action delegates to authenticate
    when(client.evaluate(anyString(), anyInt(), anyInt(), anyString(), anyBoolean()))
        .thenReturn(RateLimitDecision.allow(10, "IP"));
    authenticator.action(context);
    verify(context).success();
  }

  @Test
  @DisplayName("isJsonRequest identifies token endpoints and JSON accept headers")
  void testIsJsonRequest() {
    UriInfo uriInfo = mock(UriInfo.class);
    when(context.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getPath()).thenReturn("/realms/master/protocol/openid-connect/token");
    assertTrue(DistributedRateLimiterAuthenticator.isJsonRequest(context));

    when(uriInfo.getPath()).thenReturn("/realms/master/login-actions/authenticate");
    when(httpHeaders.getHeaderString("Accept")).thenReturn("application/json");
    assertTrue(DistributedRateLimiterAuthenticator.isJsonRequest(context));

    when(httpHeaders.getHeaderString("Accept")).thenReturn("text/html");
    when(httpHeaders.getHeaderString("Content-Type")).thenReturn("application/json");
    assertTrue(DistributedRateLimiterAuthenticator.isJsonRequest(context));

    when(httpHeaders.getHeaderString("Content-Type")).thenReturn("application/x-www-form-urlencoded");
    assertFalse(DistributedRateLimiterAuthenticator.isJsonRequest(context));
  }
}
