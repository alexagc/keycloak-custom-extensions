package io.github.alexc.keycloak.ageproofing.challenge;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates OpenID for Verifiable Presentations (OpenID4VP) challenge URIs and nonces
 * for digital wallets (e.g. EU Digital Identity Wallet / EUDI Wallet).
 */
public class OpenId4VpChallengeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Immutable record holding the generated challenge parameters.
     *
     * @param nonce         cryptographically secure random nonce
     * @param openid4vpUri  formatted deep-link URI (openid4vp://...)
     * @param callbackUrl   Keycloak action callback URL for response submission
     */
    public record ChallengeDescriptor(
            String nonce,
            String openid4vpUri,
            String callbackUrl
    ) {}

    /**
     * Generates a new OpenID4VP challenge.
     *
     * @param clientId    Keycloak client ID or realm issuer identifier
     * @param callbackUrl Keycloak action URL to which the assertion will be posted
     * @param minAge      minimum required age for the presentation definition
     * @return populated ChallengeDescriptor
     */
    public static ChallengeDescriptor createChallenge(String clientId, String callbackUrl, int minAge) {
        byte[] nonceBytes = new byte[24];
        RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);

        String encodedClientId = URLEncoder.encode(clientId != null ? clientId : "keycloak-age-proof", StandardCharsets.UTF_8);
        String encodedResponseUri = URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8);
        String encodedNonce = URLEncoder.encode(nonce, StandardCharsets.UTF_8);

        // OpenID4VP authorization request URI for EUDI Wallet and compliant digital identity wallets
        String openid4vpUri = String.format(
                "openid4vp://authorize?client_id=%s&response_uri=%s&response_type=vp_token&nonce=%s&scope=age_over_%d",
                encodedClientId,
                encodedResponseUri,
                encodedNonce,
                minAge
        );

        return new ChallengeDescriptor(nonce, openid4vpUri, callbackUrl);
    }
}
