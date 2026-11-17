package com.unilink.security;
import jakarta.servlet.*; import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException; import java.util.Set;
/** Must-change-password restriction (D-094). When a login sets the session
 *  attribute {@link #SESSION_ATTRIBUTE} — because the credential in use is a
 *  temporary password an admin issued — every API call is refused with 403
 *  {@code PASSWORD_CHANGE_REQUIRED} except the escape hatches below, until
 *  the password is changed (changing it invalidates the session, which
 *  clears the attribute). The check is a session attribute, not a principal
 *  field, so no serialised principal shape changes and the restriction
 *  cannot outlive the session that created it.
 *  Allowed while restricted: the login endpoints themselves (re-login),
 *  the change-password endpoints, logout, session restore (/me, so the
 *  frontend can learn the flag after a reload), CSRF priming, the health
 *  probe, the admin TOTP/email-OTP steps (an admin must finish MFA before
 *  the admin change-password endpoint will accept them), and the
 *  forgot-password request/confirm (the escape hatch for an expired
 *  temporary password, which rejects login outright). Everything else —
 *  including /ws — is blocked. */
public class MustChangePasswordFilter extends OncePerRequestFilter {
  public static final String SESSION_ATTRIBUTE = "MUST_CHANGE_PASSWORD";
  public static final String CODE = "PASSWORD_CHANGE_REQUIRED";
  static final Set<String> ALLOWED = Set.of(
    "GET /api/csrf", "GET /api/health",
    "POST /api/auth/login", "GET /api/auth/me",
    "POST /api/auth/logout", "POST /api/auth/change-password",
    "POST /api/auth/password-reset/request", "POST /api/auth/password-reset/confirm",
    "POST /api/admin/auth/login", "GET /api/admin/auth/me",
    "POST /api/admin/auth/logout", "POST /api/admin/auth/change-password",
    "POST /api/admin/auth/totp/enrol", "POST /api/admin/auth/totp/verify",
    "POST /api/admin/auth/email-otp/request", "POST /api/admin/auth/email-otp/verify");
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    HttpSession s = req.getSession(false);
    if (s==null || !Boolean.TRUE.equals(s.getAttribute(SESSION_ATTRIBUTE))
        || ALLOWED.contains(req.getMethod()+" "+req.getRequestURI())){
      chain.doFilter(req, res); return;
    }
    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
    res.setContentType("application/json");
    String path = req.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\"");
    res.getWriter().write("{\"status\":403,\"code\":\"" + CODE
      + "\",\"message\":\"You must change your password before using UniLink.\",\"path\":\"" + path + "\"}");
  }
}
