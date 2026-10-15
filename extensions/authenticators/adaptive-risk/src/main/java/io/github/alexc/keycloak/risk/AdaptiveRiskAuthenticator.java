package io.github.alexc.keycloak.risk;

import io.github.alexc.keycloak.risk.config.AdaptiveRiskConfig;
import io.github.alexc.keycloak.risk.engine.DeviceFingerprintEvaluator;
import io.github.alexc.keycloak.risk.engine.GeoLocationResolver;
import io.github.alexc.keycloak.risk.engine.RiskScoringEngine;
import io.github.alexc.keycloak.risk.model.GeoLocation;
import io.github.alexc.keycloak.risk.model.LoginContext;
import io.github.alexc.keycloak.risk.model.RiskEvaluationResult;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.Errors;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Keycloak Authenticator evaluating contextual risk for Zero-Trust architectures.
 * Assesses impossible travel velocity, IP reputation (TOR exit nodes), and device fingerprint changes,
 * enforcing dynamic Step-Up MFA or preventive blocking.
 */
public class AdaptiveRiskAuthenticator implements Authenticator {

  private static final Logger LOG = Logger.getLogger(AdaptiveRiskAuthenticator.class);

  public static final String AUTH_NOTE_RISK_SCORE = "adaptive_risk_score";
  public static final String AUTH_NOTE_RISK_LEVEL = "adaptive_risk_level";
  public static final String AUTH_NOTE_STEP_UP = "adaptive_risk_step_up";
  public static final String AUTH_NOTE_BLOCKED = "adaptive_risk_blocked";
  public static final String AUTH_NOTE_CHALLENGED = "adaptive_risk_challenged";

  public static final String CHALLENGE_TEMPLATE = "adaptive-risk-challenge.ftl";

  private final KeycloakSession session;
  private final RiskScoringEngine scoringEngine;
  private final AdaptiveRiskConfig baseConfig;

  /**
   * Constructs an AdaptiveRiskAuthenticator with session and base configuration.
   *
   * @param session Current Keycloak session
   * @param scoringEngine Risk scoring engine
   * @param baseConfig Base SPI configuration
   */
  public AdaptiveRiskAuthenticator(
      KeycloakSession session,
      RiskScoringEngine scoringEngine,
      AdaptiveRiskConfig baseConfig) {
    this.session = session;
    this.scoringEngine = scoringEngine;
    this.baseConfig = baseConfig;
  }

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    UserModel user = context.getUser();
    if (user == null) {
      LOG.warn("AdaptiveRiskAuthenticator invoked without an identified user; proceeding as attempted");
      context.attempted();
      return;
    }

    AdaptiveRiskConfig config = AdaptiveRiskConfig.fromExecution(baseConfig, context.getAuthenticatorConfig());

    HttpRequest request = context.getHttpRequest();
    String remoteAddr = (context.getConnection() != null) ? context.getConnection().getRemoteAddr() : "127.0.0.1";

    String clientIp = GeoLocationResolver.resolveClientIp(request, remoteAddr);
    GeoLocation location = GeoLocationResolver.resolveLocation(request, remoteAddr);

    String userAgent = (request != null && request.getHttpHeaders() != null)
        ? request.getHttpHeaders().getHeaderString(GeoLocationResolver.HEADER_USER_AGENT)
        : "Unknown";
    String acceptLang = (request != null && request.getHttpHeaders() != null)
        ? request.getHttpHeaders().getHeaderString(GeoLocationResolver.HEADER_ACCEPT_LANGUAGE)
        : "None";

    String fingerprint = DeviceFingerprintEvaluator.computeFingerprint(userAgent, acceptLang, clientIp);
    long currentTimestamp = System.currentTimeMillis();

    LoginContext loginContext = new LoginContext(clientIp, userAgent, currentTimestamp, location, fingerprint);

    RiskEvaluationResult result;
    try {
      result = scoringEngine.evaluate(user, loginContext, config);
    } catch (Exception e) {
      LOG.errorv(e, "Error during adaptive risk evaluation for user {0}: {1}", user.getUsername(), e.getMessage());
      if (config.failOpen()) {
        LOG.warnv("Fail-open active: allowing login for user {0}", user.getUsername());
        context.success();
        return;
      } else {
        context.failure(AuthenticationFlowError.INTERNAL_ERROR);
        return;
      }
    }

    recordSessionNotes(context.getAuthenticationSession(), result);

