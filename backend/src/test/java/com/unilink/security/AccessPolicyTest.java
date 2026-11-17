package com.unilink.security;
import com.unilink.common.Exceptions; import com.unilink.identity.domain.User;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
/** AccessPolicy unit tests: verified gate, ownership 404, moderator scope 404. */
class AccessPolicyTest {
  private final AccessPolicy policy = new AccessPolicy();
  private Principals.UnilinkUserPrincipal student(Long id, String state){
    return new Principals.UnilinkUserPrincipal(id, "a@b.c", "hash", 1L, 2L, 3L, state, true);
  }
  private Principals.AdminPrincipal admin(String role, Long uni){
    return new Principals.AdminPrincipal(1L, "m@x.y", "hash", role, uni, true, true);
  }
  @Test void requireVerifiedBlocksUnverified(){
    assertThrows(Exceptions.Forbidden.class, () -> policy.requireVerified(student(1L,"REGISTERED")));
    assertDoesNotThrow(() -> policy.requireVerified(student(1L,"VERIFIED")));
  }
  @Test void ownershipIs404Not403(){
    assertThrows(Exceptions.NotFound.class, () -> policy.requireOwnerOr404(student(1L,"VERIFIED"), 2L));
    assertDoesNotThrow(() -> policy.requireOwnerOr404(student(1L,"VERIFIED"), 1L));
  }
  @Test void moderatorScope(){
    var mod = admin("UNIVERSITY_MODERATOR", 1L);
    assertDoesNotThrow(() -> policy.requireModeratorScopeOr404(mod, 1L));
    assertThrows(Exceptions.NotFound.class, () -> policy.requireModeratorScopeOr404(mod, 2L));
    assertDoesNotThrow(() -> policy.requireModeratorScopeOr404(admin("PLATFORM_ADMIN", null), 99L));
    assertThrows(Exceptions.Forbidden.class, () -> policy.requirePlatformAdmin(mod));
  }
  @Test void requireStudentRejectsAdminPrincipal(){
    assertThrows(Exceptions.Forbidden.class, () -> policy.requireStudent(admin("PLATFORM_ADMIN", null)));
  }
  @Test void canModerateUser(){
    User u = new User(); u.setUniversityId(2L);
    assertThrows(Exceptions.NotFound.class, () -> policy.requireCanModerateUser(admin("UNIVERSITY_MODERATOR", 1L), u));
  }
}
