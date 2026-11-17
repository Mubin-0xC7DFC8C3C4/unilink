package com.unilink.profile;

import com.unilink.common.Exceptions;
import com.unilink.profile.api.ProfileController;
import com.unilink.security.AccessPolicy;
import com.unilink.security.Principals;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** F7: GET /api/profiles/{id} must answer 403 (not 500) for an admin principal.
 *  Plain unit test — no Spring context: the controller takes its collaborators
 *  by constructor, so a mocked Authentication is enough to prove the fix. */
class ProfileControllerTest {
  private static Authentication authWith(Object principal){
    Authentication a = mock(Authentication.class);
    when(a.getPrincipal()).thenReturn(principal);
    return a;
  }

  @Test void adminPrincipalGets403OnGetProfile() {
    var service = mock(ProfileService.class);
    var controller = new ProfileController(service, new AccessPolicy());
    var admin = new Principals.AdminPrincipal(1L, "admin@example.com", "x", "PLATFORM_ADMIN", null, true, true);
    assertThrows(Exceptions.Forbidden.class, () -> controller.get(authWith(admin), 42L));
    verifyNoInteractions(service);
  }

  @Test void studentPrincipalReachesService() {
    var service = mock(ProfileService.class);
    var controller = new ProfileController(service, new AccessPolicy());
    var student = new Principals.UnilinkUserPrincipal(7L, "s@example.com", "x", 1L, 2L, 3L, "VERIFIED", true);
    assertNull(controller.get(authWith(student), 7L));
    verify(service).getProfile(7L, student);
  }
}
