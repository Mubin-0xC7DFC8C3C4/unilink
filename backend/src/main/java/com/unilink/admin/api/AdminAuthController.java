package com.unilink.admin.api;
import com.unilink.admin.AdminAuthService; import com.unilink.security.Principals;
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
    var p = (Principals.AdminPrincipal) a.getPrincipal();
    List<String> codes = auth.enrolTotp(p.adminId(), b.code(), req.getRemoteAddr());
    // Recovery codes are shown ONCE here; they are stored hashed.
    return Map.of("status","enrolled","recoveryCodes", codes);
  }
  @PostMapping("/totp/verify")
  public Map<String,Object> verifyTotp(@RequestBody CodeBody b, Authentication a, HttpServletRequest req, HttpServletResponse res){
    var p = (Principals.AdminPrincipal) a.getPrincipal();
    var verified = auth.verifyTotpOrRecovery(p.adminId(), b.code(), req.getRemoteAddr());
    saveAuth(req, res, verified);
    Map<String,Object> m = new LinkedHashMap<>(principalDto(verified));
    m.put("stage","MFA_VERIFIED");
    return m;
  }
  @PostMapping("/email-otp/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String,String> requestEmailOtp(Authentication a){
    var p = (Principals.AdminPrincipal) a.getPrincipal();
    auth.requestEmailOtp(p.adminId());
    return Map.of("status","Email OTP sent");
  }
  @PostMapping("/email-otp/verify")
  public Map<String,Object> verifyEmailOtp(@RequestBody EmailOtpBody b, Authentication a, HttpServletRequest req, HttpServletResponse res){
    var p = (Principals.AdminPrincipal) a.getPrincipal();
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
    var p = (Principals.AdminPrincipal) a.getPrincipal();
    return principalDto(p);
  }
}
