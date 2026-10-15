package io.github.alexc.keycloak.risk.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alexc.keycloak.risk.model.GeoLocation;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeoLocationResolverTest {

  @Test
  @DisplayName("Should extract client IP from CF-Connecting-IP header first")
  void testExtractCfConnectingIp() {
    Map<String, String> headers = Map.of(
        GeoLocationResolver.HEADER_CF_CONNECTING_IP, "203.0.113.195",
        GeoLocationResolver.HEADER_X_FORWARDED_FOR, "198.51.100.1, 10.0.0.1",
        GeoLocationResolver.HEADER_X_REAL_IP, "198.51.100.2"
    );

    String ip = GeoLocationResolver.resolveClientIp(headers::get, "127.0.0.1");
    assertEquals("203.0.113.195", ip);
  }

  @Test
  @DisplayName("Should fall back to first IP in X-Forwarded-For chain when CF-Connecting-IP is absent")
  void testExtractXForwardedFor() {
    Map<String, String> headers = Map.of(
        GeoLocationResolver.HEADER_X_FORWARDED_FOR, "198.51.100.99, 10.0.0.2"
    );

    String ip = GeoLocationResolver.resolveClientIp(headers::get, "127.0.0.1");
    assertEquals("198.51.100.99", ip);
  }

  @Test
  @DisplayName("Should fall back to remoteAddr when headers are empty")
  void testFallbackToRemoteAddr() {
    String ip = GeoLocationResolver.resolveClientIp(header -> null, "172.20.0.5");
    assertEquals("172.20.0.5", ip);
  }

  @Test
  @DisplayName("Should parse coordinates and locality from Cloudflare GeoIP headers")
  void testParseCloudflareGeoHeaders() {
    Map<String, String> headers = Map.of(
        GeoLocationResolver.HEADER_CF_IP_LATITUDE, "40.4168",
        GeoLocationResolver.HEADER_CF_IP_LONGITUDE, "-3.7038",
        GeoLocationResolver.HEADER_CF_IP_COUNTRY, "ES",
        GeoLocationResolver.HEADER_CF_IP_CITY, "Madrid"
    );

    GeoLocation loc = GeoLocationResolver.resolveLocation(headers::get, "1.1.1.1");

    assertTrue(loc.hasCoordinates());
    assertEquals(40.4168, loc.latitude());
    assertEquals(-3.7038, loc.longitude());
    assertEquals("ES", loc.countryCode());
    assertEquals("Madrid", loc.cityName());
  }

  @Test
  @DisplayName("Should parse coordinates from X-GeoIP-* headers as fallback")
  void testParseXGeoIpHeaders() {
    Map<String, String> headers = Map.of(
        GeoLocationResolver.HEADER_X_GEOIP_LAT, "41.3879",
        GeoLocationResolver.HEADER_X_GEOIP_LON, "2.1699",
        GeoLocationResolver.HEADER_X_GEOIP_COUNTRY, "ES",
        GeoLocationResolver.HEADER_X_GEOIP_CITY, "Barcelona"
    );

    GeoLocation loc = GeoLocationResolver.resolveLocation(headers::get, "2.2.2.2");

    assertTrue(loc.hasCoordinates());
    assertEquals(41.3879, loc.latitude());
    assertEquals(2.1699, loc.longitude());
    assertEquals("Barcelona", loc.cityName());
  }

  @Test
  @DisplayName("Should discard out-of-range coordinates gracefully")
  void testDiscardInvalidCoordinates() {
    Map<String, String> headers = Map.of(
        GeoLocationResolver.HEADER_CF_IP_LATITUDE, "195.0000", // Invalid latitude (> 90)
        GeoLocationResolver.HEADER_CF_IP_LONGITUDE, "2.1699"
    );

    GeoLocation loc = GeoLocationResolver.resolveLocation(headers::get, "2.2.2.2");

    assertFalse(loc.hasCoordinates());
    assertNull(loc.latitude());
  }

  @Test
  @DisplayName("Should return empty coordinates when headers are absent")
  void testMissingGeoHeaders() {
    GeoLocation loc = GeoLocationResolver.resolveLocation(header -> null, "192.168.1.1");

    assertFalse(loc.hasCoordinates());
    assertEquals("192.168.1.1", loc.ipAddress());
  }
}
