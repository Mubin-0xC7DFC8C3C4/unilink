package com.unilink.integration;

import com.unilink.admin.AdminService;
import com.unilink.common.Exceptions;
import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*;
import com.unilink.security.AesGcm;
import com.unilink.security.Principals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Security fixes (admin scope / validation / master data / audit export):
 *  service-level tests on H2, same setup as Phase2ServiceTest. */
@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-secfix;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage"
})
@Transactional
class AdminSecurityFixesTest {
  @Autowired TestDataFactory factory;
  @Autowired AdminService admin;
  @Autowired PasswordEncoder encoder;
  @Autowired UserRepository users;
  @Autowired AdminAccountRepository admins;
  @Autowired PostRepository postRepo;
  @Autowired CommentRepository comments;
  @Autowired AppealRepository appeals;
  @Autowired UserCredentialRepository creds;
  @Autowired AdminTotpSecretRepository totpSecrets;
  @Autowired BatchRepository batches;
  @Autowired SectorTagRepository sectors;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach void createH2OnlyTables(){
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION (PRIMARY_ID CHAR(36) PRIMARY KEY, SESSION_ID CHAR(36), CREATION_TIME BIGINT, LAST_ACCESS_TIME BIGINT, MAX_INACTIVE_INTERVAL INT, EXPIRY_TIME TIMESTAMP, PRINCIPAL_NAME VARCHAR(100))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID CHAR(36), ATTRIBUTE_NAME VARCHAR(200), ATTRIBUTE_BYTES BLOB, PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME))");
  }

  private AdminAccount newAdmin(String role, Long uni){
    return factory.admin("secfix-" + role + "-" + System.nanoTime() + "@example.com", role, uni);
  }
  private Principals.AdminPrincipal principal(AdminAccount a){
    return new Principals.AdminPrincipal(a.getId(), a.getEmail(), "x", a.getRole(), a.getUniversityId(), true, true);
  }
  private Principals.AdminPrincipal platformAdmin(){ return principal(newAdmin("PLATFORM_ADMIN", null)); }
  private Principals.AdminPrincipal moderator(Long uni){ return principal(newAdmin("UNIVERSITY_MODERATOR", uni)); }

  private TestDataFactory.Fixture fixtureWithStudents(){
    var f = factory.seedMaster();
    factory.verifiedStudent("sec-a@example.com", "SECA", f.mu(), f.muCse(), f.muBatch());
    factory.verifiedStudent("sec-b@example.com", "SECB", f.du(), f.duCse(), f.duBatch());
    return f;
  }
  private User user(String email){ return users.findByEmail(email).orElseThrow(); }
  private Comment commentOn(Post p, User author){
    Comment c = new Comment(); c.setPostId(p.getId()); c.setAuthorId(author.getId());
    c.setBody("comment body"); c.setStatus("ACTIVE"); return comments.save(c);
  }
  private Appeal appealFrom(User appellant, Post target){
    Appeal a = new Appeal(); a.setAppellantId(appellant.getId()); a.setTargetType("POST");
    a.setTargetId(target.getId()); a.setReason("please restore"); a.setStatus("PENDING");
    return appeals.save(a);
  }

  // ---- Fix 1: GLOBAL announcements are Platform Admin only ----
  @Test void moderatorCannotCreateGlobalAnnouncement(){
    var f = factory.seedMaster();
    var mod = moderator(f.mu().getId());
    assertThrows(Exceptions.Forbidden.class,
      () -> admin.createAnnouncement(mod, "Hi", "Body", "GLOBAL", null, null, "127.0.0.1"));
  }
  @Test void invalidOrNullAnnouncementScopeIs400(){
    var f = factory.seedMaster();
    var platform = platformAdmin();
    var mod = moderator(f.mu().getId());
    assertThrows(Exceptions.BadRequest.class,
      () -> admin.createAnnouncement(platform, "Hi", "Body", null, null, null, "127.0.0.1"));
    assertThrows(Exceptions.BadRequest.class,
      () -> admin.createAnnouncement(platform, "Hi", "Body", "NATIONAL", null, null, "127.0.0.1"));
    assertThrows(Exceptions.BadRequest.class,
      () -> admin.createAnnouncement(mod, "Hi", "Body", null, f.mu().getId(), null, "127.0.0.1"));
  }
  @Test void moderatorCanCreateUniversityAnnouncementForOwnUniversityOnly(){
    var f = factory.seedMaster();
    var mod = moderator(f.mu().getId());
    assertNotNull(admin.createAnnouncement(mod, "Hi", "Body", "UNIVERSITY", f.mu().getId(), null, "127.0.0.1"));
    assertThrows(Exceptions.NotFound.class,
      () -> admin.createAnnouncement(mod, "Hi", "Body", "UNIVERSITY", f.du().getId(), null, "127.0.0.1"));
  }

  // ---- Fix 2: COMMENT moderation is scope-checked ----
  @Test void moderatorCannotHideCommentOnOtherUniversityPost(){
    var f = fixtureWithStudents();
    var muStudent = user("sec-a@example.com");
    var duStudent = user("sec-b@example.com");
    Post p = factory.post(muStudent, "UNIVERSITY", "MU post");
    Comment c = commentOn(p, duStudent);
    assertThrows(Exceptions.NotFound.class,
      () -> admin.decideContent(moderator(f.du().getId()), "COMMENT", c.getId(), "HIDE", "spam", "127.0.0.1"));
    assertEquals("ACTIVE", comments.findById(c.getId()).orElseThrow().getStatus());
    admin.decideContent(moderator(f.mu().getId()), "COMMENT", c.getId(), "HIDE", "spam", "127.0.0.1");
    assertEquals("HIDDEN_ADMIN", comments.findById(c.getId()).orElseThrow().getStatus());
  }

  // ---- Fix 3: appeals are scoped to the moderator's university ----
  @Test void appealsScopedToModeratorUniversity(){
    var f = fixtureWithStudents();
    var muStudent = user("sec-a@example.com");
    var duStudent = user("sec-b@example.com");
    Post muPost = factory.post(muStudent, "UNIVERSITY", "MU post");
    Post duPost = factory.post(duStudent, "UNIVERSITY", "DU post");
    Appeal muAppeal = appealFrom(muStudent, muPost);
    Appeal duAppeal = appealFrom(duStudent, duPost);
    var modMu = moderator(f.mu().getId());
    List<Long> listed = admin.pendingAppeals(modMu).stream().map(a -> a.id()).toList();
    assertTrue(listed.contains(muAppeal.getId()), "own-university appeal must be listed");
    assertFalse(listed.contains(duAppeal.getId()), "other-university appeal must not be listed");
    assertEquals(2, admin.pendingAppeals(platformAdmin()).size(), "platform admin sees all");
    assertThrows(Exceptions.NotFound.class,
      () -> admin.decideAppeal(modMu, duAppeal.getId(), true, "ok", "127.0.0.1"));
    assertEquals("PENDING", appeals.findById(duAppeal.getId()).orElseThrow().getStatus());
    assertEquals("ACCEPTED",
      admin.decideAppeal(modMu, muAppeal.getId(), true, "ok", "127.0.0.1").status());
  }

  // ---- Fix 9: FORCE_RESET returns the temp password once ----
  @Test void forceResetReturnsTempPasswordThatAuthenticates(){
    fixtureWithStudents();
    var platform = platformAdmin();
    User u = user("sec-a@example.com");
    var result = admin.changeUserState(platform, u.getId(), "FORCE_RESET", "lost password", "127.0.0.1");
    assertNotNull(result.temporaryPassword());
    assertTrue(result.temporaryPassword().length() >= 12);
    assertTrue(encoder.matches(result.temporaryPassword(),
      creds.findById(u.getId()).orElseThrow().getPasswordHash()));
    var other = admin.changeUserState(platform, user("sec-b@example.com").getId(), "SUSPEND", "bad behaviour", "127.0.0.1");
    assertNull(other.temporaryPassword(), "non-reset actions must not carry a temp password");
  }

  // ---- Fix 10: credential reset deletes the stale TOTP secret ----
  @Test void resetModeratorCredentialsDeletesStaleTotpSecret(){
    var f = factory.seedMaster();
    var platform = platformAdmin();
    AdminAccount mod = newAdmin("UNIVERSITY_MODERATOR", f.mu().getId());
    AdminTotpSecret row = new AdminTotpSecret();
    row.setId(mod.getId());
    row.setSecretEncrypted(AesGcm.encrypt("dev-only-encryption-key-change-me", "JBSWY3DPEHPK3PXP"));
    totpSecrets.save(row);
    assertTrue(totpSecrets.findById(mod.getId()).isPresent());
    var reset = admin.resetModeratorCredentials(platform, mod.getId(), "127.0.0.1");
    assertTrue(encoder.matches(reset.temporaryPassword(),
      admins.findById(mod.getId()).orElseThrow().getPasswordHash()));
    assertTrue(totpSecrets.findById(mod.getId()).isEmpty(), "stale TOTP secret row must be gone");
    assertEquals(false, admins.findById(mod.getId()).orElseThrow().getTotpEnrolled());
  }

  // ---- Fix 11: updateModerator guards ----
  @Test void updateModeratorRejectsBadEmailAndSelfDeactivationAndSelfEmailChange(){
    var f = factory.seedMaster();
    var platform = platformAdmin();
    AdminAccount mod = newAdmin("UNIVERSITY_MODERATOR", f.mu().getId());
    assertThrows(Exceptions.BadRequest.class,
      () -> admin.updateModerator(platform, mod.getId(), "not-an-email", null, null));
    assertThrows(Exceptions.BadRequest.class,
      () -> admin.updateModerator(platform, mod.getId(), "   ", null, null));
    AdminAccount self = newAdmin("PLATFORM_ADMIN", null);
    var selfPrincipal = principal(self);
    assertThrows(Exceptions.BadRequest.class,
      () -> admin.updateModerator(selfPrincipal, self.getId(), null, null, false));
    assertThrows(Exceptions.BadRequest.class,
      () -> admin.updateModerator(selfPrincipal, self.getId(), "someone-else@example.com", null, null));
    String goodEmail = "ok-" + System.nanoTime() + "@example.com";
    assertEquals(goodEmail, admin.updateModerator(platform, mod.getId(), goodEmail, null, null).email());
  }

  // ---- Fix 12: createBatch pre-checks; blank names rejected on PUTs ----
  @Test void createBatchValidatesUniversityDepartmentAndDuplicate(){
    var f = factory.seedMaster();
    var platform = platformAdmin();
    assertThrows(Exceptions.NotFound.class,
      () -> admin.createBatch(platform, 999999L, f.muCse().getId(), "99", 2024, "127.0.0.1"));
    assertThrows(Exceptions.NotFound.class,
      () -> admin.createBatch(platform, f.mu().getId(), 999999L, "99", 2024, "127.0.0.1"));
    // seedMaster already created batch "45" for MU/CSE.
    assertThrows(Exceptions.Conflict.class,
      () -> admin.createBatch(platform, f.mu().getId(), f.muCse().getId(), "45", 2022, "127.0.0.1"));
    assertNotNull(admin.createBatch(platform, f.mu().getId(), f.muCse().getId(), "46", 2023, "127.0.0.1"));
  }
  @Test void masterDataPutsRejectBlankNames(){
    var f = factory.seedMaster();
    var platform = platformAdmin();
    assertThrows(Exceptions.BadRequest.class,
      () -> admin.updateUniversity(platform, f.mu().getId(), "  ", null, null, null, "127.0.0.1"));
    assertThrows(Exceptions.BadRequest.class,
      () -> admin.updateDepartment(platform, f.muCse().getId(), "", null, "127.0.0.1"));
    SectorTag s = new SectorTag(); s.setName("Sec"); s.setActive(true);
    final SectorTag savedSector = sectors.save(s);
    assertThrows(Exceptions.BadRequest.class,
      () -> admin.updateSector(platform, savedSector.getId(), " ", null, "127.0.0.1"));
    var batch = batches.findByUniversityIdAndDepartmentIdAndActiveTrue(f.mu().getId(), f.muCse().getId()).get(0);
    assertThrows(Exceptions.BadRequest.class,
      () -> admin.updateBatch(platform, batch.getId(), "  ", null, null, "127.0.0.1"));
  }

  // ---- Fix 13 (service part): negative audit limit is 400 ----
  @Test void auditLogsRejectsNegativeLimit(){
    var platform = platformAdmin();
    assertThrows(Exceptions.BadRequest.class, () -> admin.auditLogs(platform, -1));
    assertTrue(admin.auditLogs(platform, 0).isEmpty());
  }
  @Test void csvCellsAreDefusedAgainstFormulaInjection() throws Exception {
    Method csvCell = Class.forName("com.unilink.admin.api.AdminController")
      .getDeclaredMethod("csvCell", String.class);
    csvCell.setAccessible(true);
    assertEquals("'=SUM(A1:A2)", csvCell.invoke(null, "=SUM(A1:A2)"));
    assertEquals("'+2+5", csvCell.invoke(null, "+2+5"));
    assertEquals("'-cmd", csvCell.invoke(null, "-cmd"));
    assertEquals("'@cmd", csvCell.invoke(null, "@cmd"));
    assertEquals("plain", csvCell.invoke(null, "plain"));
    assertEquals("ab", csvCell.invoke(null, "a\rb"), "CR must be stripped");
    assertEquals("", csvCell.invoke(null, (String) null));
  }
}
