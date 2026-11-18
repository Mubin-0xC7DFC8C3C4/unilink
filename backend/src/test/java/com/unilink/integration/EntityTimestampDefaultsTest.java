package com.unilink.integration;

import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit Worker 1 (§4A/§4B, D-157): entity–database consistency.
 *
 * <p>Why these tests assert Java-side values instead of a MySQL constraint
 * violation: the production schema (Flyway V001–V011, {@code ddl-auto: none})
 * declares e.g. {@code updated_at DATETIME(3) NOT NULL} with a
 * {@code DEFAULT CURRENT_TIMESTAMP(3)}, but Hibernate sends an explicit NULL
 * for every mapped field left null in Java — a column DEFAULT only applies
 * when the column is omitted from the INSERT, and Hibernate never omits a
 * mapped column. So on the user's Windows MySQL the bootstrap insert fails
 * with {@code Column 'updated_at' cannot be null}, while the H2 test
 * database (schema generated from the entities, columns nullable) happily
 * accepts the NULL. Asserting the persisted entity's field values is
 * therefore exactly the assertion that matters: it is what Hibernate will
 * send to MySQL. Each test persists the entity the way the production
 * service builds it (no timestamp/flag set by the caller) and reloads it
 * from the database.
 */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-entitydefaults;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage",
  "app.encryption-key=test-entitydefaults-key",
  "spring.session.jdbc.initialize-schema=always"
})
@Transactional
class EntityTimestampDefaultsTest {
  @Autowired TestDataFactory factory;
  @Autowired AdminAccountRepository admins;
  @Autowired UserRepository users;
  @Autowired UserCredentialRepository creds;
  @Autowired ProfileRepository profiles;
  @Autowired PostRepository posts;
  @Autowired UniversityRepository universities;
  @Autowired DepartmentRepository departments;
  @Autowired BatchRepository batches;
  @Autowired DepartmentCatalogRepository catalogs;
  @Autowired SectorTagRepository sectors;
  @Autowired SkillRepository skills;
  @Autowired VerificationRequestRepository requests;
  @Autowired AdminAlertRepository alerts;
  @Autowired AdminIpAllowlistRepository allowlist;
  @PersistenceContext EntityManager em;

  private TestDataFactory.Fixture fx;

  @BeforeEach void setup() {
    TestProviders.reset();
    fx = factory.seedMaster();
  }

  private <T> T reload(Class<T> type, Long id) {
    em.flush();
    em.clear();
    return em.find(type, id);
  }

  /** Issue A, as reported: bootstrap builds AdminAccount with no updatedAt. */
  @Test void adminAccountBootstrapGetsBothTimestampsAndMustChangeFalse() {
    AdminAccount a = new AdminAccount();
    a.setEmail("bootstrap-" + System.nanoTime() + "@example.com");
    a.setPasswordHash("hash");
    a.setRole("PLATFORM_ADMIN");
    a.setActive(true);
    a.setTotpEnrolled(false);
    a = admins.save(a);
    AdminAccount reloaded = reload(AdminAccount.class, a.getId());
    assertNotNull(reloaded.getCreatedAt(), "created_at must be set by @PrePersist");
    assertNotNull(reloaded.getUpdatedAt(), "updated_at must be set by @PrePersist (Issue A)");
    assertEquals(reloaded.getCreatedAt(), reloaded.getUpdatedAt(), "fresh row: updated_at starts at created_at");
    assertEquals(Boolean.FALSE, reloaded.getMustChangePassword(), "must_change_password defaults to false (V010 NOT NULL)");
  }

  /** @PreUpdate must refresh updatedAt, and never touch createdAt. */
  @Test void adminAccountUpdateRefreshesUpdatedAtOnly() {
    AdminAccount a = new AdminAccount();
    a.setEmail("upd-" + System.nanoTime() + "@example.com");
    a.setPasswordHash("hash");
    a.setRole("PLATFORM_ADMIN");
    a.setActive(true);
    a.setTotpEnrolled(false);
    a = admins.save(a);
    LocalDateTime created = reload(AdminAccount.class, a.getId()).getCreatedAt();
    AdminAccount loaded = em.find(AdminAccount.class, a.getId());
    loaded.setUpdatedAt(LocalDateTime.of(2020, 1, 1, 0, 0));
    loaded.setActive(false);
    admins.saveAndFlush(loaded);
    AdminAccount reloaded = reload(AdminAccount.class, a.getId());
    assertEquals(created, reloaded.getCreatedAt(), "update must never overwrite created_at");
    assertTrue(reloaded.getUpdatedAt().isAfter(LocalDateTime.of(2020, 1, 1, 0, 0)),
        "update must refresh updated_at to now");
  }

