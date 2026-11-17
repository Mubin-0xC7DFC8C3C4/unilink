package com.unilink.security;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
/** IpResolver (D-100): X-Forwarded-For is honoured only from a configured
 *  trusted proxy; otherwise the direct remote address is used. */
class IpResolverTest {
  private static MockHttpServletRequest req(String remote, String xff){
    MockHttpServletRequest r = new MockHttpServletRequest();
    r.setRemoteAddr(remote);
    if (xff != null) r.addHeader("X-Forwarded-For", xff);
    return r;
  }
  @Test void noForwardedHeaderReturnsRemoteAddr(){
    assertEquals("203.0.113.9", new IpResolver("127.0.0.1,::1").resolve(req("203.0.113.9", null)));
  }
  @Test void trustedProxyForwardsFirstClientIp(){
    assertEquals("203.0.113.7",
      new IpResolver("127.0.0.1,::1").resolve(req("127.0.0.1", "203.0.113.7, 10.0.0.1")));
  }
  @Test void trustedIpv6LoopbackForwardsClientIp(){
    assertEquals("198.51.100.3",
      new IpResolver("127.0.0.1,::1").resolve(req("::1", "198.51.100.3")));
    assertEquals("198.51.100.3",
      new IpResolver("127.0.0.1,::1").resolve(req("0:0:0:0:0:0:0:1", "198.51.100.3")));
  }
  @Test void untrustedProxyCannotSpoofClientIp(){
    // The spoofed header must be ignored: the rate-limit key stays the
    // direct peer, so a client cannot dodge a per-IP cap by asserting a
    // fresh X-Forwarded-For on every request.
    assertEquals("203.0.113.9",
      new IpResolver("127.0.0.1,::1").resolve(req("203.0.113.9", "198.51.100.3")));
  }
  @Test void unknownAndInvalidForwardedEntriesFallBackToRemote(){
    IpResolver r = new IpResolver("127.0.0.1,::1");
    assertEquals("198.51.100.3", r.resolve(req("127.0.0.1", "unknown, 198.51.100.3")));
    assertEquals("127.0.0.1", r.resolve(req("127.0.0.1", "not-an-ip")));
    assertEquals("127.0.0.1", r.resolve(req("127.0.0.1", "unknown")));
    assertEquals("127.0.0.1", r.resolve(req("127.0.0.1", "")));
  }
  @Test void bracketedIpv6AndIpv4PortAreAccepted(){
    IpResolver r = new IpResolver("127.0.0.1,::1");
    assertEquals("2001:db8::1", r.resolve(req("127.0.0.1", "[2001:db8::1]")));
    assertEquals("203.0.113.7", r.resolve(req("127.0.0.1", "203.0.113.7:1234")));
  }
  @Test void trustedProxyListIsConfigurable(){
    IpResolver r = new IpResolver("10.0.0.5");
    assertEquals("198.51.100.3", r.resolve(req("10.0.0.5", "198.51.100.3")));
    assertEquals("10.0.0.6", r.resolve(req("10.0.0.6", "198.51.100.3")));
    assertEquals("127.0.0.1", r.resolve(req("127.0.0.1", "198.51.100.3")),
      "127.0.0.1 is not trusted once the list is overridden");
  }
  @Test void hostnameInForwardedHeaderIsNotResolveded(){
    // A hostname must never trigger a DNS lookup from an
    // attacker-controlled header value; it falls back to the remote addr.
    assertEquals("127.0.0.1",
      new IpResolver("127.0.0.1,::1").resolve(req("127.0.0.1", "evil.example.com")));
  }
}
