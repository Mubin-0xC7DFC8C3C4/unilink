package com.unilink.admin.api;
import com.unilink.admin.AdminAuthService; import com.unilink.common.Exceptions; import com.unilink.identity.dto.AuthDtos.ChangePasswordRequest;
import com.unilink.security.IpResolver; import com.unilink.security.MustChangePasswordFilter; import com.unilink.security.Principals;
import jakarta.servlet.http.*; import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import java.util.*;
/** Admin auth (D-031): password login -> TOTP enrolment at first login -> TOTP/recovery verify -> MFA_VERIFIED session. */
@RestController @RequestMapping("/api/admin/auth")
public class AdminAuthController {
  private final AdminAuthService auth; private final IpResolver ipResolver;
  private final HttpSessionSecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();
  public AdminAuthController(AdminAuthService a, IpResolver ipResolver){ auth=a; this.ipResolver=ipResolver; }
  public record LoginBody(String email, String password) {}
  public record CodeBody(String code) {}
  public record EmailOtpBody(String otp) {}
  private void saveAuth(HttpServletRequest req, HttpServletResponse res, Principals.AdminPrincipal principal){
    Authentication a = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(a);
    HttpSession s = req.getSession(true);
    s.setAttribute("ADMIN_LAST_ACTIVE", System.currentTimeMillis());
    contextRepo.saveContext(SecurityContextHolder.getContext(), req, res);
  }
  private Map<String,Object> principalDto(Principals.AdminPrincipal p){
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("adminId", p.adminId()); m.put("email", p.getUsername()); m.put("role", p.role());
    m.put("universityId", p.universityId()==null?"":p.universityId()); m.put("mfaVerified", p.mfaVerified());
    // D-094: session restore (/me) must report the flag so the admin app
    // can redirect to the change-password screen after a reload.
    m.put("mustChangePassword", auth.mustChangePassword(p.adminId()));
    return m;
  }
  /** I-15: /api/admin/auth/** is permitAll (the login flow itself lives here),
   *  so an unauthenticated request reaches this controller with a null
   *  Authentication. The old code cast a.getPrincipal() unconditionally and
   *  threw NPE (500). A missing or non-admin principal is 401, not 500.
   *  (Deliberately not AccessPolicy.requireAdmin: that also demands
   *  mfaVerified, which the enrol/verify caller does not have yet.) */
  private Principals.AdminPrincipal requireAdminPrincipal(Authentication a){
    if (a==null || !(a.getPrincipal() instanceof Principals.AdminPrincipal p))
      throw new Exceptions.Unauthorized("Admin authentication required");
    return p;
  }
  @PostMapping("/login")
  public Map<String,Object> login(@RequestBody LoginBody b, HttpServletRequest req, HttpServletResponse res){
    var result = auth.login(b.email(), b.password(), ipResolver.resolve(req));
    saveAuth(req, res, result.principal());
    Map<String,Object> m = new LinkedHashMap<>(principalDto(result.principal()));
    m.put("stage", result.stage());
    m.put("mustChangePassword", result.mustChangePassword());
    if (result.otpauthUrl()!=null) m.put("otpauthUrl", result.otpauthUrl());
    // D-094: restrict this session until the password is changed. The
    // TOTP/email-OTP steps stay reachable (MustChangePasswordFilter
    // allowlist); the admin change-password endpoint additionally requires
    // MFA, so a bare temporary password cannot change itself into a
    // persistent credential without the second factor.
    if (result.mustChangePassword()) req.getSession().setAttribute(MustChangePasswordFilter.SESSION_ATTRIBUTE, Boolean.TRUE);
    return m;
  }
  @PostMapping("/totp/enrol")
  public Map<String,Object> enrol(@RequestBody CodeBody b, Authentication a, HttpServletRequest req){
    var p = requireAdminPrincipal(a);
    List<String> codes = auth.enrolTotp(p.adminId(), b.code(), ipResolver.resolve(req));
    // Recovery codes are shown ONCE here; they are stored hashed.
    return Map.of("status","enrolled","recoveryCodes", codes);
  }
  @PostMapping("/totp/verify")
  public Map<String,Object> verifyTotp(@RequestBody CodeBody b, Authentication a, HttpServletRequest req, HttpServletResponse res){
    var p = requireAdminPrincipal(a);
    var verified = auth.verifyTotpOrRecovery(p.adminId(), b.code(), ipResolver.resolve(req));
    saveAuth(req, res, verified);
    if (auth.mustChangePassword(verified.adminId())) req.getSession().setAttribute(MustChangePasswordFilter.SESSION_ATTRIBUTE, Boolean.TRUE);
    Map<String,Object> m = new LinkedHashMap<>(principalDto(verified));
    m.put("stage","MFA_VERIFIED");
    return m;
  }
  @PostMapping("/email-otp/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String,String> requestEmailOtp(Authentication a){
    var p = requireAdminPrincipal(a);
    auth.requestEmailOtp(p.adminId());
    return Map.of("status","Email OTP sent");
  }
  @PostMapping("/email-otp/verify")
  public Map<String,Object> verifyEmailOtp(@RequestBody EmailOtpBody b, Authentication a, HttpServletRequest req, HttpServletResponse res){
    var p = requireAdminPrincipal(a);
    var verified = auth.verifyEmailOtp(p.adminId(), b.otp(), ipResolver.resolve(req));
    saveAuth(req, res, verified);
    if (auth.mustChangePassword(verified.adminId())) req.getSession().setAttribute(MustChangePasswordFilter.SESSION_ATTRIBUTE, Boolean.TRUE);
    Map<String,Object> m = new LinkedHashMap<>(principalDto(verified));
    m.put("stage","MFA_VERIFIED");
    return m;
  }
  /** D-093: POST /api/admin/auth/change-password — the endpoint Phase 2
   *  noted as missing. Authenticated admin only, and MFA-verified only:
   *  a holder of a temporary password must still pass TOTP/recovery
   *  before they can turn it into a persistent password. Current password
   *  + new password (≥12 chars, D-031); audited; all sessions invalidated
   *  (D-013), so the caller logs in again. The filter allowlists this path
   *  for must-change sessions; the MFA check here is what keeps that safe.
   *  CSRF: /api/admin/auth/** is exempt (pre-existing, as for login/TOTP). */
  @PostMapping("/change-password")
  public Map<String,String> changePassword(Authentication a, @jakarta.validation.Valid @RequestBody ChangePasswordRequest r, HttpServletRequest req){
    var p = requireAdminPrincipal(a);
    if (!p.mfaVerified()) throw new Exceptions.Forbidden("Two-factor authentication required");
    auth.changePassword(p.adminId(), r.oldPassword(), r.newPassword(), ipResolver.resolve(req));
    HttpSession s = req.getSession(false); if (s!=null) s.invalidate();
    SecurityContextHolder.clearContext();
    return Map.of("status","Password changed. All sessions were invalidated; please log in again.");
  }
  @PostMapping("/logout") public Map<String,String> logout(HttpServletRequest req){
    HttpSession s = req.getSession(false); if (s!=null) s.invalidate();
    SecurityContextHolder.clearContext();
    return Map.of("status","logged out");
  }
  @GetMapping("/me") public Map<String,Object> me(Authentication a){
    var p = requireAdminPrincipal(a);
    return principalDto(p);
  }
}
