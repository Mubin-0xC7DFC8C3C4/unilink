package com.unilink.identity.api;
import com.unilink.common.Exceptions;
import com.unilink.identity.dto.AuthDtos.*;
import com.unilink.identity.service.AccountDeletionService;
import com.unilink.identity.service.AuthService;
import com.unilink.security.AccessPolicy; import com.unilink.security.IpResolver; import com.unilink.security.Principals;
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
  private final AuthService auth; private final AccessPolicy policy; private final AccountDeletionService deletion;
  private final IpResolver ipResolver;
  private final org.springframework.security.core.userdetails.UserDetailsService studentUds;
  private final HttpSessionSecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();
  public AuthController(AuthService auth, AccessPolicy policy, AccountDeletionService deletion, IpResolver ipResolver,
                        @org.springframework.beans.factory.annotation.Qualifier("studentUserDetailsService") org.springframework.security.core.userdetails.UserDetailsService studentUds){ this.auth=auth; this.policy=policy; this.deletion=deletion; this.ipResolver=ipResolver; this.studentUds=studentUds; }
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
  @PostMapping("/api/auth/verify-phone") public UserDto verifyPhone(@Valid @RequestBody OtpVerifyRequest r, HttpServletRequest req, HttpServletResponse res){
    UserDto dto = auth.verifyPhone(r);
    // The OTP just proved control of this account's phone, and the calls
    // the flow makes next (GET /auth/verification/status, resubmit) all
    // require a session — but registration deliberately creates none, so
    // every genuine user reached /pending with a 401 on the status query
    // (production report 2026-11-23). Establish the same session a
    // password login would give: no extra privilege, since write access
    // still requires admin approval, and the user could obtain this exact
    // session by logging in. Suspended/banned accounts get no session —
    // the login path refuses them too.
    if (!"SUSPENDED".equals(dto.state()) && !"BANNED".equals(dto.state())){
      var ud = studentUds.loadUserByUsername(dto.email());
      saveAuth(req, res, new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
    }
    return dto;
  }
  @PostMapping("/api/auth/otp/resend") public Map<String,String> resendOtp(@Valid @RequestBody OtpResendRequest r, HttpServletRequest req){
    return auth.resendPhoneOtp(r.userId(), ipResolver.resolve(req));
  }
  @GetMapping("/api/auth/verify-email") public UserDto verifyEmail(@RequestParam String token){ return auth.verifyEmailByToken(token); }
  @PostMapping("/api/auth/login") public UserDto login(@Valid @RequestBody LoginRequest r, HttpServletRequest req, HttpServletResponse res){
    Authentication a = auth.login(r.email(), r.password(), ipResolver.resolve(req));
    saveAuth(req, res, a);
    var p = (Principals.UnilinkUserPrincipal) a.getPrincipal();
    // D-094: a must-change credential restricts this session (enforced by
    // MustChangePasswordFilter) and the flag is returned so the frontend
    // can redirect to the change-password screen.
    boolean mustChange = auth.mustChangePassword(p.userId());
    if (mustChange) req.getSession().setAttribute(com.unilink.security.MustChangePasswordFilter.SESSION_ATTRIBUTE, Boolean.TRUE);
    return AuthService.toDto(auth.getUserOr404(p.userId()), mustChange);
  }
  @PostMapping("/api/auth/logout") public Map<String,String> logout(HttpServletRequest req){
    HttpSession s = req.getSession(false); if (s!=null) s.invalidate();
    SecurityContextHolder.clearContext();
    return Map.of("status","logged out");
  }
  @PostMapping("/api/auth/password-reset/request") public Map<String,String> resetRequest(@Valid @RequestBody PasswordResetRequest r, HttpServletRequest req){
    auth.requestPasswordReset(r.email(), ipResolver.resolve(req)); return Map.of("status","If the email exists, a reset code was sent");
  }
  @PostMapping("/api/auth/password-reset/confirm") public Map<String,String> resetConfirm(@Valid @RequestBody PasswordResetConfirm r){
    auth.confirmPasswordReset(r); return Map.of("status","Password changed. Please log in again.");
  }
  // /api/auth/** is permitAll, so unauthenticated requests reach these methods
  // with a null Authentication; dereferencing it NPE'd (500) instead of 401.
  // Mirrors AdminAuthController.requireAdminPrincipal.
  private Principals.UnilinkUserPrincipal requireStudentPrincipal(Authentication authn){
    if (authn==null) throw new Exceptions.Unauthorized("Authentication required");
    return policy.requireStudent(authn.getPrincipal());
  }
  @PostMapping("/api/auth/change-password") public Map<String,String> changePassword(Authentication authn, @Valid @RequestBody ChangePasswordRequest r, HttpServletRequest req){
    var p = requireStudentPrincipal(authn);
    auth.changePassword(p.userId(), r);
    HttpSession s = req.getSession(false); if (s!=null) s.invalidate();
    return Map.of("status","Password changed. All sessions were invalidated; please log in again.");
  }
  @GetMapping("/api/auth/me") public UserDto me(Authentication authn){
    var p = requireStudentPrincipal(authn);
    return AuthService.toDto(auth.getUserOr404(p.userId()), auth.mustChangePassword(p.userId()));
  }
  @PostMapping("/api/me/delete") public Map<String,Object> deleteMe(Authentication authn, @Valid @RequestBody DeleteAccountRequest r, HttpServletRequest req){
    var p = requireStudentPrincipal(authn);
    deletion.deleteOwnAccount(p.userId(), r.password(), ipResolver.resolve(req));
    HttpSession s = req.getSession(false); if (s!=null) s.invalidate();
    SecurityContextHolder.clearContext();
    return Map.of("deleted", true);
  }
  @GetMapping("/api/auth/verification/status") public VerificationStatusDto status(Authentication authn){
    var p = requireStudentPrincipal(authn);
    return auth.verificationStatus(p.userId());
  }
  @PostMapping(value="/api/auth/verification/resubmit", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public UserDto resubmit(Authentication authn, @RequestPart(value="idCard", required=false) MultipartFile idCard){
    var p = requireStudentPrincipal(authn);
    return auth.resubmit(p.userId(), idCard);
  }
}