  /** AuthService.register builds User with no updatedAt. */
  @Test void userRegistrationGetsBothTimestamps() {
    User u = new User();
    u.setFullName("Test User");
    u.setEmail("u-" + System.nanoTime() + "@example.com");
    u.setPhone("+880100000001");
    u.setAddress("Test address");
    u.setStudentId("S-" + System.nanoTime());
    u.setUniversityId(fx.mu().getId());
    u.setDepartmentId(fx.muCse().getId());
    u.setBatchId(fx.muBatch().getId());
    u.setState("REGISTERED");
    u = users.save(u);
    User reloaded = reload(User.class, u.getId());
    assertNotNull(reloaded.getCreatedAt());
    assertNotNull(reloaded.getUpdatedAt(), "users.updated_at is NOT NULL — register must not send NULL");
  }

  @Test void userUpdateRefreshesUpdatedAtOnly() {
    User u = factory.verifiedStudent("upd-u-" + System.nanoTime() + "@example.com", "UPD" + System.nanoTime(), fx.mu(), fx.muCse(), fx.muBatch());
    LocalDateTime created = reload(User.class, u.getId()).getCreatedAt();
    User loaded = em.find(User.class, u.getId());
    loaded.setUpdatedAt(LocalDateTime.of(2020, 1, 1, 0, 0));
    loaded.setAddress("New address");
    users.saveAndFlush(loaded);
    User reloaded = reload(User.class, u.getId());
    assertEquals(created, reloaded.getCreatedAt());
    assertTrue(reloaded.getUpdatedAt().isAfter(LocalDateTime.of(2020, 1, 1, 0, 0)));
  }

  /** AuthService.register builds UserCredential with id + hash only. */
  @Test void userCredentialDefaultsMustChangeFalse() {
    User u = new User();
    u.setFullName("Cred User");
    u.setEmail("cred-" + System.nanoTime() + "@example.com");
    u.setPhone("+880100000002");
    u.setAddress("Test address");
    u.setStudentId("CR" + System.nanoTime());
    u.setUniversityId(fx.mu().getId());
    u.setDepartmentId(fx.muCse().getId());
    u.setBatchId(fx.muBatch().getId());
    u.setState("REGISTERED");
    u = users.save(u);
    UserCredential c = new UserCredential();
    c.setId(u.getId());
    c.setPasswordHash("hash");
    c = creds.save(c);
    assertEquals(Boolean.FALSE, reload(UserCredential.class, c.getId()).getMustChangePassword(),
        "user_credentials.must_change_password is NOT NULL (V010)");
  }

  /** AuthService.register sets phoneVisible; ProfileService.orElseGet paths do not. */
  @Test void profileBareInsertGetsPhoneVisibleFalseAndBothTimestamps() {
    User u = factory.verifiedStudent("prof-" + System.nanoTime() + "@example.com", "PR" + System.nanoTime(), fx.mu(), fx.muCse(), fx.muBatch());
    profiles.deleteById(u.getId());
    em.flush();
    Profile p = new Profile();
    p.setId(u.getId());
    p = profiles.save(p);
    Profile reloaded = reload(Profile.class, p.getId());
    assertEquals(Boolean.FALSE, reloaded.getPhoneVisible(), "profiles.phone_visible is NOT NULL DEFAULT 0");
    assertNotNull(reloaded.getCreatedAt());
    assertNotNull(reloaded.getUpdatedAt(), "profiles.updated_at is NOT NULL");
  }

  /** PostService.createPost sets type/visibility/status but no updatedAt. */
  @Test void postCreateGetsBothTimestamps() {
    User u = factory.verifiedStudent("post-" + System.nanoTime() + "@example.com", "PO" + System.nanoTime(), fx.mu(), fx.muCse(), fx.muBatch());
    Post p = new Post();
    p.setAuthorId(u.getId());
    p.setType("POST");
    p.setVisibility("UNIVERSITY");
    p.setScopeUniversityId(u.getUniversityId());
    p.setBody("Body");
    p.setStatus("ACTIVE");
    p = posts.save(p);
    Post reloaded = reload(Post.class, p.getId());
    assertNotNull(reloaded.getCreatedAt());
    assertNotNull(reloaded.getUpdatedAt(), "posts.updated_at is NOT NULL");
    assertEquals(reloaded.getCreatedAt(), reloaded.getUpdatedAt());
  }

  @Test void postUpdateRefreshesUpdatedAtOnly() {
    User u = factory.verifiedStudent("postu-" + System.nanoTime() + "@example.com", "PU" + System.nanoTime(), fx.mu(), fx.muCse(), fx.muBatch());
    Post p = factory.post(u, "UNIVERSITY", "t-" + System.nanoTime());
    LocalDateTime created = reload(Post.class, p.getId()).getCreatedAt();
    Post loaded = em.find(Post.class, p.getId());
    loaded.setUpdatedAt(LocalDateTime.of(2020, 1, 1, 0, 0));
    loaded.setTitle("edited");
    posts.saveAndFlush(loaded);
    Post reloaded = reload(Post.class, p.getId());
    assertEquals(created, reloaded.getCreatedAt());
    assertTrue(reloaded.getUpdatedAt().isAfter(LocalDateTime.of(2020, 1, 1, 0, 0)));
  }

