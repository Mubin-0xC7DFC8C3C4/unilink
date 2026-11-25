package com.unilink.profile;

import com.unilink.common.Exceptions;
import com.unilink.identity.domain.User;
import com.unilink.identity.repo.PhoneOtpRepository;
import com.unilink.identity.repo.UserRepository;
import com.unilink.identity.service.OtpService;
import com.unilink.integration.TestDataFactory;
import com.unilink.integration.TestProviders;
import com.unilink.security.LoginThrottle;
import com.unilink.security.Principals;
import com.unilink.storage.StorageGateway;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** D-202 PATCH semantics + photo/cover delete, and the D-199 SMS-gateway
 *  unavailable state (default test profile has app.sms.provider=log, so a
 *  phone change must 503 here — the gateway happy path lives in
 *  PhoneChangeTest). Service-level, @Transactional on H2. */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-patch;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage"
})
@Transactional
class ProfilePatchPhotoTest {
  @Autowired TestDataFactory factory;
  @Autowired ProfileService profiles;
  @Autowired OtpService otp;
  @Autowired UserRepository users;
  @Autowired PhoneOtpRepository phoneOtps;
  @Autowired StorageGateway storage;
  @Autowired LoginThrottle throttle;
  @Autowired EntityManager em;

  @BeforeEach void reset(){ TestProviders.reset(); throttle.resetForTests(); }

