package com.unilink.integration;

import com.unilink.admin.AdminService;
import com.unilink.admin.api.AdminController;
import com.unilink.audit.AuditService;
import com.unilink.common.Exceptions;
import com.unilink.common.Hashing;
import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*;
import com.unilink.identity.service.IdCardMatcher;
import com.unilink.profile.ProfileService;
import com.unilink.security.AccessPolicy;
import com.unilink.security.Principals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Slice E (D-204): admin monitoring — login activity, all-posts view,
 *  profile-change audit, verification-queue OCR filter, event edit, audit
 *  hardening (hash covers metadata+ip, verify endpoint, updateModerator
 *  audit). Service-level tests on H2, same setup as SliceDTest. */
@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-slicee-adm;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage"
})
@Transactional
class SliceEAdminTest {
  @Autowired TestDataFactory factory;
  @Autowired AdminService admin;
  @Autowired AuditService audit;
  @Autowired ProfileService profiles;
  @Autowired UserRepository users;
  @Autowired AdminAccountRepository admins;
  @Autowired PostRepository postRepo;
  @Autowired ReportRepository reportRepo;
  @Autowired VerificationRequestRepository requests;
  @Autowired LoginAttemptRepository loginAttempts;
  @Autowired AuditLogRepository auditLogs;
  @Autowired EventDetailRepository eventDetails;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager em;

