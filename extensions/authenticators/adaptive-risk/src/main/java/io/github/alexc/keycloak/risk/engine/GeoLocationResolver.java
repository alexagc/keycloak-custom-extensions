package io.github.alexc.keycloak.risk.engine;

import io.github.alexc.keycloak.risk.model.GeoLocation;
import java.util.function.Function;
import org.jboss.logging.Logger;
import org.keycloak.http.HttpRequest;

/**
 * Resolves client IP address and geographic location coordinates from incoming HTTP request headers
 * (Cloudflare, AWS CloudFront, standard reverse proxy headers).
 */
public final class GeoLocationResolver {

  private static final Logger LOG = Logger.getLogger(GeoLocationResolver.class);

  // Common reverse proxy IP headers
  public static final String HEADER_CF_CONNECTING_IP = "CF-Connecting-IP";
  public static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
  public static final String HEADER_X_REAL_IP = "X-Real-IP";

  // Common GeoIP headers
  public static final String HEADER_CF_IP_LATITUDE = "CF-IPLatitude";
  public static final String HEADER_CF_IP_LONGITUDE = "CF-IPLongitude";
  public static final String HEADER_CF_IP_COUNTRY = "CF-IPCountry";
  public static final String HEADER_CF_IP_CITY = "CF-IPCity";

  public static final String HEADER_X_GEOIP_LAT = "X-GeoIP-Lat";
  public static final String HEADER_X_GEOIP_LON = "X-GeoIP-Lon";
  public static final String HEADER_X_GEOIP_COUNTRY = "X-GeoIP-Country";
  public static final String HEADER_X_GEOIP_CITY = "X-GeoIP-City";

  public static final String HEADER_USER_AGENT = "User-Agent";
  public static final String HEADER_ACCEPT_LANGUAGE = "Accept-Language";

  private GeoLocationResolver() {
    // Utility class
  }

  /**
   * Resolves the client IP address from Keycloak HttpRequest and connection fallback.
   *
   * @param request Keycloak HttpRequest
   * @param remoteAddr Connection remote IP address fallback
   * @return Resolved client IP string
   */
  public static String resolveClientIp(HttpRequest request, String remoteAddr) {
    if (request == null || request.getHttpHeaders() == null) {
      return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "127.0.0.1";
    }
    return resolveClientIp(headerName -> request.getHttpHeaders().getHeaderString(headerName), remoteAddr);
  }

  /**
   * Resolves the client IP address from a header supplier function.
   *
   * @param headerLookup Header lookup function
   * @param remoteAddr Connection remote address fallback
   * @return Resolved client IP string
   */
  public static String resolveClientIp(Function<String, String> headerLookup, String remoteAddr) {
    if (headerLookup != null) {
      String cfIp = headerLookup.apply(HEADER_CF_CONNECTING_IP);
      if (isValidIp(cfIp)) {
        return cfIp.trim();
      }

      String xRealIp = headerLookup.apply(HEADER_X_REAL_IP);
      if (isValidIp(xRealIp)) {
        return xRealIp.trim();
      }

      String xff = headerLookup.apply(HEADER_X_FORWARDED_FOR);
      if (xff != null && !xff.isBlank()) {
        String[] parts = xff.split(",");
        if (parts.length > 0 && isValidIp(parts[0])) {
          return parts[0].trim();
        }
      }
    }

    return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "127.0.0.1";
  }

  /**
   * Resolves GeoLocation from Keycloak HttpRequest and remote address.
   *
   * @param request Keycloak HttpRequest
   * @param remoteAddr Connection remote address fallback
   * @return Resolved GeoLocation record
   */
  public static GeoLocation resolveLocation(HttpRequest request, String remoteAddr) {
    String clientIp = resolveClientIp(request, remoteAddr);
    if (request == null || request.getHttpHeaders() == null) {
      return GeoLocation.empty(clientIp);
    }
    return resolveLocation(headerName -> request.getHttpHeaders().getHeaderString(headerName), clientIp);
  }

  /**
   * Resolves GeoLocation using a header supplier function.
   *
   * @param headerLookup Header lookup function
   * @param clientIp Already resolved client IP
   * @return Resolved GeoLocation record
   */
  public static GeoLocation resolveLocation(Function<String, String> headerLookup, String clientIp) {
    if (headerLookup == null) {
      return GeoLocation.empty(clientIp);
    }

    Double lat = parseDouble(firstNonBlank(headerLookup.apply(HEADER_CF_IP_LATITUDE), headerLookup.apply(HEADER_X_GEOIP_LAT)));
    Double lon = parseDouble(firstNonBlank(headerLookup.apply(HEADER_CF_IP_LONGITUDE), headerLookup.apply(HEADER_X_GEOIP_LON)));
    String country = firstNonBlank(headerLookup.apply(HEADER_CF_IP_COUNTRY), headerLookup.apply(HEADER_X_GEOIP_COUNTRY));
    String city = firstNonBlank(headerLookup.apply(HEADER_CF_IP_CITY), headerLookup.apply(HEADER_X_GEOIP_CITY));

    if (lat != null && lon != null) {
      // Validate bounds
      if (lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0) {
        return new GeoLocation(lat, lon, country, city, clientIp);
      } else {
        LOG.warnv("Invalid coordinates received in headers: lat={0}, lon={1}", lat, lon);
      }
    }

    return new GeoLocation(null, null, country, city, clientIp);
  }

  private static boolean isValidIp(String ip) {
    return ip != null && !ip.isBlank() && !ip.equalsIgnoreCase("unknown");
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first.trim();
    }
    if (second != null && !second.isBlank()) {
      return second.trim();
    }
    return null;
  }

  private static Double parseDouble(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
