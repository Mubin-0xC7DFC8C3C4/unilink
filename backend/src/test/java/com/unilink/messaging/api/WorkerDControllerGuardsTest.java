package com.unilink.messaging.api;

import com.unilink.common.Exceptions;
import com.unilink.messaging.MessagingService;
import com.unilink.notification.NotificationService;
import com.unilink.notification.api.NotificationController;
import com.unilink.profile.ProfileService;
import com.unilink.profile.api.ProfileController;
import com.unilink.search.SearchService;
import com.unilink.search.api.SearchController;
import com.unilink.security.AccessPolicy;
import com.unilink.security.IpResolver;
import com.unilink.security.Principals;
import com.unilink.storage.StorageGateway;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** I-15: the controller-level requireVerified guards (plain unit tests, no
 *  Spring — same pattern as ProfileControllerTest). An unverified principal
 *  (state REGISTERED) must get 403 and the service must never be called.
 *  This class lives in com.unilink.messaging.api because
 *  MessagingRestController's constructor is package-private. */
class WorkerDControllerGuardsTest {
  private static Authentication authWith(Object principal) {
    Authentication a = mock(Authentication.class);
    when(a.getPrincipal()).thenReturn(principal);
    return a;
  }
  private static Principals.UnilinkUserPrincipal unverified() {
    return new Principals.UnilinkUserPrincipal(7L, "s@example.com", "x", 1L, 2L, 3L, "REGISTERED", true);
  }
  private static Principals.UnilinkUserPrincipal verified() {
    return new Principals.UnilinkUserPrincipal(7L, "s@example.com", "x", 1L, 2L, 3L, "VERIFIED", true);
  }

  @Test void unverifiedCannotDeleteProfileSections() {
    var service = mock(ProfileService.class);
    var c = new ProfileController(service, new AccessPolicy(), new IpResolver("127.0.0.1,::1"));
    var a = authWith(unverified());
    assertThrows(Exceptions.Forbidden.class, () -> c.deleteProject(a, 1L));
    assertThrows(Exceptions.Forbidden.class, () -> c.deleteEducation(a, 1L));
    assertThrows(Exceptions.Forbidden.class, () -> c.deleteAchievement(a, 1L));
    assertThrows(Exceptions.Forbidden.class, () -> c.removeSkill(a, 1L));
    assertThrows(Exceptions.Forbidden.class, () -> c.followSector(a, 1L));
    assertThrows(Exceptions.Forbidden.class, () -> c.unfollowSector(a, 1L));
    assertThrows(Exceptions.Forbidden.class, () -> c.unfollow(a, 1L));
    verifyNoInteractions(service);
  }

  @Test void verifiedStudentReachesProfileService() {
    var service = mock(ProfileService.class);
    var c = new ProfileController(service, new AccessPolicy(), new IpResolver("127.0.0.1,::1"));
    assertEquals("deleted", c.deleteProject(authWith(verified()), 1L).get("status"));
    verify(service).deleteProject(7L, 1L);
  }

  @Test void unverifiedCannotSearch() {
    var service = mock(SearchService.class);
    var c = new SearchController(service, new AccessPolicy());
    assertThrows(Exceptions.Forbidden.class, () -> c.search(authWith(unverified()), "dhaka", "all", null));
    verifyNoInteractions(service);
  }

  @Test void unverifiedCannotChangeNotificationPreferences() {
    var service = mock(NotificationService.class);
    var c = new NotificationController(service, new AccessPolicy());
    assertThrows(Exceptions.Forbidden.class,
        () -> c.pref(authWith(unverified()), new NotificationController.PrefBody("TEAM_JOIN_ACCEPTED", false, true)));
    verifyNoInteractions(service);
  }

  @Test void unverifiedCannotBlockUnblockOrMute() {
    var service = mock(MessagingService.class);
    var c = new MessagingRestController(service, new AccessPolicy(), mock(StorageGateway.class));
    var a = authWith(unverified());
    assertThrows(Exceptions.Forbidden.class, () -> c.block(a, 9L));
    assertThrows(Exceptions.Forbidden.class, () -> c.unblock(a, 9L));
    assertThrows(Exceptions.Forbidden.class, () -> c.mute(a, "room-1", 60));
    verifyNoInteractions(service);
  }

  @Test void verifiedStudentCanBlock() {
    var service = mock(MessagingService.class);
    var c = new MessagingRestController(service, new AccessPolicy(), mock(StorageGateway.class));
    assertEquals("blocked", c.block(authWith(verified()), 9L).get("status"));
    verify(service).block(7L, 9L);
  }
}