  @BeforeEach void createH2OnlyTables(){
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION (PRIMARY_ID CHAR(36) PRIMARY KEY, SESSION_ID CHAR(36), CREATION_TIME BIGINT, LAST_ACCESS_TIME BIGINT, MAX_INACTIVE_INTERVAL INT, EXPIRY_TIME TIMESTAMP, PRINCIPAL_NAME VARCHAR(100))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID CHAR(36), ATTRIBUTE_NAME VARCHAR(200), ATTRIBUTE_BYTES BLOB, PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME))");
  }

  private Principals.AdminPrincipal platformAdmin(){
    AdminAccount a = factory.admin("slicee-platform-" + System.nanoTime() + "@example.com", "PLATFORM_ADMIN", null);
    return new Principals.AdminPrincipal(a.getId(), a.getEmail(), "x", a.getRole(), a.getUniversityId(), true, true);
  }
  private Principals.AdminPrincipal moderator(Long uni){
    AdminAccount a = factory.admin("slicee-mod-" + System.nanoTime() + "@example.com", "UNIVERSITY_MODERATOR", uni);
    return new Principals.AdminPrincipal(a.getId(), a.getEmail(), "x", a.getRole(), a.getUniversityId(), true, true);
  }
  private static Principals.UnilinkUserPrincipal studentPrincipal(User u){
    return new Principals.UnilinkUserPrincipal(u.getId(), u.getEmail(), "x", u.getUniversityId(),
      u.getDepartmentId(), null, "VERIFIED", true);
  }
  private static Authentication authWith(Object principal){
    return new UsernamePasswordAuthenticationToken(principal, null);
  }
  private User student(String tag, TestDataFactory.Fixture f){
    return factory.verifiedStudent("slicee-adm-" + tag + "-" + System.nanoTime() + "@example.com",
      "SLA-" + tag + "-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
  }
  private static byte[] tinyPng(){
    try {
      ByteArrayOutputStream b = new ByteArrayOutputStream();
      b.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
      byte[] ihdr = {0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0};
      b.write(new byte[]{0, 0, 0, 13}); b.write("IHDR".getBytes()); b.write(ihdr); b.write(new byte[]{0, 0, 0, 0});
      b.write(new byte[]{0, 0, 0, 0}); b.write("IEND".getBytes()); b.write(new byte[]{0, 0, 0, 0});
      return b.toByteArray();
    } catch (Exception e){ throw new IllegalStateException(e); }
  }
  private LoginAttempt attempt(String email, String ip, boolean success){
    LoginAttempt a = new LoginAttempt();
    a.setEmail(email); a.setIp(ip); a.setSuccess(success);
    return loginAttempts.save(a);
  }
  private VerificationRequest queueRequest(User u, String ocrStatus){
    VerificationRequest r = new VerificationRequest();
    r.setUserId(u.getId()); r.setStatus("PENDING_REVIEW"); r.setOcrStatus(ocrStatus);
    r.setOcrText("Metropolitan University student card"); r.setOcrMatchScore(0.8);
    return requests.save(r);
  }

  // ---- (B) login attempts ----

  @Test void loginAttemptsEndpointReturnsRowsAndModeratorSeesOwnUniversityOnly(){
    var f = factory.seedMaster();
    User mu = student("la-mu", f);
    User du = factory.verifiedStudent("slicee-adm-la-du-" + System.nanoTime() + "@example.com",
      "SLA-LADU-" + System.nanoTime(), f.du(), f.duCse(), f.duBatch());
    attempt(mu.getEmail(), "10.0.0.1", true);
    attempt(mu.getEmail(), "10.0.0.2", false);
    attempt(du.getEmail(), "10.0.0.3", false);
    attempt("stranger@example.com", "10.0.0.4", false);
    var page = admin.loginAttempts(platformAdmin(), 0, 50);
    assertEquals(4, page.totalElements());
    assertEquals(4, page.content().size());
    assertTrue(page.content().stream().anyMatch(a -> "10.0.0.1".equals(a.ip()) && Boolean.TRUE.equals(a.success())));
    var modPage = admin.loginAttempts(moderator(f.mu().getId()), 0, 50);
    assertEquals(2, modPage.totalElements(), "a moderator sees only their own university's accounts");
    assertTrue(modPage.content().stream().allMatch(a -> a.email().equals(mu.getEmail())));
  }

  @Test void studentPrincipalGets403OnLoginAttemptsEndpoint(){
    var controller = new AdminController(admin, mock(com.unilink.identity.service.VerificationFacade.class),
      new AccessPolicy(), mock(com.unilink.storage.StorageGateway.class),
      new com.unilink.security.IpResolver("127.0.0.1,::1"), mock(com.unilink.storage.UploadValidator.class));
    var f = factory.seedMaster();
    User u = student("la-403", f);
    assertThrows(Exceptions.Forbidden.class,
      () -> controller.loginAttempts(authWith(studentPrincipal(u)), 0, 50));
    assertThrows(Exceptions.Forbidden.class,
      () -> controller.allPosts(authWith(studentPrincipal(u)), 0, 50));
    assertThrows(Exceptions.Forbidden.class,
      () -> controller.userActivity(authWith(studentPrincipal(u)), u.getId()));
  }

  // ---- (C) all-posts view ----

  @Test void allPostsReturnsUnreportedPostsWithAuthorAndContent(){
    var f = factory.seedMaster();
    User a = student("ap-a", f);
    User b = student("ap-b", f);
    Post reported = factory.post(a, "NATIONAL", "Reported post");
    Post quiet = factory.post(b, "NATIONAL", "Quiet post nobody reported");
    Report r = new Report();
    r.setReporterId(b.getId()); r.setTargetType("POST"); r.setTargetId(reported.getId());
    r.setReason("spam"); r.setStatus("ACTIVE");
    reportRepo.save(r);
    var page = admin.allPosts(platformAdmin(), 0, 50);
    assertEquals(2, page.totalElements());
    var quietRow = page.content().stream().filter(p -> "Quiet post nobody reported".equals(p.title())).findFirst().orElseThrow();
    assertEquals(b.getId(), quietRow.authorId());
    assertEquals("Body of Quiet post nobody reported", quietRow.body());
    assertEquals(0, quietRow.reportCount(), "unreported posts must appear with report count 0");
    assertEquals("ACTIVE", quietRow.status());
    assertNotNull(quietRow.createdAt());
    var reportedRow = page.content().stream().filter(p -> "Reported post".equals(p.title())).findFirst().orElseThrow();
    assertEquals(1, reportedRow.reportCount());
    assertEquals(a.getFullName(), reportedRow.authorName());
  }

  @Test void allPostsModeratorScopeHidesOtherUniversity(){
    var f = factory.seedMaster();
    User mu = student("ap-mu", f);
    User du = factory.verifiedStudent("slicee-adm-ap-du-" + System.nanoTime() + "@example.com",
      "SLA-APDU-" + System.nanoTime(), f.du(), f.duCse(), f.duBatch());
    factory.post(mu, "UNIVERSITY", "MU post");
    factory.post(du, "UNIVERSITY", "DU post");
    var page = admin.allPosts(moderator(f.mu().getId()), 0, 50);
    assertEquals(1, page.totalElements());
    assertEquals("MU post", page.content().get(0).title());
  }

  // ---- (D) profile-change audit + per-user activity ----

  @Test void profilePhotoAndBioUpdatesWriteAuditRows(){
    var f = factory.seedMaster();
    User u = student("pa", f);
    profiles.updateOwn(u.getId(), "New bio", null, null, null, studentPrincipal(u));
    profiles.uploadPhoto(u.getId(), "photo.png", "image/png", tinyPng());
    profiles.uploadCover(u.getId(), "cover.png", "image/png", tinyPng());
    profiles.deletePhoto(u.getId());
    profiles.deleteCover(u.getId());
    List<AuditLog> rows = auditLogs.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("USER", u.getId(),
      org.springframework.data.domain.PageRequest.of(0, 50));
    List<String> actions = rows.stream().map(AuditLog::getAction).toList();
    assertTrue(actions.contains("PROFILE_UPDATED"), actions.toString());
    assertTrue(actions.contains("PROFILE_PHOTO_UPDATED"), actions.toString());
    assertTrue(actions.contains("PROFILE_COVER_UPDATED"), actions.toString());
    assertTrue(actions.contains("PROFILE_PHOTO_REMOVED"), actions.toString());
    assertTrue(actions.contains("PROFILE_COVER_REMOVED"), actions.toString());
    assertTrue(rows.stream().allMatch(r -> r.getActorAdminId()==null), "student actions have no admin actor");
    assertTrue(rows.stream().noneMatch(r -> r.getMetadata()!=null && r.getMetadata().contains("profile-photos")),
      "audit metadata must never carry image content or storage keys");
  }

  @Test void perUserActivityReturnsAuditRowsAndLoginAttempts(){
    var f = factory.seedMaster();
    User u = student("ua", f);
    profiles.updateOwn(u.getId(), "Bio for activity", null, null, null, studentPrincipal(u));
    attempt(u.getEmail(), "10.1.2.3", true);
    attempt(u.getEmail(), "10.1.2.4", false);
    var activity = admin.userActivity(platformAdmin(), u.getId());
    assertEquals(u.getId(), activity.user().id());
    assertTrue(activity.auditLogs().stream().anyMatch(a -> "PROFILE_UPDATED".equals(a.action())));
    assertEquals(2, activity.loginAttempts().size());
    assertTrue(activity.loginAttempts().stream().anyMatch(a -> "10.1.2.3".equals(a.ip())));
  }

  @Test void perUserActivityOutOfScopeIs404ForModerator(){
    var f = factory.seedMaster();
    User du = factory.verifiedStudent("slicee-adm-ua-du-" + System.nanoTime() + "@example.com",
      "SLA-UADU-" + System.nanoTime(), f.du(), f.duCse(), f.duBatch());
    assertThrows(Exceptions.NotFound.class,
      () -> admin.userActivity(moderator(f.mu().getId()), du.getId()));
  }

  // ---- (A) verification queue: ocrStatus filter + address evidence ----

  @Test void queueFiltersByOcrStatus(){
    var f = factory.seedMaster();
    User u = student("qf", f);
    queueRequest(u, "MANUAL_REVIEW");
    queueRequest(u, "AUTO_VERIFIED");
    queueRequest(u, "VERIFICATION_FAILED");
    assertEquals(1, admin.verificationQueue(platformAdmin(), null, "MANUAL_REVIEW").size());
    assertEquals(1, admin.verificationQueue(platformAdmin(), null, "AUTO_VERIFIED").size());
    // 3 created here + 1 seeded by verifiedStudent (ocrStatus "DONE").
    assertEquals(4, admin.verificationQueue(platformAdmin(), null, null).size());
    var ex = assertThrows(Exceptions.BadRequest.class,
      () -> admin.verificationQueue(platformAdmin(), null, "BOGUS"));
    assertEquals("INVALID_OCR_STATUS", ex.code());
  }

  @Test void queueSurfacesVerifiedTimestampsAndAddressConflict(){
    var f = factory.seedMaster();
    User u = student("qe", f);
    u.setAddress("House 12, Dhaka"); users.save(u);
    VerificationRequest r = queueRequest(u, "MANUAL_REVIEW");
    r.setOcrText("Chittagong University student card, Chittagong");
    requests.save(r);
    var row = admin.verificationQueue(platformAdmin(), null, "MANUAL_REVIEW").get(0);
    assertNotNull(row.phoneVerifiedAt(), "phone-verified timestamp must be surfaced");
    assertNotNull(row.emailVerifiedAt(), "email-verified timestamp must be surfaced");
    assertEquals(Boolean.TRUE, row.addressConflict(),
      "card names Chittagong while the entered address is Dhaka — the evidence flag must fire");
  }

  @Test void addressConflictHelperAgreesWithMatcher(){
    assertTrue(IdCardMatcher.addressConflictFor("Chittagong University card", null, "Dhaka"));
    assertFalse(IdCardMatcher.addressConflictFor("Dhaka University card", null, "Dhaka"));
    assertFalse(IdCardMatcher.addressConflictFor(null, null, "Dhaka"), "no card places means no conflict");
    assertFalse(IdCardMatcher.addressConflictFor("Chittagong card", null, null), "no entered address means no conflict");
  }

  // ---- (F) audit hash + verify + updateModerator audit ----

  @Test void auditHashCoversMetadataAndIpAndVerifies(){
    var v = audit.verifyDetailed();
    assertNull(v.brokenRowId());
    AuditLog row = audit.write(999L, "SLICEE_TEST_WRITE", "USER", 1L, "reason", Map.of("b", 1, "a", "x"), "10.9.9.9");
    var after = audit.verifyDetailed();
    assertNull(after.brokenRowId(), "a freshly written row must verify");
    assertTrue(after.verifiedRows() >= 1);
    assertEquals(row.getRowHash(), AuditService.recompute(row),
      "recomputing a stored row must reproduce its hash");
  }

  @Test void tamperingWithMetadataOrIpBreaksTheChain(){
    // The first row establishes the v2 cutover; the SECOND row is the
    // tampered one. (Known limit, see AuditService: tampering ONLY the
    // first post-change row evades the recompute because no intact v2
    // row remains to locate the cutover — linkage is still checked.)
    // Metadata is tampered through the JPA repository, not raw JDBC:
    // the column is @JdbcTypeCode(JSON) and a raw-JDBC string update
    // gets stored as a JSON *string* ("{\"k\":\"v\"}") instead of the
    // object — a driver artifact, not a service bug (the service only
    // ever writes via JPA). Ip is a plain VARCHAR, so JDBC is fine there.
    audit.write(999L, "SLICEE_TAMPER_A", "USER", 1L, "reason", Map.of("k", "v"), "10.8.8.8");
    AuditLog row = audit.write(999L, "SLICEE_TAMPER", "USER", 1L, "reason", Map.of("k", "v"), "10.8.8.8");
    AuditLog loaded = auditLogs.findById(row.getId()).orElseThrow();
    loaded.setMetadata("{\"k\":\"tampered\"}");
    auditLogs.save(loaded); em.flush(); em.clear();
    assertEquals(row.getId(), audit.verifyChain(), "tampered metadata must break verification");
    loaded = auditLogs.findById(row.getId()).orElseThrow();
    loaded.setMetadata("{\"k\":\"v\"}");
    auditLogs.save(loaded); em.flush(); em.clear();
    assertNull(audit.verifyChain(), "restoring the metadata must restore verification");
    jdbc.update("UPDATE audit_logs SET ip='1.2.3.4' WHERE id=?", row.getId());
    em.clear();
    assertEquals(row.getId(), audit.verifyChain(), "tampered ip must break verification");
    jdbc.update("UPDATE audit_logs SET ip='10.8.8.8' WHERE id=?", row.getId());
    em.clear();
    assertNull(audit.verifyChain());
  }

  @Test void legacyRowVerifiesByLinkageOnly(){
    // A pre-change row: its hash covers a never-persisted nanosecond
    // timestamp, so no recompute can match it — linkage is its check.
    // In production every legacy row predates every v2 row, so the legacy
    // row is inserted FIRST here (prev GENESIS); a v2 row written after
    // it chains from it and both verify.
    String fakeAt = "2026-01-01T00:00:00.123456789";
    String legacyHash = Hashing.sha256("GENESIS|LEGACY_ACTION|USER|1|999|legacy-reason|" + fakeAt);
    jdbc.update("INSERT INTO audit_logs (actor_admin_id, action, target_type, target_id, reason, metadata, ip, prev_hash, row_hash, created_at) VALUES (999, 'LEGACY_ACTION', 'USER', 1, 'legacy-reason', NULL, NULL, 'GENESIS', ?, ?)",
      legacyHash, LocalDateTime.now());
    AuditLog tip = audit.write(999L, "SLICEE_TIP", "USER", 1L, null, null, null);
    var v = audit.verifyDetailed();
    assertNull(v.brokenRowId(), "a legacy row with intact linkage must not report broken");
    assertTrue(v.legacyRows() >= 1, "the pre-change row must be counted as legacy, not verified");
    assertTrue(v.verifiedRows() >= 1, "the row written after the change must verify under the new formula");
    jdbc.update("DELETE FROM audit_logs WHERE id=?", tip.getId());
    jdbc.update("DELETE FROM audit_logs WHERE row_hash=?", legacyHash);
    assertNull(audit.verifyChain(), "removing the test rows must restore a clean chain");
  }

  @Test void updateModeratorWritesAuditRow(){
    var platform = platformAdmin();
    AdminAccount mod = factory.admin("slicee-mod-audit-" + System.nanoTime() + "@example.com",
      "UNIVERSITY_MODERATOR", factory.seedMaster().mu().getId());
    admin.updateModerator(platform, mod.getId(), "slicee-mod-renamed-" + System.nanoTime() + "@example.com", null, null, "10.5.5.5");
    List<AuditLog> rows = auditLogs.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("ADMIN", mod.getId(),
      org.springframework.data.domain.PageRequest.of(0, 10));
    assertTrue(rows.stream().anyMatch(r -> "MODERATOR_UPDATE".equals(r.getAction())
      && platform.adminId().equals(r.getActorAdminId()) && "10.5.5.5".equals(r.getIp())),
      "updateModerator must write its audit row with the request IP");
  }

  @Test void auditVerifyEndpointShape(){
    var v = admin.auditVerify(platformAdmin());
    assertTrue(v.ok());
    assertNull(v.brokenRowId());
    assertTrue(v.totalRows() >= 0);
  }

  // ---- (E) admin event edit: full PUT ----

  @Test void adminEventFullPutUpdatesEveryField(){
    var platform = platformAdmin();
    var f = factory.seedMaster();
    var in = new AdminService.AdminEventInput("Original title", "Original body", "Short", "UNIVERSITY",
      LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(5).plusHours(2), "Asia/Dhaka",
      "Hall A", "Hall A", "1 Main Rd", "https://meet.example.com", "Physical", "CSE Club", "CSE",
      "club@example.com", true, LocalDateTime.now().plusDays(3), "https://register.example.com", 50,
      List.of(), f.mu().getId(), f.muCse().getId(), "PUBLISHED");
    var created = admin.createAdminEvent(platform, in, "127.0.0.1");
    var upd = new AdminService.AdminEventInput("Renamed title", "Rewritten body", "New short", "NATIONAL",
      LocalDateTime.now().plusDays(9), LocalDateTime.now().plusDays(9).plusHours(3), "Asia/Dhaka",
      "Hall B", "Hall B", "2 Other Rd", null, "Online", "New Organizer", "New Unit",
      "new@example.com", false, null, null, 25,
      List.of(), null, null, null);
    var out = admin.updateAdminEvent(platform, created.id(), upd, "127.0.0.1");
    assertEquals("Renamed title", out.title());
    assertEquals("Rewritten body", out.body());
    assertEquals("NATIONAL", out.visibility());
    assertEquals("Hall B", out.location());
    assertEquals("New Organizer", out.organizerName());
    var detail = eventDetails.findById(created.id()).orElseThrow().getMaxParticipants();
    assertEquals(25, detail);
  }
}