    if (result.shouldBlock()) {
      handleBlock(context, user, result);
    } else if (result.shouldStepUp()) {
      handleStepUp(context, user, result, location, currentTimestamp, fingerprint);
    } else {
      handleSuccess(context, user, location, currentTimestamp, fingerprint);
    }
  }

  @Override
  public void action(AuthenticationFlowContext context) {
    MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
    String action = formData.getFirst("action");

    if ("cancel".equalsIgnoreCase(action)) {
      context.failure(AuthenticationFlowError.GENERIC_AUTHENTICATION_ERROR);
      return;
    }

    UserModel user = context.getUser();
    AuthenticationSessionModel authSession = context.getAuthenticationSession();
    if (authSession != null) {
      authSession.setAuthNote(AUTH_NOTE_CHALLENGED, "true");
    }

    // After user acknowledges challenge, require MFA registration if user has no MFA configured
    if (user != null && !isMfaConfigured(user)) {
      user.addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
    }

    context.success();
  }

  @Override
  public boolean requiresUser() {
    return true;
  }

  @Override
  public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
    return true;
  }

  @Override
  public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    // Actions are dynamically assigned when Step-Up MFA is challenged
  }

  @Override
  public void close() {
    // Session-scoped provider cleanup
  }

  private void handleBlock(AuthenticationFlowContext context, UserModel user, RiskEvaluationResult result) {
    LOG.warnv("High risk login blocked for user {0}: score={1}, level={2}",
        user.getUsername(), result.overallScore(), result.level());

    context.getEvent()
        .user(user)
        .detail("risk_score", String.valueOf(result.overallScore()))
        .detail("risk_level", result.level().name())
        .error(Errors.NOT_ALLOWED);

    Response errorResponse = context.form()
        .setError("adaptiveRiskBlocked")
        .createErrorPage(Response.Status.FORBIDDEN);

    context.failure(AuthenticationFlowError.ACCESS_DENIED, errorResponse);
  }

  private void handleStepUp(
      AuthenticationFlowContext context,
      UserModel user,
      RiskEvaluationResult result,
      GeoLocation location,
      long timestamp,
      String fingerprint) {

    LOG.infov("Step-Up MFA required for user {0}: score={1}", user.getUsername(), result.overallScore());

    context.getEvent()
        .user(user)
        .detail("risk_score", String.valueOf(result.overallScore()))
        .detail("risk_level", result.level().name())
        .detail("risk_step_up", "true");

    AuthenticationSessionModel authSession = context.getAuthenticationSession();
    boolean alreadyChallenged = authSession != null && "true".equalsIgnoreCase(authSession.getAuthNote(AUTH_NOTE_CHALLENGED));

    if (!alreadyChallenged) {
      String locationDesc = (location.cityName() != null)
          ? location.cityName() + (location.countryCode() != null ? ", " + location.countryCode() : "")
          : (location.countryCode() != null ? location.countryCode() : "Unknown location");

      Response challengePage = context.form()
          .setAttribute("riskScore", result.overallScore())
          .setAttribute("riskLevel", result.level().name())
          .setAttribute("clientIp", location.ipAddress())
          .setAttribute("locationDesc", locationDesc)
          .createForm(CHALLENGE_TEMPLATE);

      context.challenge(challengePage);
    } else {
      // User has completed challenge acknowledgment
      scoringEngine.persistSuccessBaseline(user, location, timestamp, fingerprint);
      context.success();
    }
  }

  private void handleSuccess(
      AuthenticationFlowContext context,
      UserModel user,
      GeoLocation location,
      long timestamp,
      String fingerprint) {

    scoringEngine.persistSuccessBaseline(user, location, timestamp, fingerprint);
    context.success();
  }

  private void recordSessionNotes(AuthenticationSessionModel authSession, RiskEvaluationResult result) {
    if (authSession == null) {
      return;
    }
    String scoreStr = String.valueOf(result.overallScore());
    String levelStr = result.level().name();
    String stepUpStr = String.valueOf(result.shouldStepUp());
    String blockStr = String.valueOf(result.shouldBlock());

    authSession.setAuthNote(AUTH_NOTE_RISK_SCORE, scoreStr);
    authSession.setAuthNote(AUTH_NOTE_RISK_LEVEL, levelStr);
    authSession.setAuthNote(AUTH_NOTE_STEP_UP, stepUpStr);
    authSession.setAuthNote(AUTH_NOTE_BLOCKED, blockStr);

    authSession.setUserSessionNote(AUTH_NOTE_RISK_SCORE, scoreStr);
    authSession.setUserSessionNote(AUTH_NOTE_RISK_LEVEL, levelStr);
    authSession.setUserSessionNote(AUTH_NOTE_STEP_UP, stepUpStr);
  }

  private boolean isMfaConfigured(UserModel user) {
    if (user == null || user.credentialManager() == null) {
      return false;
    }
    return user.credentialManager().isConfiguredFor(OTPCredentialModel.TYPE)
        || user.credentialManager().isConfiguredFor("webauthn")
        || user.credentialManager().isConfiguredFor("webauthn-passwordless");
  }
}
