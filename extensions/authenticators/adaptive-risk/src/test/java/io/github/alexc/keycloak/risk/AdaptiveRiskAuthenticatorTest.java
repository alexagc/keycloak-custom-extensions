package io.github.alexc.keycloak.risk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.alexc.keycloak.risk.config.AdaptiveRiskConfig;
import io.github.alexc.keycloak.risk.engine.RiskScoringEngine;
import io.github.alexc.keycloak.risk.model.GeoLocation;
import io.github.alexc.keycloak.risk.model.LoginContext;
import io.github.alexc.keycloak.risk.model.RiskEvaluationResult;
import io.github.alexc.keycloak.risk.model.RiskFactor;
import io.github.alexc.keycloak.risk.model.RiskLevel;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.events.EventBuilder;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialManager;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

class AdaptiveRiskAuthenticatorTest {

  private KeycloakSession session;
  private RiskScoringEngine scoringEngine;
  private AdaptiveRiskConfig config;
  private AdaptiveRiskAuthenticator authenticator;

  private AuthenticationFlowContext flowContext;
  private UserModel user;
  private AuthenticationSessionModel authSession;
  private HttpRequest httpRequest;
  private HttpHeaders httpHeaders;
  private LoginFormsProvider formsProvider;
  private EventBuilder event;
  private RealmModel realm;
  private UserCredentialManager credentialManager;

  @BeforeEach
  void setUp() {
    session = mock(KeycloakSession.class);
    scoringEngine = mock(RiskScoringEngine.class);
    config = AdaptiveRiskConfig.defaultConfig();
    authenticator = new AdaptiveRiskAuthenticator(session, scoringEngine, config);

    flowContext = mock(AuthenticationFlowContext.class);
    user = mock(UserModel.class);
    authSession = mock(AuthenticationSessionModel.class);
    httpRequest = mock(HttpRequest.class);
    httpHeaders = mock(HttpHeaders.class);
    formsProvider = mock(LoginFormsProvider.class);
    event = mock(EventBuilder.class);
    realm = mock(RealmModel.class);
    credentialManager = mock(UserCredentialManager.class);

    KeycloakContext keycloakContext = mock(KeycloakContext.class);
    when(session.getContext()).thenReturn(keycloakContext);
    when(keycloakContext.getRealm()).thenReturn(realm);
    when(session.userCredentialManager()).thenReturn(credentialManager);

    when(flowContext.getUser()).thenReturn(user);
    when(flowContext.getAuthenticationSession()).thenReturn(authSession);
    when(flowContext.getHttpRequest()).thenReturn(httpRequest);
    when(httpRequest.getHttpHeaders()).thenReturn(httpHeaders);
    when(flowContext.form()).thenReturn(formsProvider);
    when(flowContext.getEvent()).thenReturn(event);
    when(event.user(any())).thenReturn(event);
    when(event.error(any())).thenReturn(event);
    when(event.detail(anyString(), anyString())).thenReturn(event);

    when(formsProvider.setAttribute(anyString(), any())).thenReturn(formsProvider);
    when(formsProvider.setError(anyString())).thenReturn(formsProvider);
    when(user.getUsername()).thenReturn("test-user");
  }

  @Test
  @DisplayName("Should proceed as attempted when no user is present in context")
  void testNoUserAttempted() {
    when(flowContext.getUser()).thenReturn(null);

    authenticator.authenticate(flowContext);

    verify(flowContext).attempted();
    verify(flowContext, never()).success();
  }