  private User student(String tag){
    var f = factory.seedMaster();
    return factory.verifiedStudent("pp-" + tag + "-" + System.nanoTime() + "@example.com",
      "PP-" + tag + "-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
  }
  private static Principals.UnilinkUserPrincipal principal(User u){
    return new Principals.UnilinkUserPrincipal(u.getId(), u.getEmail(), "x", 1L, 2L, 3L, "VERIFIED", true);
  }
  private static ProfileService.PatchUpdate onlyBio(String bio){
    return new ProfileService.PatchUpdate(true, bio, false, null, false, null, false, null);
  }
  private static ProfileService.PatchUpdate onlyPhoneVisible(Boolean v){
    return new ProfileService.PatchUpdate(false, null, false, null, false, null, true, v);
  }
  /** Minimal structurally-valid PNG (signature + IHDR + IEND). */
  static byte[] tinyPng(){
    try {
      ByteArrayOutputStream b = new ByteArrayOutputStream();
      b.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
      byte[] ihdr = {0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0}; // 1x1, 8-bit, RGB
      b.write(new byte[]{0, 0, 0, 13}); b.write("IHDR".getBytes()); b.write(ihdr); b.write(new byte[]{0, 0, 0, 0});
      b.write(new byte[]{0, 0, 0, 0}); b.write("IEND".getBytes()); b.write(new byte[]{0, 0, 0, 0});
      return b.toByteArray();
    } catch (Exception e){ throw new IllegalStateException(e); }
  }

  @Test void patchAbsentFieldsUntouched(){
    User u = student("absent");
    profiles.updateOwn(u.getId(), "old bio", "old headline", "GitHub: https://github.com/old", true, principal(u));
    var out = profiles.patchOwn(u.getId(), onlyBio("new bio"), principal(u));
    assertEquals("new bio", out.bio());
    assertEquals("old headline", out.headline(), "absent headline must stay untouched");
    assertEquals("GitHub: https://github.com/old", out.links(), "absent links must stay untouched");
    assertTrue(out.phoneVisible(), "absent phoneVisible must stay untouched");
  }

  @Test void patchExplicitNullClears(){
    User u = student("null");
    profiles.updateOwn(u.getId(), "old bio", "old headline", "GitHub: https://github.com/old", true, principal(u));
    var out = profiles.patchOwn(u.getId(),
      new ProfileService.PatchUpdate(true, null, true, null, true, null, true, null), principal(u));
    assertNull(out.bio(), "explicit null bio must clear");
    assertNull(out.headline(), "explicit null headline must clear");
    assertNull(out.links(), "explicit null links must clear");
    assertFalse(out.phoneVisible(), "explicit null phoneVisible clears to false (NOT NULL default)");
  }

  @Test void patchEmptyStringClears(){
    User u = student("empty");
    profiles.updateOwn(u.getId(), "old bio", null, null, null, principal(u));
    var out = profiles.patchOwn(u.getId(), onlyBio(""), principal(u));
    assertEquals("", out.bio(), "explicit empty string overwrites (it is a value, not an absence)");
  }

  @Test void patchJavascriptLinkRejected(){
    User u = student("js");
    var ex = assertThrows(Exceptions.BadRequest.class, () -> profiles.patchOwn(u.getId(),
      new ProfileService.PatchUpdate(false, null, false, null, true, "javascript:alert(1)", false, null), principal(u)));
    assertEquals("BAD_LINK", ex.code());
  }

  @Test void savingPhoneVisibleAloneLeavesBioHeadlineLinksIntact(){
    // Regression for the D-202 seeding bug: a privacy-only save used to
    // send {phoneVisible:false} and silently turn visibility off; the
    // PATCH equivalent must leave every other field untouched.
    User u = student("priv");
    profiles.updateOwn(u.getId(), "keep me", "keep me too", "GitHub: https://github.com/keep", true, principal(u));
    var out = profiles.patchOwn(u.getId(), onlyPhoneVisible(false), principal(u));
    assertFalse(out.phoneVisible());
    assertEquals("keep me", out.bio());
    assertEquals("keep me too", out.headline());
    assertEquals("GitHub: https://github.com/keep", out.links());
  }

  @Test void savingBioAloneLeavesPhoneVisibleIntact(){
    User u = student("bio");
    profiles.updateOwn(u.getId(), "old", null, null, true, principal(u));
    var out = profiles.patchOwn(u.getId(), onlyBio("new"), principal(u));
    assertEquals("new", out.bio());
    assertTrue(out.phoneVisible(), "a bio-only save must not reset phone visibility");
  }

  @Test void updateOwnReturnsRealViewer(){
    User u = student("viewer");
    var out = profiles.updateOwn(u.getId(), "bio", null, null, null, principal(u));
    assertTrue(out.isOwner(), "updateOwn must report the real viewer (was getProfile(userId, null) -> isOwner=false)");
    assertEquals(u.getPhone(), out.phone(), "the owner must see their own phone in the response");
    assertNotNull(out.phoneVisible(), "the owner must see their own phoneVisible setting");
  }

  @Test void getProfileReportsHasPhotoHasCoverAndOwnerPhoneVisible(){
    User u = student("flags");
    var before = profiles.getProfile(u.getId(), principal(u));
    assertFalse(before.hasPhoto()); assertFalse(before.hasCover()); assertFalse(before.phoneVisible());
    profiles.uploadPhoto(u.getId(), "photo.png", "image/png", tinyPng());
    profiles.uploadCover(u.getId(), "cover.png", "image/png", tinyPng());
    var after = profiles.getProfile(u.getId(), principal(u));
    assertTrue(after.hasPhoto()); assertTrue(after.hasCover());
    var stranger = profiles.getProfile(u.getId(), principal(student("stranger")));
    assertTrue(stranger.hasPhoto(), "hasPhoto is public (the image endpoint is authenticated-student only)");
    assertNull(stranger.phoneVisible(), "a non-owner must not learn the privacy setting");
  }

  @Test void photoUploadReplacesKeyAndDeletesOldObject(){
    User u = student("replace");
    profiles.uploadPhoto(u.getId(), "a.png", "image/png", tinyPng());
    String oldKey = users.findById(u.getId()).orElseThrow().getProfilePhotoKey();
    assertNotNull(oldKey); assertTrue(storage.exists(oldKey));
    profiles.uploadPhoto(u.getId(), "b.png", "image/png", tinyPng());
    String newKey = users.findById(u.getId()).orElseThrow().getProfilePhotoKey();
    assertNotEquals(oldKey, newKey);
    assertTrue(storage.exists(newKey));
    assertFalse(storage.exists(oldKey), "the replaced photo object must be deleted");
  }

  @Test void coverUploadReplacesKeyAndDeletesOldObject(){
    User u = student("cover-replace");
    profiles.uploadCover(u.getId(), "a.png", "image/png", tinyPng());
    String oldKey = currentCoverKey(u);
    assertNotNull(oldKey); assertTrue(storage.exists(oldKey));
    profiles.uploadCover(u.getId(), "b.png", "image/png", tinyPng());
    String newKey = currentCoverKey(u);
    assertNotEquals(oldKey, newKey);
    assertTrue(storage.exists(newKey));
    assertFalse(storage.exists(oldKey), "the replaced cover object must be deleted");
  }
  /** Cover key is not exposed on the DTO — read it from the profiles row
   *  directly. Flush first: the upload's change lives in this test's
   *  transaction until flushed, and clear() would discard it. The factory
   *  always creates the row, so the query has a row. */
  private String currentCoverKey(User u){
    em.flush(); em.clear();
    return (String) em.createNativeQuery("SELECT cover_key FROM profiles WHERE user_id = :id")
      .setParameter("id", u.getId()).getSingleResult();
  }

  @Test void uploadFailureKeepsOldPhoto(){
    User u = student("fail");
    profiles.uploadPhoto(u.getId(), "a.png", "image/png", tinyPng());
    String oldKey = users.findById(u.getId()).orElseThrow().getProfilePhotoKey();
    byte[] oldBytes = profiles.openPhoto(u.getId()).bytes();
    assertThrows(Exceptions.BadRequest.class,
      () -> profiles.uploadPhoto(u.getId(), "bad.txt", "text/plain", "not an image".getBytes()));
    em.flush(); em.clear();
    assertEquals(oldKey, users.findById(u.getId()).orElseThrow().getProfilePhotoKey(),
      "a failed upload must keep the old photo key");
    assertTrue(storage.exists(oldKey));
    assertArrayEquals(oldBytes, profiles.openPhoto(u.getId()).bytes());
  }

  @Test void openPhoto404WhenNoKey(){
    User u = student("nokey");
    var ex = assertThrows(Exceptions.NotFound.class, () -> profiles.openPhoto(u.getId()));
    assertEquals("NOT_FOUND", ex.code());
    assertThrows(Exceptions.NotFound.class, () -> profiles.openCover(u.getId()));
  }

  @Test void deletePhotoClearsKeyAndDeletesObject(){
    User u = student("delphoto");
    profiles.uploadPhoto(u.getId(), "a.png", "image/png", tinyPng());
    String key = users.findById(u.getId()).orElseThrow().getProfilePhotoKey();
    profiles.deletePhoto(u.getId());
    em.flush(); em.clear();
    assertNull(users.findById(u.getId()).orElseThrow().getProfilePhotoKey());
    assertFalse(storage.exists(key));
    assertThrows(Exceptions.NotFound.class, () -> profiles.openPhoto(u.getId()));
    profiles.deletePhoto(u.getId()); // idempotent no-op
  }

  @Test void deleteCoverClearsKeyAndDeletesObject(){
    User u = student("delcover");
    profiles.uploadCover(u.getId(), "a.png", "image/png", tinyPng());
    String key = currentCoverKey(u);
    assertNotNull(key);
    profiles.deleteCover(u.getId());
    assertNull(currentCoverKey(u));
    assertFalse(storage.exists(key));
    assertThrows(Exceptions.NotFound.class, () -> profiles.openCover(u.getId()));
    profiles.deleteCover(u.getId()); // idempotent no-op
  }

  @Test void nonOwnerUploadDoesNotTouchOtherUser(){
    User a = student("owner-a");
    User b = student("owner-b");
    profiles.uploadPhoto(b.getId(), "b.png", "image/png", tinyPng());
    String bKey = users.findById(b.getId()).orElseThrow().getProfilePhotoKey();
    profiles.uploadPhoto(a.getId(), "a.png", "image/png", tinyPng());
    em.flush(); em.clear();
    assertEquals(bKey, users.findById(b.getId()).orElseThrow().getProfilePhotoKey(),
      "uploading as A must not change B's photo");
    assertArrayEquals(tinyPng(), profiles.openPhoto(b.getId()).bytes());
  }

  @Test void phoneChangeUnavailableWhenNoSmsGateway(){
    // Default test profile: app.sms.provider=log — the log provider is
    // not delivery, so D-199 requires a clear 503, never an email fallback.
    User u = student("nogw");
    assertFalse(otp.smsGatewayConfigured());
    var ex = assertThrows(Exceptions.AppException.class,
      () -> profiles.requestPhoneChange(u.getId(), "01712345678", "127.0.0.1"));
    assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, ex.status());
    assertEquals("SMS_NOT_CONFIGURED", ex.code());
    assertTrue(phoneOtps.findByUserId(u.getId()).isEmpty(), "no OTP row may be left behind by a 503");
    // Clear WITHOUT flushing: the failed request's pending-phone change
    // must never reach the DB (the service transaction rolled back), so
    // the re-read below must come from the database, not the dirty entity.
    em.clear();
    assertNull(users.findById(u.getId()).orElseThrow().getPendingPhone(),
      "a failed request must leave no pending change behind");
  }

  @Test void suspendedNumberRejectedEvenWithoutGateway(){
    User suspended = student("susp");
    // The factory phone (+880...) is not BD-regex valid; give the
    // suspended account a valid BD number to reach the D-077 check.
    suspended.setPhone("01799999999"); suspended.setState("SUSPENDED"); users.save(suspended);
    User u = student("victim");
    var ex = assertThrows(Exceptions.Forbidden.class,
      () -> profiles.requestPhoneChange(u.getId(), "01799999999", "127.0.0.1"));
    assertEquals("FORBIDDEN", ex.code());
  }
}
