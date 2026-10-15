package io.github.alexc.keycloak.ageproofing.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.alexc.keycloak.ageproofing.model.AgeVerificationConfig;
import io.github.alexc.keycloak.ageproofing.model.VerificationEvidence;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import org.jboss.logging.Logger;
import org.keycloak.jose.jws.JWSHeader;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.util.JsonSerialization;

/**
 * Validates cryptographic Verifiable Presentations and eID assertions (JWS / OpenID4VP / eIDAS 2.0).
 *
 * <p>Enforces zero-PII validation: verifies the cryptographic signature against trusted JWKS,
 * checks expiration and issuer whitelisting, evaluates age proof claims, and computes an immutable
 * SHA-256 audit digest without storing or logging any personal identity data.
 */
public class VerifiablePresentationValidator {

    private static final Logger LOG = Logger.getLogger(VerifiablePresentationValidator.class);

    private final PublicTrustProviderResolver keyResolver;

    public VerifiablePresentationValidator(PublicTrustProviderResolver keyResolver) {
        this.keyResolver = keyResolver;
    }

    /**
     * Validates a raw JWS assertion token against the provided configuration.
     *
     * @param rawToken raw JWT/JWS assertion token string
     * @param config   effective AgeVerificationConfig
     * @return VerificationEvidence capturing success or detailed failure reason
     */
    public VerificationEvidence validate(String rawToken, AgeVerificationConfig config) {
        if (rawToken == null || rawToken.isBlank()) {
            return VerificationEvidence.failed("Empty or blank assertion token");
        }

        String trimmedToken = rawToken.trim();
        String proofHash = calculateSha256(trimmedToken);

        JWSInput jwsInput;
        try {
            jwsInput = new JWSInput(trimmedToken);
        } catch (Exception e) {
            LOG.warnv("Malformed JWS assertion structure: {0}", e.getMessage());
            return VerificationEvidence.failed("Malformed assertion structure");
        }

        JWSHeader header = jwsInput.getHeader();
        if (header == null) {
            return VerificationEvidence.failed("Missing JWS header in assertion");
        }

        String alg = header.getRawAlgorithm();
        if (alg == null || alg.isBlank()) {
            return VerificationEvidence.failed("Missing cryptographic algorithm in JWS header");
        }

        String kid = header.getKeyId();
        PublicKey publicKey = null;
        if (config.jwksUrl() != null) {
            publicKey = keyResolver.resolvePublicKey(config.jwksUrl(), kid, config.apiTimeoutMs(), config.cacheTtlMinutes());
        }

        if (publicKey == null) {
            if (config.failOpen()) {
                LOG.warnv("JWKS public key resolution failed for {0}, proceeding under fail-open policy", config.jwksUrl());
                return VerificationEvidence.successful(proofHash, "degraded-fail-open");
            }
            return VerificationEvidence.failed("Unable to resolve trusted public key from JWKS");
        }

        // Verify cryptographic signature
        try {
            boolean signatureValid = verifySignature(jwsInput, alg, publicKey);
            if (!signatureValid) {
                return VerificationEvidence.failed("Cryptographic signature verification failed");
            }
        } catch (Exception e) {
            LOG.warnv("Cryptographic verification exception: {0}", e.getMessage());
            return VerificationEvidence.failed("Error during signature verification: " + e.getMessage());
        }

        // Parse claims and evaluate age proof
        try {
            String content = jwsInput.readContentAsString();
            JsonNode root = JsonSerialization.readValue(content, JsonNode.class);

            String issuer = resolveClaimString(root, "iss");
            if (issuer == null || issuer.isBlank()) {
                issuer = "unknown-issuer";
            }

            // Verify allowed issuers whitelist if configured
            if (!config.allowedIssuers().isEmpty() && !config.allowedIssuers().contains(issuer)) {
                return VerificationEvidence.failed("Assertion issuer '" + issuer + "' is not permitted");
            }

            // Check expiration
            if (root.has("exp") && root.get("exp").isNumber()) {
                long exp = root.get("exp").asLong();
                if (Instant.now().getEpochSecond() > exp) {
                    return VerificationEvidence.failed("Assertion token has expired");
                }
            }

            // Check not-before (nbf)
            if (root.has("nbf") && root.get("nbf").isNumber()) {
                long nbf = root.get("nbf").asLong();
                if (Instant.now().getEpochSecond() < nbf) {
                    return VerificationEvidence.failed("Assertion token is not yet valid");
                }
            }

            // Evaluate age proof claims (top-level or inside credentialSubject/vc)
            boolean isOverAge = evaluateAgeClaim(root, config.minAge());
            if (!isOverAge) {
                return VerificationEvidence.failed("Subject does not meet the minimum age requirement (" + config.minAge() + "+)");
            }

            return VerificationEvidence.successful(proofHash, issuer);
        } catch (Exception e) {
            LOG.warnv("Failed to parse or evaluate assertion payload claims: {0}", e.getMessage());
            return VerificationEvidence.failed("Invalid assertion payload claims");
        }
    }

    private boolean verifySignature(JWSInput input, String alg, PublicKey publicKey) throws Exception {
        Signature signature;
        switch (alg.toUpperCase()) {
            case "RS256" -> signature = Signature.getInstance("SHA256withRSA");
            case "RS384" -> signature = Signature.getInstance("SHA384withRSA");
            case "RS512" -> signature = Signature.getInstance("SHA512withRSA");
            case "ES256" -> signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            case "ES384" -> signature = Signature.getInstance("SHA384withECDSAinP1363Format");
            case "ES512" -> signature = Signature.getInstance("SHA512withECDSAinP1363Format");
            default -> throw new IllegalArgumentException("Unsupported JWS algorithm: " + alg);
        }

        signature.initVerify(publicKey);

        String wire = input.getWireString();
        int lastDot = wire.lastIndexOf('.');
        if (lastDot <= 0) {
            return false;
        }
        byte[] signingInput = wire.substring(0, lastDot).getBytes(StandardCharsets.US_ASCII);
        signature.update(signingInput);

        return signature.verify(input.getSignature());
    }

    private boolean evaluateAgeClaim(JsonNode root, int minAge) {
        String dynamicClaim = "age_over_" + minAge;
        String[] candidateClaims = {dynamicClaim, "age_over_18", "is_over_18", "age_verified", "is_of_age"};

        // 1. Check direct top-level boolean claims
        for (String claim : candidateClaims) {
            if (root.has(claim) && root.get(claim).asBoolean(false)) {
                return true;
            }
        }

        // 2. Check W3C Verifiable Credential / OpenID4VP credentialSubject nested claims
        JsonNode credentialSubject = null;
        if (root.has("credentialSubject")) {
            credentialSubject = root.get("credentialSubject");
        } else if (root.has("vc") && root.get("vc").has("credentialSubject")) {
            credentialSubject = root.get("vc").get("credentialSubject");
        } else if (root.has("claims")) {
            credentialSubject = root.get("claims");
        }

        if (credentialSubject != null) {
            for (String claim : candidateClaims) {
                if (credentialSubject.has(claim) && credentialSubject.get(claim).asBoolean(false)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String resolveClaimString(JsonNode root, String claim) {
        if (root.has(claim) && root.get(claim).isTextual()) {
            return root.get(claim).asText();
        }
        return null;
    }

    /**
     * Calculates the SHA-256 cryptographic hex digest of an input string.
     *
     * @param input raw string
     * @return 64-character lowercase hexadecimal hash
     */
    public static String calculateSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
