package com.unilink.security;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.InetAddress; import java.util.*;
import java.util.regex.Pattern;
/** Resolves the client IP for rate limiting and audit logging (D-100).
 *
 *  <p>{@code X-Forwarded-For} is honoured ONLY when the direct peer
 *  ({@code getRemoteAddr()}) is a configured trusted proxy — localhost /
 *  127.0.0.1 by default, configurable via
 *  {@code app.security.trusted-proxies} (comma-separated, env
 *  {@code TRUSTED_PROXIES}). Otherwise the header is ignored entirely and
 *  the direct remote address is used, so a client cannot spoof its way
 *  around a per-IP rate limit or forge the IP recorded in the audit log.
 *  This replaces the previous behaviour in {@code AdminController}, which
 *  trusted {@code X-Forwarded-For} unconditionally, and the raw
 *  {@code getRemoteAddr()} calls in the auth controllers, which ignored
 *  the header even behind a legitimate reverse proxy (e.g. the nginx
 *  front-end in D-1, which terminates on 127.0.0.1).
 *
 *  <p>Rules: the first {@code X-Forwarded-For} entry is the client (any
 *  later entries are proxies the chain passed through). Entries of
 *  {@code unknown}, blanks and values that are not IP literals are skipped;
 *  if no usable entry remains, the direct remote address is returned.
 *  Trusted-proxy entries are matched as parsed IP addresses, so
 *  {@code ::1} and {@code 0:0:0:0:0:0:0:1} are the same proxy. Only exact
 *  addresses are supported (no CIDR ranges yet — a deployment that needs
 *  a proxy range should extend this class, not bypass it). */
@Component
public class IpResolver {
  private static final Logger log = LoggerFactory.getLogger(IpResolver.class);
  private static final Pattern IPV4 = Pattern.compile(
    "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
  private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:]+$");
  private final Set<InetAddress> trustedProxies;

  public IpResolver(@Value("${app.security.trusted-proxies:127.0.0.1,::1}") String trustedProxies){
    Set<InetAddress> set = new HashSet<>();
    for (String raw : trustedProxies.split(",")){
      String entry = raw.trim();
      if (entry.isEmpty()) continue;
      try { set.add(InetAddress.getByName(entry)); }
      catch (Exception e){ log.warn("Ignoring unparseable trusted-proxy entry '{}': {}", entry, e.getMessage()); }
    }
    this.trustedProxies = Set.copyOf(set);
  }

  /** Client IP for the request: forwarded client when (and only when) the
   *  direct peer is a trusted proxy, otherwise the direct remote address. */
  public String resolve(HttpServletRequest req){
    String remote = req.getRemoteAddr();
    if (remote == null) return "unknown";
    if (!isTrustedProxy(remote)) return remote;
    String xff = req.getHeader("X-Forwarded-For");
    if (xff == null || xff.isBlank()) return remote;
    for (String part : xff.split(",")){
      String candidate = part.trim();
      if (candidate.isEmpty() || "unknown".equalsIgnoreCase(candidate)) continue;
      String literal = ipLiteral(candidate);
      if (literal != null) return literal;
    }
    return remote;
  }

  boolean isTrustedProxy(String remote){
    try { return trustedProxies.contains(InetAddress.getByName(remote)); }
    catch (Exception e){ return false; }
  }

  /** Returns the value as a bare IP literal, or null when it is not one.
   *  Accepts bracketed IPv6 ({@code [::1]}) and an IPv4 {@code :port}
   *  suffix, both of which some proxies append; anything else (notably a
   *  hostname, which would need a DNS lookup to validate) is rejected so
   *  no attacker-controlled value can trigger resolution. */
  static String ipLiteral(String value){
    String v = value.trim();
    if (v.startsWith("[") && v.endsWith("]")) v = v.substring(1, v.length() - 1);
    if (IPV4.matcher(v).matches()) return v;
    int colon = v.lastIndexOf(':');
    if (colon > 0 && IPV4.matcher(v.substring(0, colon)).matches()
        && v.substring(colon + 1).matches("\\d{1,5}")) return v.substring(0, colon);
    if (v.contains(":") && IPV6_CHARS.matcher(v).matches()){
      try { InetAddress.getByName(v); return v; } catch (Exception ignored) { return null; }
    }
    return null;
  }
}
