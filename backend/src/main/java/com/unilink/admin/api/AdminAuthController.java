package com.unilink.admin.api;
import com.unilink.admin.AdminAuthService; import com.unilink.common.Exceptions; import com.unilink.security.Principals;
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
  private final AdminAuthService auth;
  private final HttpSessionSecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();
  public AdminAuthController(AdminAuthService a){ auth=a; }
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
    return Map.of("adminId", p.adminId(), "email", p.getUsername(), "role", p.role(),
      "universityId", p.universityId()==null?"":p.universityId(), "mfaVerified", p.mfaVerified());
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
    var result = auth.login(b.email(), b.password(), req.getRemoteAddr());
    saveAuth(req, res, result.principal());
    Map<String,Object> m = new LinkedHashMap<>(principalDto(result.principal()));
    m.put("stage", result.stage());
    if (result.otpauthUrl()!=null) m.put("otpauthUrl", result.otpauthUrl());
    return m;
  }
  @PostMapping("/totp/enrol")
  public Map<String,Object> enrol(@RequestBody CodeBody b, Authentication a, HttpServletRequest req){
    var p = requireAdminPrincipal(a);
    List<String> codes = auth.enrolTotp(p.adminId(), b.code(), req.getRemoteAddr());
    // Recovery codes are shown ONCE here; they are stored hashed.
    return Map.of("status","enrolled","recoveryCodes", codes);
  }
  @PostMapping("/totp/verify")
  public Map<String,Object> verifyTotp(@RequestBody CodeBody b, Authentication a, HttpServletRequest req, HttpServletResponse res){
    var p = requireAdminPrincipal(a);
    var verified = auth.verifyTotpOrRecovery(p.adminId(), b.code(), req.getRemoteAddr());
    saveAuth(req, res, verified);
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
    var verified = auth.verifyEmailOtp(p.adminId(), b.otp(), req.getRemoteAddr());
    saveAuth(req, res, verified);
    Map<String,Object> m = new LinkedHashMap<>(principalDto(verified));
    m.put("stage","MFA_VERIFIED");
    return m;
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
