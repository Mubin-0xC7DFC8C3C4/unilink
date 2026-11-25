package com.unilink.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unilink.common.Exceptions;
import com.unilink.profile.api.ProfileController;
import com.unilink.security.AccessPolicy;
import com.unilink.security.IpResolver;
import com.unilink.security.Principals;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** F7: GET /api/profiles/{id} must answer 403 (not 500) for an admin principal.
 *  Plain unit test — no Spring context: the controller takes its collaborators
 *  by constructor, so a mocked Authentication is enough to prove the fix.
 *  D-199/D-202: the same shape proves the new /me endpoints always act on
 *  the principal's own id (there is no target id to spoof) and that PATCH
 *  presence detection maps absent vs explicit-null correctly. */
class ProfileControllerTest {
  private static Authentication authWith(Object principal){
    Authentication a = mock(Authentication.class);
    when(a.getPrincipal()).thenReturn(principal);
    return a;
  }
  private static ProfileController controller(ProfileService service){
    return new ProfileController(service, new AccessPolicy(), new IpResolver("127.0.0.1,::1"));
  }
  private static Principals.UnilinkUserPrincipal student(long id){
    return new Principals.UnilinkUserPrincipal(id, "s@example.com", "x", 1L, 2L, 3L, "VERIFIED", true);
  }

  @Test void adminPrincipalGets403OnGetProfile() {
    var service = mock(ProfileService.class);
    var controller = controller(service);
    var admin = new Principals.AdminPrincipal(1L, "admin@example.com", "x", "PLATFORM_ADMIN", null, true, true);
    assertThrows(Exceptions.Forbidden.class, () -> controller.get(authWith(admin), 42L));
    verifyNoInteractions(service);
  }

  @Test void studentPrincipalReachesService() {
    var service = mock(ProfileService.class);
    var controller = controller(service);
    var student = student(7L);
    assertNull(controller.get(authWith(student), 7L));
    verify(service).getProfile(7L, student);
  }

  @Test void patchAbsentIsUntouchedAndExplicitNullClears() throws Exception {
    var service = mock(ProfileService.class);
    var controller = controller(service);
    var who = student(7L);
    var node = new ObjectMapper().readTree("{\"bio\":null,\"phoneVisible\":true}");
    controller.patchMe(authWith(who), node);
    var captor = org.mockito.ArgumentCaptor.forClass(ProfileService.PatchUpdate.class);
    // UnilinkUserPrincipal is a class, not a record: match the same instance.
    verify(service).patchOwn(eq(7L), captor.capture(), same(who));
    var patch = captor.getValue();
    assertTrue(patch.bioPresent()); assertNull(patch.bio(), "explicit null bio must clear");
    assertFalse(patch.headlinePresent(), "absent headline must stay untouched");
    assertFalse(patch.linksPresent(), "absent links must stay untouched");
    assertTrue(patch.phoneVisiblePresent()); assertTrue(patch.phoneVisible());
  }

  @Test void patchNonObjectIs400() throws Exception {
    var service = mock(ProfileService.class);
    var controller = controller(service);
    var node = new ObjectMapper().readTree("[1,2]");
    assertThrows(Exceptions.BadRequest.class, () -> controller.patchMe(authWith(student(7L)), node));
    verifyNoInteractions(service);
  }

  @Test void adminPrincipalGets403OnPhoneChange() {
    var service = mock(ProfileService.class);
    var controller = controller(service);
    var admin = new Principals.AdminPrincipal(1L, "admin@example.com", "x", "PLATFORM_ADMIN", null, true, true);
    var req = mock(jakarta.servlet.http.HttpServletRequest.class);
    assertThrows(Exceptions.Forbidden.class,
      () -> controller.requestPhoneChange(authWith(admin), new ProfileController.PhoneChangeRequest("01712345678"), req));
    verifyNoInteractions(service);
  }

  @Test void phoneChangeEndpointsUsePrincipalId() {
    var service = mock(ProfileService.class);
    var controller = controller(service);
    var req = mock(jakarta.servlet.http.HttpServletRequest.class);
    when(req.getRemoteAddr()).thenReturn("203.0.113.7");
    when(service.verifyPhoneChange(7L, "123456")).thenReturn("01712345678");
    controller.requestPhoneChange(authWith(student(7L)), new ProfileController.PhoneChangeRequest("01712345678"), req);
    verify(service).requestPhoneChange(eq(7L), eq("01712345678"), eq("203.0.113.7"));
    controller.verifyPhoneChange(authWith(student(7L)), new ProfileController.PhoneChangeVerifyRequest("123456"));
    verify(service).verifyPhoneChange(7L, "123456");
    controller.cancelPhoneChange(authWith(student(7L)));
    verify(service).cancelPhoneChange(7L);
  }

  @Test void deletePhotoAndCoverUsePrincipalId() {
    var service = mock(ProfileService.class);
    var controller = controller(service);
    controller.deletePhoto(authWith(student(7L)));
    verify(service).deletePhoto(7L);
    controller.deleteCover(authWith(student(7L)));
    verify(service).deleteCover(7L);
  }
}
