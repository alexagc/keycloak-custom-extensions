package io.github.alexc.keycloak.ageproofing.crypto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PublicTrustProviderResolverTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private PublicTrustProviderResolver resolver;
    private RSAPublicKey testRsaKey;
    private String testJwksJson;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        resolver = new PublicTrustProviderResolver(httpClient);

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        testRsaKey = (RSAPublicKey) keyPair.getPublic();

        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(testRsaKey.getModulus()));
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(testRsaKey.getPublicExponent()));

        testJwksJson = String.format("""
                {
                  "keys": [
                    {
                      "kty": "RSA",
                      "kid": "key-2026-01",
                      "use": "sig",
                      "alg": "RS256",
                      "n": "%s",
                      "e": "%s"
                    }
                  ]
                }
                """, n, e);
    }

    private static byte[] toUnsignedByteArray(BigInteger bi) {
        byte[] bytes = bi.toByteArray();
        if (bytes[0] == 0) {
            byte[] tmp = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, tmp, 0, tmp.length);
            return tmp;
        }
        return bytes;
    }

    @Test
    void testResolvePublicKeySuccessfulAndCached() throws Exception {
        URI jwksUri = URI.create("https://identity.gov.example/jwks.json");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(testJwksJson);
        when(httpClient.send(any(HttpRequest.class), any())).thenReturn(httpResponse);

        // First call: fetches from remote
        PublicKey key1 = resolver.resolvePublicKey(jwksUri, "key-2026-01", 3000, 60);
        assertNotNull(key1);
        assertEquals("RSA", key1.getAlgorithm());

        // Second call: served from in-memory cache, no second HTTP call
        PublicKey key2 = resolver.resolvePublicKey(jwksUri, "key-2026-01", 3000, 60);
        assertNotNull(key2);
        assertSame(key1, key2);

        verify(httpClient, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    void testResolvePublicKeyWithoutKidReturnsDefaultKey() throws Exception {
        URI jwksUri = URI.create("https://identity.gov.example/jwks.json");
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(testJwksJson);
        when(httpClient.send(any(HttpRequest.class), any())).thenReturn(httpResponse);

        PublicKey key = resolver.resolvePublicKey(jwksUri, null, 3000, 60);
        assertNotNull(key);
        assertEquals("RSA", key.getAlgorithm());
    }

    @Test
    void testResolvePublicKeyHttpErrorReturnsNull() throws Exception {
        URI jwksUri = URI.create("https://identity.gov.example/jwks.json");
        when(httpResponse.statusCode()).thenReturn(503);
        when(httpClient.send(any(HttpRequest.class), any())).thenReturn(httpResponse);

        PublicKey key = resolver.resolvePublicKey(jwksUri, "key-2026-01", 3000, 60);
        assertNull(key);
    }

    @Test
    void testResolvePublicKeyNetworkIOExceptionReturnsNull() throws Exception {
        URI jwksUri = URI.create("https://identity.gov.example/jwks.json");
        when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new IOException("Connection timed out"));

        PublicKey key = resolver.resolvePublicKey(jwksUri, "key-2026-01", 3000, 60);
        assertNull(key);
    }

    @Test
    void testResolvePublicKeyNullUriReturnsNull() {
        PublicKey key = resolver.resolvePublicKey(null, "kid-1", 3000, 60);
        assertNull(key);
    }
}
