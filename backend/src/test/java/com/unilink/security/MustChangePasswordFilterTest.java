package com.unilink.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** D-094: the must-change-password filter blocks every API call except the
 *  escape hatches while the session carries the flag, and passes everything
 *  through when the flag is absent. Plain unit test — no Spring context. */
class MustChangePasswordFilterTest {
  private final MustChangePasswordFilter filter = new MustChangePasswordFilter();

  private MockHttpServletRequest request(String method, String uri, boolean flagged) {
    MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
    if (flagged) {
      MockHttpSession session = new MockHttpSession();
      session.setAttribute(MustChangePasswordFilter.SESSION_ATTRIBUTE, Boolean.TRUE);
      req.setSession(session);
    }
    return req;
  }

  private boolean run(String method, String uri, boolean flagged) throws Exception {
    MockHttpServletRequest req = request(method, uri, flagged);
    MockHttpServletResponse res = new MockHttpServletResponse();
    AtomicBoolean invoked = new AtomicBoolean(false);
    FilterChain chain = (rq, rs) -> invoked.set(true);
    filter.doFilter(req, res, chain);
    if (!invoked.get()) {
      assertEquals(403, res.getStatus());
      assertTrue(res.getContentAsString().contains(MustChangePasswordFilter.CODE),
        "blocked response must carry " + MustChangePasswordFilter.CODE + ": " + res.getContentAsString());
    }
    return invoked.get();
  }

  @Test void unflaggedSessionPassesEverything() throws Exception {
    assertTrue(run("GET", "/api/feed", false));
    assertTrue(run("POST", "/api/posts", false));
  }

  @Test void flaggedSessionBlocksOrdinaryApiCalls() throws Exception {
    assertFalse(run("GET", "/api/feed", true));
    assertFalse(run("POST", "/api/posts", true));
    assertFalse(run("GET", "/api/admin/dashboard", true));
    assertFalse(run("GET", "/ws/websocket", true));
  }

  @Test void flaggedSessionAllowsChangePasswordLogoutAndMe() throws Exception {
    assertTrue(run("POST", "/api/auth/change-password", true));
    assertTrue(run("POST", "/api/auth/logout", true));
    assertTrue(run("GET", "/api/auth/me", true));
    assertTrue(run("POST", "/api/admin/auth/change-password", true));
    assertTrue(run("POST", "/api/admin/auth/logout", true));
    assertTrue(run("GET", "/api/admin/auth/me", true));
  }

  @Test void flaggedSessionAllowsAuthStepsAndPublicProbes() throws Exception {
    assertTrue(run("POST", "/api/auth/login", true));
    assertTrue(run("POST", "/api/admin/auth/login", true));
    assertTrue(run("POST", "/api/admin/auth/totp/verify", true));
    assertTrue(run("POST", "/api/admin/auth/totp/enrol", true));
    assertTrue(run("POST", "/api/admin/auth/email-otp/verify", true));
    assertTrue(run("POST", "/api/auth/password-reset/confirm", true));
    assertTrue(run("GET", "/api/csrf", true));
    assertTrue(run("GET", "/api/health", true));
  }
}
