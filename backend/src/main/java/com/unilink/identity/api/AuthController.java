package com.unilink.identity.api;
import com.unilink.identity.dto.AuthDtos.*;
import com.unilink.identity.service.AuthService;
import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import jakarta.servlet.http.*; import jakarta.validation.Valid;
import org.springframework.http.*; import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
/** Student auth + verification (FR-AUTH-01..13, D-010..D-017). Sessions are server-side (UL_SESSION). */
@RestController
public class AuthController {
  private final AuthService auth; private final AccessPolicy policy;
  private final HttpSessionSecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();
  public AuthController(AuthService auth, AccessPolicy policy){ this.auth=auth; this.policy=policy; }
  private void saveAuth(HttpServletRequest req, HttpServletResponse res, Authentication a){
    SecurityContextHolder.getContext().setAuthentication(a);
    req.getSession(true); // session fixation: migrate on login via SecurityConfig
    contextRepo.saveContext(SecurityContextHolder.getContext(), req, res);
  }
  @GetMapping("/api/csrf") public Map<String,String> csrf(jakarta.servlet.http.HttpServletRequest req){
    var token = (org.springframework.security.web.csrf.CsrfToken) req.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
    return Map.of("token", token==null?"":token.getToken(), "headerName", token==null?"X-XSRF-TOKEN":token.getHeaderName());
  }
  @GetMapping("/api/health") public Map<String,String> health(){ return Map.of("status","UP","service","unilink-backend"); }
  @PostMapping(value="/api/auth/register", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public UserDto register(@Valid @ModelAttribute RegisterForm form){ return auth.register(form); }
  @PostMapping("/api/auth/verify-phone") public UserDto verifyPhone(@Valid @RequestBody OtpVerifyRequest r){ return auth.verifyPhone(r); }
  @GetMapping("/api/auth/verify-email") public UserDto verifyEmail(@RequestParam String token){ return auth.verifyEmailByToken(token); }
  @PostMapping("/api/auth/login") public UserDto login(@Valid @RequestBody LoginRequest r, HttpServletRequest req, HttpServletResponse res){
    Authentication a = auth.login(r.email(), r.password(), req.getRemoteAddr());
    saveAuth(req, res, a);
    var p = (Principals.UnilinkUserPrincipal) a.getPrincipal();
    return AuthService.toDto(auth.getUserOr404(p.userId()));
  }
  @PostMapping("/api/auth/logout") public Map<String,String> logout(HttpServletRequest req){
    HttpSession s = req.getSession(false); if (s!=null) s.invalidate();
    SecurityContextHolder.clearContext();
    return Map.of("status","logged out");
  }
  @PostMapping("/api/auth/password-reset/request") public Map<String,String> resetRequest(@Valid @RequestBody PasswordResetRequest r){
    auth.requestPasswordReset(r.email()); return Map.of("status","If the email exists, a reset code was sent");
  }
  @PostMapping("/api/auth/password-reset/confirm") public Map<String,String> resetConfirm(@Valid @RequestBody PasswordResetConfirm r){
    auth.confirmPasswordReset(r); return Map.of("status","Password changed. Please log in again.");
  }
  @PostMapping("/api/auth/change-password") public Map<String,String> changePassword(Authentication authn, @Valid @RequestBody ChangePasswordRequest r, HttpServletRequest req){
    var p = policy.requireStudent(authn.getPrincipal());
    auth.changePassword(p.userId(), r);
    HttpSession s = req.getSession(false); if (s!=null) s.invalidate();
    return Map.of("status","Password changed. All sessions were invalidated; please log in again.");
  }
  @GetMapping("/api/auth/me") public UserDto me(Authentication authn){
    var p = policy.requireStudent(authn.getPrincipal());
    return AuthService.toDto(auth.getUserOr404(p.userId()));
  }
  @GetMapping("/api/auth/verification/status") public VerificationStatusDto status(Authentication authn){
    var p = policy.requireStudent(authn.getPrincipal());
    return auth.verificationStatus(p.userId());
  }
  @PostMapping(value="/api/auth/verification/resubmit", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public UserDto resubmit(Authentication authn, @RequestPart(value="idCard", required=false) MultipartFile idCard){
    var p = policy.requireStudent(authn.getPrincipal());
    return auth.resubmit(p.userId(), idCard);
  }
}