  @Test
  @DisplayName("Should succeed directly and persist baseline when risk is low")
  void testLowRiskSuccess() {
    RiskEvaluationResult lowRisk = new RiskEvaluationResult(
        10, RiskLevel.LOW, List.of(new RiskFactor("BASELINE", 10, "Baseline")), false, false);
    when(scoringEngine.evaluate(eq(user), any(LoginContext.class), any(AdaptiveRiskConfig.class)))
        .thenReturn(lowRisk);

    authenticator.authenticate(flowContext);

    verify(authSession).setAuthNote(AdaptiveRiskAuthenticator.AUTH_NOTE_RISK_SCORE, "10");
    verify(authSession).setAuthNote(AdaptiveRiskAuthenticator.AUTH_NOTE_RISK_LEVEL, "LOW");
    verify(authSession).setAuthNote(AdaptiveRiskAuthenticator.AUTH_NOTE_STEP_UP, "false");
    verify(scoringEngine).persistSuccessBaseline(eq(user), any(GeoLocation.class), anyLong(), anyString());
    verify(flowContext).success();
  }

  @Test
  @DisplayName("Should challenge with security template when risk is medium (Step-Up required)")
  void testMediumRiskChallenge() {
    RiskEvaluationResult medRisk = new RiskEvaluationResult(
        50, RiskLevel.MEDIUM, List.of(new RiskFactor("TRAVEL", 50, "Fast travel")), true, false);
    when(scoringEngine.evaluate(eq(user), any(LoginContext.class), any(AdaptiveRiskConfig.class)))
        .thenReturn(medRisk);
    when(authSession.getAuthNote(AdaptiveRiskAuthenticator.AUTH_NOTE_CHALLENGED)).thenReturn(null);

    Response response = mock(Response.class);
    when(formsProvider.createForm(AdaptiveRiskAuthenticator.CHALLENGE_TEMPLATE)).thenReturn(response);

    authenticator.authenticate(flowContext);

    verify(authSession).setAuthNote(AdaptiveRiskAuthenticator.AUTH_NOTE_STEP_UP, "true");
    verify(flowContext).challenge(response);
    verify(flowContext, never()).success();
  }

  @Test
  @DisplayName("Should block and return 403 Forbidden error page when risk is critical")
  void testCriticalRiskBlock() {
    RiskEvaluationResult critRisk = new RiskEvaluationResult(
        90, RiskLevel.CRITICAL, List.of(new RiskFactor("CRITICAL", 90, "TOR and travel")), true, true);
    when(scoringEngine.evaluate(eq(user), any(LoginContext.class), any(AdaptiveRiskConfig.class)))
        .thenReturn(critRisk);

    Response errorResponse = mock(Response.class);
    when(formsProvider.createErrorPage(Response.Status.FORBIDDEN)).thenReturn(errorResponse);

    authenticator.authenticate(flowContext);

    verify(authSession).setAuthNote(AdaptiveRiskAuthenticator.AUTH_NOTE_BLOCKED, "true");
    verify(flowContext).failure(AuthenticationFlowError.ACCESS_DENIED, errorResponse);
  }

  @Test
  @DisplayName("Should fail-open on unexpected scoring engine exception")
  void testFailOpenOnException() {
    when(scoringEngine.evaluate(any(), any(), any())).thenThrow(new RuntimeException("Engine failure"));

    authenticator.authenticate(flowContext);

    verify(flowContext).success();
    verify(flowContext, never()).failure(any());
  }

  @Test
  @DisplayName("Action: cancel submission should fail with USER_CANCELLED")
  void testActionCancel() {
    MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
    params.putSingle("action", "cancel");
    when(httpRequest.getDecodedFormParameters()).thenReturn(params);

    authenticator.action(flowContext);

    verify(flowContext).failure(AuthenticationFlowError.USER_CANCELLED);
    verify(flowContext, never()).success();
  }

  @Test
  @DisplayName("Action: continue submission should add CONFIGURE_TOTP when user lacks MFA")
  void testActionContinueWithoutMfa() {
    MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
    params.putSingle("action", "continue");
    when(httpRequest.getDecodedFormParameters()).thenReturn(params);
    when(credentialManager.isConfiguredFor(any(), any(), any())).thenReturn(false);

    authenticator.action(flowContext);

    verify(authSession).setAuthNote(AdaptiveRiskAuthenticator.AUTH_NOTE_CHALLENGED, "true");
    verify(user).addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
    verify(flowContext).success();
  }
}