  /** AdminService.createUniversity sets active but no updatedAt. */
  @Test void universityCreateGetsBothTimestamps() {
    University u = new University();
    u.setName("Uni-" + System.nanoTime());
    u.setActive(true);
    u = universities.save(u);
    University reloaded = reload(University.class, u.getId());
    assertNotNull(reloaded.getCreatedAt());
    assertNotNull(reloaded.getUpdatedAt(), "universities.updated_at is NOT NULL");
  }

  /** Neither AuthService.register nor resubmit sets submittedAt. */
  @Test void verificationRequestGetsSubmittedAt() {
    User u = factory.verifiedStudent("ver-" + System.nanoTime() + "@example.com", "VE" + System.nanoTime(), fx.mu(), fx.muCse(), fx.muBatch());
    VerificationRequest r = new VerificationRequest();
    r.setUserId(u.getId());
    r.setStatus("AWAITING_VERIFICATION");
    r.setOcrStatus("PENDING");
    r = requests.save(r);
    VerificationRequest reloaded = reload(VerificationRequest.class, r.getId());
    assertNotNull(reloaded.getCreatedAt());
    assertNotNull(reloaded.getSubmittedAt(), "verification_requests.submitted_at is NOT NULL — Java must set it, the DB default never fires for a mapped column");
  }

  /** ProfileService.addSkill builds Skill with name only. */
  @Test void newSkillDefaultsActiveTrue() {
    Skill s = new Skill();
    s.setName("Skill-" + System.nanoTime());
    s = skills.save(s);
    assertEquals(Boolean.TRUE, reload(Skill.class, s.getId()).getActive(), "skills.active is NOT NULL DEFAULT 1");
  }

  /** Both AdminAlert writers (AdminService overdue, ModerationService strikes) omit readFlag. */
  @Test void adminAlertDefaultsReadFlagFalse() {
    AdminAlert a = new AdminAlert();
    a.setType("STRIKE_FLAG");
    a.setTargetType("USER");
    a.setTargetId(1L);
    a.setMessage("needs review");
    a = alerts.save(a);
    assertEquals(Boolean.FALSE, reload(AdminAlert.class, a.getId()).getReadFlag(), "admin_alerts.read_flag is NOT NULL DEFAULT 0");
  }

  /** Latent: no JPA writer exists for these yet, but the columns are NOT NULL DEFAULT 1. */
  @Test void masterDataActiveDefaultsTrue() {
    DepartmentCatalog c = new DepartmentCatalog();
    c.setName("Catalog-" + System.nanoTime());
    c = catalogs.save(c);
    assertEquals(Boolean.TRUE, reload(DepartmentCatalog.class, c.getId()).getActive());

    Department d = new Department();
    d.setUniversityId(fx.mu().getId());
    d.setCatalogId(c.getId());
    d.setName("Dept-" + System.nanoTime());
    d = departments.save(d);
    assertEquals(Boolean.TRUE, reload(Department.class, d.getId()).getActive());

    Batch b = new Batch();
    b.setUniversityId(fx.mu().getId());
    b.setDepartmentId(fx.muCse().getId());
    b.setLabel("L-" + System.nanoTime());
    b = batches.save(b);
    assertEquals(Boolean.TRUE, reload(Batch.class, b.getId()).getActive());

    SectorTag s = new SectorTag();
    s.setName("Sector-" + System.nanoTime());
    s = sectors.save(s);
    assertEquals(Boolean.TRUE, reload(SectorTag.class, s.getId()).getActive());

    AdminIpAllowlist row = new AdminIpAllowlist();
    row.setCidr("10.0.0.0/8");
    row = allowlist.save(row);
    assertEquals(Boolean.TRUE, reload(AdminIpAllowlist.class, row.getId()).getActive());
  }

  /** A caller-supplied createdAt must survive persist and update untouched. */
  @Test void presetCreatedAtIsNeverOverwritten() {
    LocalDateTime preset = LocalDateTime.of(2021, 5, 4, 3, 2, 1);
    AdminAccount a = new AdminAccount();
    a.setEmail("preset-" + System.nanoTime() + "@example.com");
    a.setPasswordHash("hash");
    a.setRole("PLATFORM_ADMIN");
    a.setActive(true);
    a.setTotpEnrolled(false);
    a.setCreatedAt(preset);
    a = admins.save(a);
    assertEquals(preset, reload(AdminAccount.class, a.getId()).getCreatedAt());
    AdminAccount loaded = em.find(AdminAccount.class, a.getId());
    loaded.setActive(false);
    admins.saveAndFlush(loaded);
    AdminAccount reloaded = reload(AdminAccount.class, a.getId());
    assertEquals(preset, reloaded.getCreatedAt(), "update must never overwrite a preset created_at");
    assertTrue(reloaded.getUpdatedAt().isAfter(preset));
  }
}
