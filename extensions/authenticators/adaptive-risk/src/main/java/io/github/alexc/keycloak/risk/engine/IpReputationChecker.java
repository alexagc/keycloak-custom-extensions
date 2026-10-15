package io.github.alexc.keycloak.risk.engine;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * Checks client IP reputation against known TOR exit nodes, anonymous proxies, and private network ranges.
 */
public final class IpReputationChecker {

  private static final Logger LOG = Logger.getLogger(IpReputationChecker.class);
  private static final String TOR_EXIT_NODES_RESOURCE = "tor-exit-nodes.txt";

  private final Set<String> torExitNodes;

  /**
   * Reputation evaluation result for an IP address.
   *
   * @param ipAddress Target IP
   * @param isTorExitNode Whether the IP matches a known TOR exit node
   * @param isPrivateOrLocal Whether the IP belongs to a private, loopback, or link-local range
   * @param riskScore Calculated risk score contribution [0, 100]
   * @param reason Explanatory rationale
   */
  public record ReputationResult(
      String ipAddress,
      boolean isTorExitNode,
      boolean isPrivateOrLocal,
      int riskScore,
      String reason) {

    public static ReputationResult clean(String ipAddress) {
      return new ReputationResult(ipAddress, false, false, 0, "Clean public IP");
    }

    public static ReputationResult privateIp(String ipAddress) {
      return new ReputationResult(ipAddress, false, true, 0, "Private or local network IP");
    }
  }

  /**
   * Constructs an IpReputationChecker loading the default baseline TOR exit nodes.
   */
  public IpReputationChecker() {
    this(loadBaselineTorNodes());
  }

  /**
   * Constructs an IpReputationChecker with explicit TOR exit node addresses.
   *
   * @param torExitNodes Set of TOR exit node IP strings
   */
  public IpReputationChecker(Set<String> torExitNodes) {
    this.torExitNodes = (torExitNodes != null)
        ? Collections.unmodifiableSet(new HashSet<>(torExitNodes))
        : Collections.emptySet();
  }

  /**
   * Checks the reputation of an IP address.
   *
   * @param ipAddress IP address string to check
   * @param checkTor Whether to check against TOR exit nodes
   * @param torPenalty Risk points to assign if identified as a TOR exit node
   * @return ReputationResult record
   */
  public ReputationResult checkReputation(String ipAddress, boolean checkTor, int torPenalty) {
    if (ipAddress == null || ipAddress.isBlank()) {
      return new ReputationResult("unknown", false, false, 0, "Unknown IP address");
    }

    String cleanIp = ipAddress.trim();

    if (isPrivateOrLocal(cleanIp)) {
      return ReputationResult.privateIp(cleanIp);
    }

    if (checkTor && torExitNodes.contains(cleanIp)) {
      LOG.warnv("Authentication attempt originated from known TOR exit node: {0}", cleanIp);
      return new ReputationResult(cleanIp, true, false, torPenalty, "Identified as TOR exit node");
    }

    return ReputationResult.clean(cleanIp);
  }

  /**
   * Checks if an IP is loopback, link-local, site-local, or private.
   *
   * @param ip Address string
   * @return true if private or local
   */
  public static boolean isPrivateOrLocal(String ip) {
    try {
      InetAddress addr = InetAddress.getByName(ip);
      return addr.isLoopbackAddress()
          || addr.isLinkLocalAddress()
          || addr.isSiteLocalAddress()
          || isCidrPrivate(addr);
    } catch (UnknownHostException e) {
      return false;
    }
  }

  private static boolean isCidrPrivate(InetAddress addr) {
    byte[] bytes = addr.getAddress();
    if (bytes.length == 4) {
      // IPv4 private ranges: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
      int b0 = bytes[0] & 0xFF;
      int b1 = bytes[1] & 0xFF;
      if (b0 == 10) {
        return true;
      }
      if (b0 == 172 && (b1 >= 16 && b1 <= 31)) {
        return true;
      }
      if (b0 == 192 && b1 == 168) {
        return true;
      }
      // 100.64.0.0/10 (Carrier-grade NAT)
      if (b0 == 100 && (b1 >= 64 && b1 <= 127)) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> loadBaselineTorNodes() {
    Set<String> nodes = new HashSet<>();
    try (InputStream is = IpReputationChecker.class.getClassLoader().getResourceAsStream(TOR_EXIT_NODES_RESOURCE)) {
      if (is != null) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
              nodes.add(trimmed);
            }
          }
        }
      }
    } catch (Exception e) {
      LOG.warnv("Unable to load baseline TOR exit nodes from classpath resource: {0}", e.getMessage());
    }
    return nodes;
  }
}
