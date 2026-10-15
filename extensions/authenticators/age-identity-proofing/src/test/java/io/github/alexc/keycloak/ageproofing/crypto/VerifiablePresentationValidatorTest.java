package io.github.alexc.keycloak.ageproofing.crypto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.alexc.keycloak.ageproofing.model.AgeVerificationConfig;
import io.github.alexc.keycloak.ageproofing.model.VerificationEvidence;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class VerifiablePresentationValidatorTest {

    @Mock
    private PublicTrustProviderResolver keyResolver;

    private VerifiablePresentationValidator validator;
    private KeyPair rsaKeyPair;
    private KeyPair ecKeyPair;
    private AgeVerificationConfig defaultConfig;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        validator = new VerifiablePresentationValidator(keyResolver);

        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048);
        rsaKeyPair = rsaGen.generateKeyPair();

        KeyPairGenerator ecGen = KeyPairGenerator.getInstance("EC");
        ecGen.initialize(new ECGenParameterSpec("secp256r1"));
        ecKeyPair = ecGen.generateKeyPair();

        defaultConfig = new AgeVerificationConfig(
                18,
                URI.create("https://identity.gov.example/jwks.json"),
                365,
                Set.of(),
                "hybrid",
                false,
                null,
                3000,
                60
        );

        when(keyResolver.resolvePublicKey(any(), eq("rsa-key-1"), anyInt(), anyInt()))
                .thenReturn(rsaKeyPair.getPublic());
        when(keyResolver.resolvePublicKey(any(), eq("ec-key-1"), anyInt(), anyInt()))
                .thenReturn(ecKeyPair.getPublic());
    }

    private static String createJws(String headerJson, String payloadJson, PrivateKey privateKey, String alg) throws Exception {
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedPayload;

        Signature sig = switch (alg) {
            case "RS256" -> Signature.getInstance("SHA256withRSA");
            case "ES256" -> Signature.getInstance("SHA256withECDSAinP1363Format");
            default -> throw new IllegalArgumentException("Unsupported test alg: " + alg);
        };

        sig.initSign(privateKey);
        sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        byte[] signatureBytes = sig.sign();
        String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

        return signingInput + "." + encodedSignature;
    }

    @Test
    void testValidRsaAssertionSuccess() throws Exception {
        String header = """
                {"alg":"RS256","kid":"rsa-key-1","typ":"JWT"}
                """;
        String payload = """
                {
                  "iss": "https://clave.gob.es",
                  "sub": "user-anon-123",
                  "age_over_18": true,
                  "exp": %d
                }
                """.formatted(Instant.now().plusSeconds(300).getEpochSecond());

        String token = createJws(header, payload, rsaKeyPair.getPrivate(), "RS256");

        VerificationEvidence evidence = validator.validate(token, defaultConfig);

        assertTrue(evidence.success());
        assertEquals("https://clave.gob.es", evidence.issuer());
        assertNotNull(evidence.proofHash());
        assertEquals(64, evidence.proofHash().length());
        assertNull(evidence.errorMessage());
    }

    @Test
    void testValidEcAssertionSuccess() throws Exception {
        String header = """
                {"alg":"ES256","kid":"ec-key-1","typ":"JWT"}
                """;
        String payload = """
                {
                  "iss": "https://wallet.europa.eu",
                  "age_over_18": true,
                  "exp": %d
                }
                """.formatted(Instant.now().plusSeconds(600).getEpochSecond());

        String token = createJws(header, payload, ecKeyPair.getPrivate(), "ES256");

        VerificationEvidence evidence = validator.validate(token, defaultConfig);

        assertTrue(evidence.success());
        assertEquals("https://wallet.europa.eu", evidence.issuer());
    }

    @Test
    void testTamperedSignatureFails() throws Exception {
        String header = """
                {"alg":"RS256","kid":"rsa-key-1"}
                """;
        String payload = """
                {"iss":"https://clave.gob.es","age_over_18":true}
                """;
        String validToken = createJws(header, payload, rsaKeyPair.getPrivate(), "RS256");

        // Tamper signature
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "XXXXX";

        VerificationEvidence evidence = validator.validate(tamperedToken, defaultConfig);

        assertFalse(evidence.success());
        assertTrue(evidence.errorMessage().contains("signature"));
    }

    @Test
    void testExpiredTokenFails() throws Exception {
        String header = """
                {"alg":"RS256","kid":"rsa-key-1"}
                """;
        String payload = """
                {
                  "iss": "https://clave.gob.es",
                  "age_over_18": true,
                  "exp": %d
                }
                """.formatted(Instant.now().minusSeconds(60).getEpochSecond());

        String token = createJws(header, payload, rsaKeyPair.getPrivate(), "RS256");

        VerificationEvidence evidence = validator.validate(token, defaultConfig);

        assertFalse(evidence.success());
        assertTrue(evidence.errorMessage().contains("expired"));
    }

    @Test
    void testNotYetValidTokenFails() throws Exception {
        String header = """
                {"alg":"RS256","kid":"rsa-key-1"}
                """;
        String payload = """
                {
                  "iss": "https://clave.gob.es",
                  "age_over_18": true,
                  "nbf": %d
                }
                """.formatted(Instant.now().plusSeconds(3600).getEpochSecond());

        String token = createJws(header, payload, rsaKeyPair.getPrivate(), "RS256");

        VerificationEvidence evidence = validator.validate(token, defaultConfig);

        assertFalse(evidence.success());
        assertTrue(evidence.errorMessage().contains("not yet valid"));
    }

    @Test
    void testAllowedIssuersEnforcement() throws Exception {
        AgeVerificationConfig strictIssuerConfig = new AgeVerificationConfig(
                18,
                URI.create("https://identity.gov.example/jwks.json"),
                365,
                Set.of("https://trusted-government.gov"),
                "hybrid",
                false,
                null,
                3000,
                60
        );

        String header = """
                {"alg":"RS256","kid":"rsa-key-1"}
                """;
        String payload = """
                {"iss":"https://untrusted-issuer.com","age_over_18":true}
                """;
        String token = createJws(header, payload, rsaKeyPair.getPrivate(), "RS256");

        VerificationEvidence evidence = validator.validate(token, strictIssuerConfig);

        assertFalse(evidence.success());
        assertTrue(evidence.errorMessage().contains("not permitted"));
    }

    @Test
    void testUnderageAssertionFails() throws Exception {
        String header = """
                {"alg":"RS256","kid":"rsa-key-1"}
                """;
        String payload = """
                {"iss":"https://clave.gob.es","age_over_18":false}
                """;
        String token = createJws(header, payload, rsaKeyPair.getPrivate(), "RS256");

        VerificationEvidence evidence = validator.validate(token, defaultConfig);

        assertFalse(evidence.success());
        assertTrue(evidence.errorMessage().contains("minimum age"));
    }

    @Test
    void testNestedVerifiableCredentialClaims() throws Exception {
        String header = """
                {"alg":"RS256","kid":"rsa-key-1"}
                """;
        String payload = """
                {
                  "iss": "https://wallet.europa.eu",
                  "vc": {
                    "credentialSubject": {
                      "age_over_18": true
                    }
                  }
                }
                """;
        String token = createJws(header, payload, rsaKeyPair.getPrivate(), "RS256");

        VerificationEvidence evidence = validator.validate(token, defaultConfig);

        assertTrue(evidence.success());
    }

    @Test
    void testFailOpenWhenKeyResolutionFails() throws Exception {
        AgeVerificationConfig failOpenConfig = new AgeVerificationConfig(
                18,
                URI.create("https://offline-jwks.example/jwks.json"),
                365,
                Set.of(),
                "hybrid",
                true,
                null,
                3000,
                60
        );

        when(keyResolver.resolvePublicKey(eq(URI.create("https://offline-jwks.example/jwks.json")), any(), anyInt(), anyInt()))
                .thenReturn(null);

        String header = """
                {"alg":"RS256","kid":"missing-key"}
                """;
        String payload = """
                {"iss":"https://clave.gob.es","age_over_18":true}
                """;
        String token = createJws(header, payload, rsaKeyPair.getPrivate(), "RS256");

        VerificationEvidence evidence = validator.validate(token, failOpenConfig);

        assertTrue(evidence.success());
        assertEquals("degraded-fail-open", evidence.issuer());
    }

    @Test
    void testBlankTokenReturnsFailure() {
        VerificationEvidence evidence = validator.validate("   ", defaultConfig);
        assertFalse(evidence.success());
        assertTrue(evidence.errorMessage().contains("Empty or blank"));
    }
}
