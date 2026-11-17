package com.unilink.integration;

import com.unilink.admin.AdminAuthService;
import com.unilink.admin.AdminService;
import com.unilink.common.Exceptions;
import com.unilink.content.PostService;
import com.unilink.identity.domain.*;
import com.unilink.identity.dto.AuthDtos.RegisterForm;
import com.unilink.identity.event.DomainEvents;
import com.unilink.identity.repo.*;
import com.unilink.identity.service.AccountDeletionService;
import com.unilink.identity.service.AuthService;
import com.unilink.identity.service.VerificationFacade;
import com.unilink.jobs.ScheduledJobs;
import com.unilink.messaging.MessagingService;
import com.unilink.moderation.ModerationService;
import com.unilink.notification.NotificationEventListener;
import com.unilink.notification.NotificationService;
import com.unilink.security.LoginThrottle;
import com.unilink.security.Principals;
import com.unilink.storage.StorageGateway;
import com.unilink.team.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Worker D (Phase 3, P2): data-integrity + business-logic fixes (audit
 *  I-11–I-18). Service-level tests on H2, same setup as Phase2ServiceTest.
 *  No live MySQL is involved; the Flyway chain (V011) is covered statically
 *  by MigrationV011Test and must still be proven by a genuine MySQL run. */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-phase3d;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage",
  "app.encryption-key=test-phase3d-key"
})
@Transactional
class Phase3WorkerDTest {
  @Autowired TestDataFactory factory;
  @Autowired MessagingService messaging;
  @Autowired PostService posts;
  @Autowired TeamService teams;
  @Autowired AdminService admin;
  @Autowired AdminAuthService adminAuth;
  @Autowired AuthService auth;
  @Autowired VerificationFacade verification;
  @Autowired ModerationService moderation;
  @Autowired NotificationService notifications;
  @Autowired NotificationEventListener notificationListener;
  @Autowired AccountDeletionService deletion;
  @Autowired ScheduledJobs jobs;
  @Autowired UserRepository users;
  @Autowired AdminIpAllowlistRepository allowlist;
  @Autowired AppealRepository appeals;
  @Autowired VerificationRequestRepository requests;
  @Autowired VerificationAttemptRepository attempts;
  @Autowired IdentityChangeRequestRepository changeRequests;
  @Autowired LoginAttemptRepository loginAttempts;
  @Autowired NotificationRepository notificationRepo;
  @Autowired NotificationPreferenceRepository notificationPrefs;
  @Autowired DepartmentRepository departments;
  @Autowired LoginThrottle throttle;
  @Autowired StorageGateway storage;
  @Autowired JdbcTemplate jdbc;

  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4};

  @BeforeEach void setup() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION (PRIMARY_ID CHAR(36) PRIMARY KEY, SESSION_ID CHAR(36), CREATION_TIME BIGINT, LAST_ACCESS_TIME BIGINT, MAX_INACTIVE_INTERVAL INT, EXPIRY_TIME TIMESTAMP, PRINCIPAL_NAME VARCHAR(100))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID CHAR(36), ATTRIBUTE_NAME VARCHAR(200), ATTRIBUTE_BYTES BLOB, PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME))");
    throttle.resetForTests();
    TestProviders.reset();
  }

  private Principals.UnilinkUserPrincipal principal(User u) {
    Long catalog = departments.findById(u.getDepartmentId()).map(Department::getCatalogId).orElse(null);
    return new Principals.UnilinkUserPrincipal(u.getId(), u.getEmail(), "x", u.getUniversityId(),
      u.getDepartmentId(), catalog, u.getState(), true);
  }
  private Principals.UnilinkUserPrincipal unverifiedPrincipal(User u) {
    Long catalog = departments.findById(u.getDepartmentId()).map(Department::getCatalogId).orElse(null);
    return new Principals.UnilinkUserPrincipal(u.getId(), u.getEmail(), "x", u.getUniversityId(),
      u.getDepartmentId(), catalog, "REGISTERED", true);
  }
  private Principals.AdminPrincipal platformAdmin() {
    AdminAccount a = factory.admin("d3-platform-" + System.nanoTime() + "@example.com", "PLATFORM_ADMIN", null);
    return new Principals.AdminPrincipal(a.getId(), a.getEmail(), "x", "PLATFORM_ADMIN", null, true, true);
  }
  private Principals.AdminPrincipal moderator(Long uni) {
    AdminAccount a = factory.admin("d3-mod-" + System.nanoTime() + "@example.com", "UNIVERSITY_MODERATOR", uni);
    return new Principals.AdminPrincipal(a.getId(), a.getEmail(), "x", "UNIVERSITY_MODERATOR", uni, true, true);
  }
  private User user(String email) { return users.findByEmail(email).orElseThrow(); }

  private TestDataFactory.Fixture fixtureWithStudents() {
    var f = factory.seedMaster();
    factory.verifiedStudent("d3-a@example.com", "D3A", f.mu(), f.muCse(), f.muBatch());
    factory.verifiedStudent("d3-b@example.com", "D3B", f.mu(), f.muCse(), f.muBatch());
    factory.verifiedStudent("d3-c@example.com", "D3C", f.du(), f.duCse(), f.duBatch());
    return f;
  }

  /** A user in PENDING_REVIEW with a matching request (for queue/decision tests). */
  private User pendingUser(String email, String studentId, TestDataFactory.Fixture f, String phone) {
    User u = new User();
    u.setFullName("Pending " + studentId); u.setEmail(email); u.setPhone(phone);
    u.setAddress("Test address"); u.setStudentId(studentId);
    u.setUniversityId(f.mu().getId()); u.setDepartmentId(f.muCse().getId()); u.setBatchId(f.muBatch().getId());
    u.setState("PENDING_REVIEW");
    u = users.save(u);
    VerificationRequest r = new VerificationRequest();
    r.setUserId(u.getId()); r.setStatus("PENDING_REVIEW"); r.setOcrStatus("DONE"); r.setOcrMatchScore(0.9);
    requests.save(r);
    return u;
  }
  private VerificationRequest requestOf(User u) {
    return requests.findTopByUserIdOrderBySubmittedAtDesc(u.getId()).orElseThrow();
  }

  // ---- A: MessageAttachment.createdAt persists and is returned ----
  @Test void messageAttachmentCreatedAtIsPersistedAndReturned() {
    fixtureWithStudents();
    var a = principal(user("d3-a@example.com"));
    var b = principal(user("d3-b@example.com"));
    var room = messaging.createPrivateRoom(a, b.userId());
    var sent = messaging.sendMessageWithAttachment(a, room.publicId(), "see this", "pic.png", "image/png", PNG);
    assertTrue(sent.hasAttachment());
    var listed = messaging.listMessageAttachments(a, room.publicId(), sent.id());
    assertEquals(1, listed.size());
    assertNotNull(listed.get(0).createdAt(), "AttachmentDto.createdAt must be set (was always null before V011)");
  }

  // ---- I-11: verification-decision email (in-app + email) ----
  @Test void verificationDecisionSendsEmailAsWellAsInAppNotification() {
    var f = fixtureWithStudents();
    User u = pendingUser("d3-decide@example.com", "D3DEC", f, "01711111111");
    notificationListener.onVerified(new DomainEvents.StudentVerified(u.getId()));
    assertTrue(TestProviders.SIMPLE_EMAILS.stream().anyMatch(s -> s.startsWith("d3-decide@example.com:Your UniLink verification was approved")),
        "approval email must be sent, got: " + TestProviders.SIMPLE_EMAILS);
    assertEquals(1, notificationRepo.findAll().stream()
        .filter(n -> n.getRecipientId().equals(u.getId()) && "VERIFICATION_DECISION".equals(n.getType())).count());
    TestProviders.reset();
    notificationListener.onRejected(new DomainEvents.StudentRejected(u.getId(), "ID unreadable"));
    assertTrue(TestProviders.SIMPLE_EMAILS.stream().anyMatch(s -> s.startsWith("d3-decide@example.com:Your UniLink verification was rejected")),
        "rejection email must be sent, got: " + TestProviders.SIMPLE_EMAILS);
  }

  @Test void verificationApproveTransitionsState() {
    var f = fixtureWithStudents();
    User u = pendingUser("d3-approve@example.com", "D3APP", f, "01711111112");
    var dto = verification.approve(requestOf(u).getId(), platformAdmin(), "127.0.0.1");
    assertEquals("APPROVED", dto.status());
    assertEquals("VERIFIED", users.findById(u.getId()).orElseThrow().getState());
  }

  // ---- I-13: suspend/ban issues the appeal token and emails it ----
  @Test void suspendIssuesAppealTokenAndEmailsLink() {
    fixtureWithStudents();
    User u = user("d3-a@example.com");
    var result = admin.changeUserState(platformAdmin(), u.getId(), "SUSPEND", "repeated abuse", "127.0.0.1");
    assertEquals("SUSPENDED", result.user().state());
    var appeal = appeals.findAll().stream()
        .filter(a -> a.getAppellantId().equals(u.getId()) && "AWAITING_SUBMISSION".equals(a.getStatus()))
        .findFirst().orElseThrow(() -> new AssertionError("no suspension-appeal token row was created"));
    assertNotNull(appeal.getTokenHash());
    assertNotNull(appeal.getTokenExpiresAt());
    assertTrue(TestProviders.SIMPLE_EMAILS.stream().anyMatch(s -> s.startsWith("d3-a@example.com:Your UniLink account was suspended")),
        "suspension email must be sent, got: " + TestProviders.SIMPLE_EMAILS);
  }

  @Test void moderatorCannotSuspendOrBan() {
    var f = fixtureWithStudents();
    User u = user("d3-a@example.com");
    var mod = moderator(f.mu().getId());
    assertThrows(Exceptions.Forbidden.class,
        () -> admin.changeUserState(mod, u.getId(), "SUSPEND", "abuse", "127.0.0.1"));
    assertThrows(Exceptions.Forbidden.class,
        () -> admin.changeUserState(mod, u.getId(), "BAN", "abuse", "127.0.0.1"));
    assertEquals("VERIFIED", users.findById(u.getId()).orElseThrow().getState());
    // The moderator path is the recommendation flow, which still works.
    var rec = admin.recommendSuspension(mod, u.getId(), "abuse", "127.0.0.1");
    assertEquals("PENDING", rec.status());
    // …and a Platform Admin confirming it suspends AND emails the appeal link.
    admin.decideRecommendation(platformAdmin(), rec.id(), true, "127.0.0.1");
    assertEquals("SUSPENDED", users.findById(u.getId()).orElseThrow().getState());
    assertTrue(appeals.findAll().stream().anyMatch(a -> a.getAppellantId().equals(u.getId())));
    assertTrue(TestProviders.SIMPLE_EMAILS.stream().anyMatch(s -> s.startsWith("d3-a@example.com:Your UniLink account was suspended")));
  }

  // ---- I-14: dashboard pendingAppeals is scoped ----
  @Test void dashboardPendingAppealsIsScopedToModeratorUniversity() {
    var f = fixtureWithStudents();
    User muUser = user("d3-a@example.com");
    User duUser = user("d3-c@example.com");
    Appeal a1 = new Appeal(); a1.setAppellantId(muUser.getId()); a1.setTargetType("POST"); a1.setTargetId(1L);
    a1.setReason("r"); a1.setStatus("PENDING"); appeals.save(a1);
    Appeal a2 = new Appeal(); a2.setAppellantId(duUser.getId()); a2.setTargetType("POST"); a2.setTargetId(2L);
    a2.setReason("r"); a2.setStatus("PENDING"); appeals.save(a2);
    assertEquals(1L, admin.dashboard(moderator(f.mu().getId())).get("pendingAppeals"));
    assertEquals(1L, admin.dashboard(moderator(f.du().getId())).get("pendingAppeals"));
    assertEquals(2L, admin.dashboard(platformAdmin()).get("pendingAppeals"));
  }

  // ---- I-17: duplicate-phone flag; I-18: previousAttempts is per-request ----
  @Test void verificationQueueFlagsDuplicatePhonesAndCountsAttemptsPerRequest() {
    var f = factory.seedMaster();
    User a = pendingUser("d3-dup-a@example.com", "D3DUPA", f, "01722222222");
    User b = pendingUser("d3-dup-b@example.com", "D3DUPB", f, "01722222222");
    User c = pendingUser("d3-dup-c@example.com", "D3DUPC", f, "01733333333");
    // Two attempts on A's first request only.
    VerificationRequest reqA = requestOf(a);
    for (int i = 0; i < 2; i++) {
      VerificationAttempt att = new VerificationAttempt();
      att.setUserId(a.getId()); att.setVerificationRequestId(reqA.getId()); att.setOutcome("REJECTED"); attempts.save(att);
    }
    // A resubmits: a second request with no attempts of its own.
    VerificationRequest reqA2 = new VerificationRequest();
    reqA2.setUserId(a.getId()); reqA2.setStatus("PENDING_REVIEW"); reqA2.setOcrStatus("DONE"); requests.save(reqA2);
    var queue = admin.verificationQueue(platformAdmin(), null);
    var dtoA = queue.stream().filter(d -> d.id().equals(reqA.getId())).findFirst().orElseThrow();
    var dtoA2 = queue.stream().filter(d -> d.id().equals(reqA2.getId())).findFirst().orElseThrow();
    var dtoB = queue.stream().filter(d -> d.userId().equals(b.getId())).findFirst().orElseThrow();
    var dtoC = queue.stream().filter(d -> d.userId().equals(c.getId())).findFirst().orElseThrow();
    assertEquals(Boolean.TRUE, dtoA.duplicatePhone());
    assertEquals(Boolean.TRUE, dtoB.duplicatePhone());
    assertEquals(Boolean.FALSE, dtoC.duplicatePhone());
    assertEquals(2L, dtoA.previousAttempts());
    assertEquals(0L, dtoA2.previousAttempts(), "previousAttempts counts this request's attempts, not the user's total");
  }

  // ---- I-17: a phone on a suspended/banned account blocks registration ----
  @Test void registrationBlockedWhenPhoneBelongsToSuspendedAccount() {
    var f = factory.seedMaster();
    User suspended = factory.verifiedStudent("d3-susp@example.com", "D3SUSP", f.mu(), f.muCse(), f.muBatch());
    suspended.setPhone("01744444444"); users.save(suspended);
    admin.changeUserState(platformAdmin(), suspended.getId(), "SUSPEND", "abuse", "127.0.0.1");
    RegisterForm form = new RegisterForm();
    form.setFullName("New Student"); form.setPhone("01744444444");
    form.setEmail("d3-newsusp@example.com"); form.setUniversityId(f.mu().getId());
    form.setDepartmentId(f.muCse().getId()); form.setBatchId(f.muBatch().getId());
    form.setAddress("Dhaka"); form.setStudentId("D3NEW1"); form.setPassword("Student123");
    form.setIdCard(new MockMultipartFile("idCard", "id.png", "image/png", PNG));
    form.setProfilePhoto(new MockMultipartFile("profilePhoto", "photo.png", "image/png", PNG));
    assertThrows(Exceptions.Forbidden.class, () -> auth.register(form));
    assertTrue(users.findByEmail("d3-newsusp@example.com").isEmpty());
  }

  // ---- I-18: resubmit cap counts RESUBMIT outcomes only ----
  @Test void resubmitCapIgnoresDecisionRows() {
    var f = factory.seedMaster();
    User u = pendingUser("d3-resub@example.com", "D3RESUB", f, "01755555555");
    u.setState("REJECTED"); users.save(u);
    // Three decision rows (APPROVED/REJECTED) in the last 24 h must NOT
    // consume the 3-per-24h resubmit cap.
    for (int i = 0; i < 3; i++) {
      VerificationAttempt att = new VerificationAttempt();
      att.setUserId(u.getId()); att.setVerificationRequestId(requestOf(u).getId());
      att.setOutcome(i == 0 ? "APPROVED" : "REJECTED"); attempts.save(att);
    }
    assertDoesNotThrow(() -> auth.resubmit(u.getId(), null));
    assertEquals("PENDING_REVIEW", users.findById(u.getId()).orElseThrow().getState());
  }

  @Test void resubmitCapBlocksAfterThreeResubmissions() {
    var f = factory.seedMaster();
    User u = pendingUser("d3-resub2@example.com", "D3RESUB2", f, "01755555556");
    u.setState("REJECTED"); users.save(u);
    for (int i = 0; i < 3; i++) {
      VerificationAttempt att = new VerificationAttempt();
      att.setUserId(u.getId()); att.setVerificationRequestId(requestOf(u).getId()); att.setOutcome("RESUBMIT"); attempts.save(att);
    }
    assertThrows(Exceptions.TooManyRequests.class, () -> auth.resubmit(u.getId(), null));
  }

  // ---- I-18: identity-change decide path ----
  @Test void identityChangeDecideApprovesAndSwapsValues() {
    var f = fixtureWithStudents();
    User u = user("d3-a@example.com");
    IdentityChangeRequest r = new IdentityChangeRequest();
    r.setUserId(u.getId()); r.setNewUniversityId(f.du().getId()); r.setNewDepartmentId(f.duCse().getId());
    r.setNewStudentId("D3MOVED1"); r.setStatus("PENDING"); changeRequests.save(r);
    // A moderator from the other university cannot decide it.
    assertThrows(Exceptions.NotFound.class,
        () -> admin.decideIdentityChange(moderator(f.du().getId()), r.getId(), true, "ok", "127.0.0.1"));
    var decided = admin.decideIdentityChange(moderator(f.mu().getId()), r.getId(), true, "verified", "127.0.0.1");
    assertEquals("APPROVED", decided.status());
    User moved = users.findById(u.getId()).orElseThrow();
    assertEquals(f.du().getId(), moved.getUniversityId());
    assertEquals("D3MOVED1", moved.getStudentId());
    assertThrows(Exceptions.Conflict.class,
        () -> admin.decideIdentityChange(platformAdmin(), r.getId(), true, "again", "127.0.0.1"));
  }

  @Test void identityChangeDecideRejectsWithReason() {
    var f = fixtureWithStudents();
    User u = user("d3-a@example.com");
    IdentityChangeRequest r = new IdentityChangeRequest();
    r.setUserId(u.getId()); r.setNewUniversityId(f.du().getId()); r.setNewDepartmentId(f.duCse().getId());
    r.setNewStudentId("D3MOVED2"); r.setStatus("PENDING"); changeRequests.save(r);
    assertThrows(Exceptions.BadRequest.class,
        () -> admin.decideIdentityChange(platformAdmin(), r.getId(), false, "  ", "127.0.0.1"));
    var decided = admin.decideIdentityChange(platformAdmin(), r.getId(), false, "ID does not match", "127.0.0.1");
    assertEquals("REJECTED", decided.status());
    assertEquals(f.mu().getId(), users.findById(u.getId()).orElseThrow().getUniversityId());
  }

  // ---- I-18: account deletion collects keys from ALL requests; purge nulls User.idCardKey ----
  @Test void accountDeletionDeletesKeysFromAllVerificationRequests() {
    var f = fixtureWithStudents();
    User u = user("d3-a@example.com");
    String key1 = storage.store("id-cards", "old.png", "image/png", PNG);
    String key2 = storage.store("id-cards", "new.png", "image/png", PNG);
    String photoKey = storage.store("profile-photos", "p.png", "image/png", PNG);
    u.setIdCardKey(key2); u.setProfilePhotoKey(photoKey); users.save(u);
    VerificationRequest old = new VerificationRequest();
    old.setUserId(u.getId()); old.setStatus("REJECTED"); old.setOcrStatus("DONE");
    old.setIdCardKey(key1); old.setProfilePhotoKey(photoKey); requests.save(old);
    VerificationRequest latest = requestOf(u);
    latest.setIdCardKey(key2); latest.setProfilePhotoKey(photoKey); requests.save(latest);
    deletion.deleteOwnAccount(u.getId(), "Student123", "127.0.0.1");
    assertTrue(users.findById(u.getId()).isEmpty());
    assertFalse(storage.exists(key1), "the OLD request's ID image must be deleted too, not only the latest");
    assertFalse(storage.exists(key2));
    assertFalse(storage.exists(photoKey));
  }

  @Test void purgeIdImagesNullsStaleUserIdCardKey() {
    var f = fixtureWithStudents();
    User u = user("d3-a@example.com");
    String key = storage.store("id-cards", "purge.png", "image/png", PNG);
    u.setIdCardKey(key); users.save(u);
    VerificationRequest r = requestOf(u);
    r.setStatus("APPROVED"); r.setIdCardKey(key);
    r.setDecidedAt(LocalDateTime.now().minusDays(8)); requests.save(r);
    jobs.purgeIdImages();
    assertNull(requests.findById(r.getId()).orElseThrow().getIdCardKey());
    assertNull(users.findById(u.getId()).orElseThrow().getIdCardKey(),
        "User.idCardKey pointed at the purged object and must be nulled");
    assertFalse(storage.exists(key));
  }

  // ---- I-18: login attempts are written ----
  @Test void loginAttemptsAreRecorded() {
    fixtureWithStudents();
    User u = user("d3-a@example.com");
    LocalDateTime since = LocalDateTime.now().minusHours(1);
    assertThrows(Exceptions.BadRequest.class, () -> auth.login(u.getEmail(), "WrongPass1", "127.0.0.1"));
    assertEquals(1, loginAttempts.countByEmailAndSuccessFalseAndCreatedAtAfter(u.getEmail(), since));
    assertNotNull(auth.login(u.getEmail(), "Student123", "127.0.0.1"));
    assertEquals(1, loginAttempts.countByEmailAndSuccessFalseAndCreatedAtAfter(u.getEmail(), since));
  }

  // ---- I-15: secondary writes require a verified account ----
  @Test void unverifiedPrincipalCannotUseSecondaryWrites() {
    var f = fixtureWithStudents();
    User a = user("d3-a@example.com");
    User b = user("d3-b@example.com");
    var unverified = unverifiedPrincipal(a);
    var verified = principal(a);
    // Posts: react/unreact/bookmark/unbookmark are all verified-only now.
    var post = posts.createPost(verified, "POST", "NATIONAL", "A post", "Body text", List.of());
    posts.react(verified, "POST", post.id(), "LIKE");
    assertThrows(Exceptions.Forbidden.class, () -> posts.unreact(unverified, "POST", post.id()));
    posts.bookmark(verified, "POST", post.id());
    assertThrows(Exceptions.Forbidden.class, () -> posts.unbookmark(unverified, "POST", post.id()));
    // Messaging: rooms, listing and reading require verification.
    var room = messaging.createPrivateRoom(verified, principal(b).userId());
    assertThrows(Exceptions.Forbidden.class, () -> messaging.myRooms(unverified));
    assertThrows(Exceptions.Forbidden.class, () -> messaging.listMessages(unverified, room.publicId(), 20));
    // Comments: visibility rules apply to the list endpoint, not just writes.
    var deptPost = posts.createPost(verified, "POST", "DEPARTMENT", "Dept post", "Body text", List.of());
    posts.addComment(verified, deptPost.id(), "hello", null);
    assertThrows(Exceptions.NotFound.class, () -> posts.listComments(deptPost.id(), unverified));
    assertEquals(1, posts.listComments(deptPost.id(), verified).size());
    // Teams: listing an event's team is scoped the same way.
    var event = posts.createPost(verified, "EVENT", "DEPARTMENT", "Event", "Body text", List.of());
    assertThrows(Exceptions.NotFound.class, () -> teams.listForEvent(event.id(), unverified));
    assertDoesNotThrow(() -> teams.listForEvent(event.id(), verified));
  }

  @Test void appealRequiresOwnership() {
    fixtureWithStudents();
    User owner = user("d3-a@example.com");
    User other = user("d3-b@example.com");
    var post = posts.createPost(principal(owner), "POST", "NATIONAL", "Hidden post", "Body text", List.of());
    assertThrows(Exceptions.Forbidden.class,
        () -> moderation.appeal(principal(other), "POST", post.id(), "not mine"));
    assertThrows(Exceptions.NotFound.class,
        () -> moderation.appeal(principal(owner), "POST", 999999L, "no such post"));
    assertDoesNotThrow(() -> moderation.appeal(principal(owner), "POST", post.id(), "please restore"));
  }

  @Test void securityEmailsCannotBeDisabled() {
    fixtureWithStudents();
    User u = user("d3-a@example.com");
    assertThrows(Exceptions.BadRequest.class,
        () -> notifications.setPreference(u.getId(), "VERIFICATION_DECISION", false, true));
    assertDoesNotThrow(
        () -> notifications.setPreference(u.getId(), "TEAM_JOIN_ACCEPTED", false, true));
    assertTrue(notificationPrefs.findByUserIdAndType(u.getId(), "TEAM_JOIN_ACCEPTED")
        .map(p -> Boolean.FALSE.equals(p.getEmailEnabled())).orElse(false));
    assertTrue(notificationPrefs.findByUserIdAndType(u.getId(), "VERIFICATION_DECISION").isEmpty(),
        "no preference row must be written for the rejected security-email change");
  }

  // ---- I-16: admin IP allow-list enforced at login ----
  @Test void adminLoginBlockedFromUnlistedIp() {
    var f = factory.seedMaster();
    AdminAccount a = factory.admin("d3-ipmod@example.com", "UNIVERSITY_MODERATOR", f.mu().getId());
    AdminIpAllowlist row = new AdminIpAllowlist();
    row.setCidr("10.0.0.0/8"); row.setActive(true); allowlist.save(row);
    assertThrows(Exceptions.Forbidden.class,
        () -> adminAuth.login(a.getEmail(), "Admin12345678", "127.0.0.1"));
    // From inside the range the login proceeds to the TOTP enrolment stage.
    var ok = adminAuth.login(a.getEmail(), "Admin12345678", "10.5.6.7");
    assertEquals("NEEDS_ENROLMENT", ok.stage());
    // With no rows at all the allow-list is opt-in: login proceeds.
    allowlist.deleteAll();
    assertNotNull(adminAuth.login(a.getEmail(), "Admin12345678", "127.0.0.1"));
  }

  // ---- I-14: analytics aggregates scoped for moderators ----
  // AdminService.analytics() uses MySQL-only SQL (DATE(), DATE_SUB(...),
  // TIMESTAMPDIFF, CURDATE) that H2 cannot execute at all — the whole
  // method, not just the scoped aggregates, is MySQL-only (pre-existing
  // from Phase 2). So the scoping is proven two ways instead of by calling
  // analytics(): (a) statically, the exact scoped predicates are in the
  // production SQL; (b) live on H2, the activeUniversities predicate
  // returns 1 for a moderator and 2 for the platform on the same data.
  // A genuine analytics() run still needs real MySQL (see I-19).
  @Test void analyticsSqlScopesAggregatesToModeratorUniversity() throws Exception {
    String src = java.nio.file.Files.readString(
        java.nio.file.Path.of("src/main/java/com/unilink/admin/AdminService.java"));
    assertTrue(src.contains("AND p.scope_university_id=\"+uni"),
        "eventsPerSector must filter by the moderator's university");
    assertTrue(src.contains("JOIN users u ON u.id=vr.user_id AND u.university_id=\"+uni"),
        "verificationTurnaroundHours must join users and filter by university");
    assertTrue(src.contains("SELECT COUNT(DISTINCT university_id) FROM users WHERE state='VERIFIED'\" + (uni==null?\"\":\" AND university_id=\"+uni)"),
        "activeUniversities must filter by the moderator's university");
  }

  @Test void analyticsActiveUniversitiesPredicateScopesByUniversity() {
    var f = fixtureWithStudents();
    Long mu = f.mu().getId();
    Long scoped = jdbc.queryForObject(
        "SELECT COUNT(DISTINCT university_id) FROM users WHERE state='VERIFIED' AND university_id=" + mu, Long.class);
    Long global = jdbc.queryForObject(
        "SELECT COUNT(DISTINCT university_id) FROM users WHERE state='VERIFIED'", Long.class);
    assertEquals(1L, scoped);
    assertEquals(2L, global);
  }
}
