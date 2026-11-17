package com.unilink.security;
import com.unilink.common.Exceptions;
import com.unilink.identity.domain.User;
import org.springframework.stereotype.Component;
/**
 * Single authorisation decision point (ARCH §6). Out-of-scope resources return 404 so
 * existence is not leaked (D-032). Services call this; @PreAuthorize is only a coarse gate.
 */
@Component
public class AccessPolicy {
  public Principals.UnilinkUserPrincipal requireStudent(Object principal){
    if (!(principal instanceof Principals.UnilinkUserPrincipal p)) throw new Exceptions.Forbidden("Authentication required");
    return p;
  }
  public Principals.AdminPrincipal requireAdmin(Object principal){
    if (!(principal instanceof Principals.AdminPrincipal p)) throw new Exceptions.Forbidden("Admin authentication required");
    if (!p.mfaVerified()) throw new Exceptions.Forbidden("Two-factor authentication required");
    return p;
  }
  public void requireVerified(Principals.UnilinkUserPrincipal p){
    if (!"VERIFIED".equals(p.state())) throw new Exceptions.Forbidden("Account must be verified");
  }
  public void requireOwnerOr404(Principals.UnilinkUserPrincipal p, Long ownerId){
    if (!p.userId().equals(ownerId)) throw new Exceptions.NotFound("Resource");
  }
  /** Moderator may act only within their university; anything else is 404 (D-032). */
  public void requireModeratorScopeOr404(Principals.AdminPrincipal admin, Long resourceUniversityId){
    if ("PLATFORM_ADMIN".equals(admin.role())) return;
    if (resourceUniversityId==null || !resourceUniversityId.equals(admin.universityId()))
      throw new Exceptions.NotFound("Resource");
  }
  public void requirePlatformAdmin(Principals.AdminPrincipal admin){
    if (!"PLATFORM_ADMIN".equals(admin.role())) throw new Exceptions.Forbidden("Platform Admin only");
  }
  public void requireCanModerateUser(Principals.AdminPrincipal admin, User user){
    requireModeratorScopeOr404(admin, user.getUniversityId());
  }
}
