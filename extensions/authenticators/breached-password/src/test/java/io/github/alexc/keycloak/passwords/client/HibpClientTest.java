package io.github.alexc.keycloak.passwords.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.alexc.keycloak.passwords.config.BreachedPasswordConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class HibpClientTest {

    private HttpClient mockHttpClient;
    private HibpPrefixCache cache;
    private BreachedPasswordConfig config;
    private HibpClient hibpClient;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        cache = new HibpPrefixCache(100, 60);
        config = new BreachedPasswordConfig(
                "strict",
                1,
                1500,
                true,
                URI.create("https://api.pwnedpasswords.com/range/"),
                100,
                60
        );
        hibpClient = new HibpClient(mockHttpClient, config, cache);
    }

    @Test
    @DisplayName("Detects breached password with matching prefix and suffix")
    void testBreachedPasswordDetection() {
        // "password" -> SHA-1: 5BAA6 1E4C9B93F3F0682250B6CF8331B7EE68FD8
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("1E4C9B93F3F0682250B6CF8331B7EE68FD8:3861493\r\n");

        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        BreachCheckResult result = hibpClient.checkPassword("password");

        assertTrue(result.breached());
        assertEquals(3861493, result.breachCount());
        assertFalse(result.degraded());
    }

    @Test
    @DisplayName("Clean password returns clean result")
    void testCleanPasswordDetection() {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n");

        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        BreachCheckResult result = hibpClient.checkPassword("password");

        assertFalse(result.breached());
        assertEquals(0, result.breachCount());
        assertFalse(result.degraded());
    }

    @Test
    @DisplayName("Prefix cache prevents duplicate outbound HTTP queries")
    void testPrefixCacheAvoidsDuplicateRequests() {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("1E4C9B93F3F0682250B6CF8331B7EE68FD8:50\r\n");

        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        BreachCheckResult firstResult = hibpClient.checkPassword("password");
        BreachCheckResult secondResult = hibpClient.checkPassword("password");

        assertTrue(firstResult.breached());
        assertTrue(secondResult.breached());

        // HTTP call should have been made only once due to prefix cache
        verify(mockHttpClient, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("HTTP 404 response returns clean without errors")
    void testHttp404ReturnsClean() {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(404);

        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        BreachCheckResult result = hibpClient.checkPassword("rare_password_never_seen_123");
        assertFalse(result.breached());
        assertFalse(result.degraded());
    }

    @Test
    @DisplayName("HTTP 500 error triggers fail-open degraded mode when failOpen is true")
    void testHttp500FailOpen() {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);

        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        BreachCheckResult result = hibpClient.checkPassword("any_password");
        assertFalse(result.breached());
        assertTrue(result.degraded());
    }

    @Test
    @DisplayName("Network exception triggers fail-open degraded mode")
    void testNetworkExceptionFailOpen() {
        CompletableFuture<HttpResponse<String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new java.net.ConnectException("Connection refused"));

        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(failedFuture);

        BreachCheckResult result = hibpClient.checkPassword("any_password");
        assertFalse(result.breached());
        assertTrue(result.degraded());
    }

    @Test
    @DisplayName("Null or empty password returns clean immediately without HTTP request")
    void testNullOrEmptyPassword() {
        BreachCheckResult resultNull = hibpClient.checkPassword(null);
        BreachCheckResult resultEmpty = hibpClient.checkPassword("");

        assertFalse(resultNull.breached());
        assertFalse(resultEmpty.breached());
        verify(mockHttpClient, times(0)).sendAsync(any(), any());
    }
}
