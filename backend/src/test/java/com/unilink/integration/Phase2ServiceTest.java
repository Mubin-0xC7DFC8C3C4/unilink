package com.unilink.integration;

import com.unilink.admin.AdminService;
import com.unilink.content.PostService;
import com.unilink.identity.domain.*;
import com.unilink.identity.dto.CatalogDtos.*;
import com.unilink.identity.repo.*;
import com.unilink.messaging.MessagingService;
import com.unilink.profile.ProfileService;
import com.unilink.security.Principals;
import com.unilink.team.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 2 (I-7/I-8/I-9) service tests, H2 context pattern from
 *  04-phase1-boot-blockers.md. Each test drives the real service against an
 *  in-memory H2 database (ddl-auto=create-drop); no live MySQL is involved.
 *  HTTP mapping itself is covered by ApplicationContextTest (all beans wire)
 *  plus the frontend Vitest contract tests (paths/payloads). */
@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-phase2;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage"
})
@Transactional
class Phase2ServiceTest {
  @Autowired TestDataFactory factory;
  @Autowired MessagingService messaging;
  @Autowired PostService posts;
  @Autowired ProfileService profiles;
  @Autowired TeamService teams;
  @Autowired EventDetailRepository eventDetails;
  @Autowired AdminService admin;
  @Autowired PasswordEncoder encoder;
  @Autowired UserRepository users;
  @Autowired DepartmentRepository departments;
  @Autowired SectorTagRepository sectors;
  @Autowired PostSectorTagRepository postSectors;
  @Autowired AdminAccountRepository admins;
  @Autowired PostRepository postRepo;
  @Autowired EventDetailRepository events;
  @Autowired ChatRoomRepository rooms;
  @Autowired MessageRepository messageRepo;
  @Autowired JdbcTemplate jdbc;

  /** H2 has no SPRING_SESSION tables under create-drop, and SessionService
   *  needs them to exist (empty) for the credential-reset test. (The
   *  `education_entries` / `achievements` tables are now created by Hibernate
   *  itself: their `year` columns are quoted in the entities.) */
  @BeforeEach void createH2OnlyTables(){
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION (PRIMARY_ID CHAR(36) PRIMARY KEY, SESSION_ID CHAR(36), CREATION_TIME BIGINT, LAST_ACCESS_TIME BIGINT, MAX_INACTIVE_INTERVAL INT, EXPIRY_TIME TIMESTAMP, PRINCIPAL_NAME VARCHAR(100))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID CHAR(36), ATTRIBUTE_NAME VARCHAR(200), ATTRIBUTE_BYTES BLOB, PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME))");
  }

  private static final byte[] PNG = {(byte)0x89,'P','N','G',13,10,26,10,1,2,3,4};

  private Principals.UnilinkUserPrincipal principal(User u){
    Long catalog = departments.findById(u.getDepartmentId()).map(Department::getCatalogId).orElse(null);
    return new Principals.UnilinkUserPrincipal(u.getId(), u.getEmail(), "x", u.getUniversityId(),
      u.getDepartmentId(), catalog, u.getState(), true);
  }
  private Principals.AdminPrincipal platformAdmin(){
    AdminAccount a = factory.admin("phase2-platform@example.com", "PLATFORM_ADMIN", null);
    return new Principals.AdminPrincipal(a.getId(), a.getEmail(), "x", "PLATFORM_ADMIN", null, true, true);
  }
  private TestDataFactory.Fixture fixtureWithStudents(){
    var f = factory.seedMaster();
    factory.verifiedStudent("phase2-a@example.com", "P2A", f.mu(), f.muCse(), f.muBatch());
    factory.verifiedStudent("phase2-b@example.com", "P2B", f.mu(), f.muCse(), f.muBatch());
    factory.verifiedStudent("phase2-c@example.com", "P2C", f.mu(), f.muCse(), f.muBatch());
    return f;
  }
  private User user(String email){ return users.findByEmail(email).orElseThrow(); }

  @Test void readReceiptGoesFromSingleToDoubleTick() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    var b = principal(user("phase2-b@example.com"));
    var room = messaging.createPrivateRoom(a, b.userId());
    var sent = messaging.sendMessage(a, room.publicId(), "hello", "k-read-1");
    assertFalse(sent.read(), "nobody else has read the new message yet");
    assertFalse(sent.hasAttachment());
    assertFalse(messaging.listMessages(a, room.publicId(), 10).get(0).read());
    messaging.markRead(b, room.publicId(), sent.id());
    assertTrue(messaging.listMessages(a, room.publicId(), 10).get(0).read(),
      "after B marks read, A sees read=true (double tick)");
  }

  @Test void tempRoomLifecycleInviteRemoveClose() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    var b = principal(user("phase2-b@example.com"));
    var c = principal(user("phase2-c@example.com"));
    var room = messaging.createTempRoom(a, "Hackathon squad", List.of(b.userId()));
    assertEquals("TEMP", room.type());
    assertTrue(room.memberIds().contains(b.userId()));
    var invited = messaging.inviteToTempRoom(a, room.publicId(), c.userId());
    assertTrue(invited.memberIds().contains(c.userId()));
    // A non-admin member cannot invite.
    assertThrows(com.unilink.common.Exceptions.Forbidden.class,
      () -> messaging.inviteToTempRoom(b, room.publicId(), c.userId()));
    var removed = messaging.removeRoomMember(a, room.publicId(), c.userId());
    assertFalse(removed.memberIds().contains(c.userId()));
    messaging.sendMessage(a, room.publicId(), "before close", "k-temp-1");
    var closed = messaging.closeRoom(a, room.publicId());
    assertTrue(closed.closed());
    assertThrows(com.unilink.common.Exceptions.BadRequest.class,
      () -> messaging.sendMessage(a, room.publicId(), "after close", "k-temp-2"));
  }

  @Test void postEditAndDeleteAreOwnerOnly() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    var b = principal(user("phase2-b@example.com"));
    var created = posts.createPost(a, "POST", "DEPARTMENT", "Original", "Body text", List.of());
    var updated = posts.updatePost(a, created.id(), "Edited", null, null, null);
    assertEquals("Edited", updated.title());
    assertThrows(com.unilink.common.Exceptions.NotFound.class,
      () -> posts.updatePost(b, created.id(), "Hijack", null, null, null));
    posts.deletePost(a, created.id());
    assertEquals("REMOVED", postRepo.findById(created.id()).orElseThrow().getStatus());
  }

  /** B1: the event-time subquery must be correlated to the outer post. With two
   *  events — one past-deadline, one upcoming — each list must exclude the other.
   *  (A single-event test passes vacuously: with one event the uncorrelated
   *  `id IN (subquery)` happens to agree.) */
  @Test void eventListsSeparatePastAndUpcoming() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    var upcoming = posts.createEvent(a, "DEPARTMENT", "Upcoming event", "Body text",
      LocalDateTime.now().plusDays(3), null, null, List.of());
    var pastEv = posts.createEvent(a, "DEPARTMENT", "Past event", "Body text",
      LocalDateTime.now().plusDays(3), null, null, List.of());
    EventDetail d = events.findById(pastEv.id()).orElseThrow();
    d.setDeadline(LocalDateTime.now().minusDays(1));
    events.save(d);
    var up = posts.listEvents(a, false);
    assertTrue(up.stream().anyMatch(e -> e.id().equals(upcoming.id())), "upcoming list must contain the upcoming event");
    assertTrue(up.stream().noneMatch(e -> e.id().equals(pastEv.id())), "upcoming list must NOT contain the past event");
    var past = posts.listEvents(a, true);
    assertTrue(past.stream().anyMatch(e -> e.id().equals(pastEv.id())), "past list must contain the past event");
    assertTrue(past.stream().noneMatch(e -> e.id().equals(upcoming.id())), "past list must NOT contain the upcoming event");
  }

  /** F1: activity() must apply the caller's visibility scope. An unverified
   *  caller sees only NATIONAL posts of another user — DEPARTMENT/UNIVERSITY
   *  posts stay hidden — while a verified caller in scope sees all three. */
  @Test void activityHidesNonNationalPostsFromUnverifiedCallers() {
    var f = fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    User unverified = new User();
    unverified.setFullName("Test Unverified"); unverified.setEmail("phase2-unverified@example.com");
    unverified.setPhone("+880100000001"); unverified.setAddress("Test address"); unverified.setStudentId("P2U");
    unverified.setUniversityId(f.mu().getId()); unverified.setDepartmentId(f.muCse().getId());
    unverified.setBatchId(f.muBatch().getId()); unverified.setState("REGISTERED");
    unverified = users.save(unverified);
    var u = principal(unverified);
    posts.createPost(a, "POST", "DEPARTMENT", "Dept post", "Body text", List.of());
    posts.createPost(a, "POST", "UNIVERSITY", "Uni post", "Body text", List.of());
    posts.createPost(a, "POST", "NATIONAL", "National post", "Body text", List.of());
    var seen = profiles.activity(a.userId(), u);
    assertEquals(1, seen.size(), "unverified caller must see only the NATIONAL post");
    assertEquals("National post", seen.get(0).title());
    assertEquals(3, profiles.activity(a.userId(), a).size(), "verified caller in scope sees all three");
  }

  /** SHAPE: PostDto carries commentCount and sector tag names. */
  @Test void postDtoCarriesCommentCountAndSectors() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    SectorTag s = new SectorTag(); s.setName("Phase2 DTO Sector"); s.setActive(true); s = sectors.save(s);
    var post = posts.createPost(a, "POST", "DEPARTMENT", "DTO post", "Body text", List.of(s.getId()));
    assertEquals(0, post.commentCount());
    assertEquals(List.of("Phase2 DTO Sector"), post.sectors());
    posts.addComment(a, post.id(), "First!", List.of());
    posts.addComment(a, post.id(), "Second!", List.of());
    var reloaded = posts.getPost(post.id(), a);
    assertEquals(2, reloaded.commentCount());
    assertEquals(List.of("Phase2 DTO Sector"), reloaded.sectors());
  }

  /** SHAPE: CommentDto carries the author's display name; BookmarkDto carries
   *  the bookmarked post's title for POST targets and null otherwise. */
  @Test void commentDtoCarriesAuthorNameAndBookmarkDtoCarriesTitle() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    var b = principal(user("phase2-b@example.com"));
    var post = posts.createPost(a, "POST", "DEPARTMENT", "Titled post", "Body text", List.of());
    var c = posts.addComment(b, post.id(), "Nice post", List.of());
    assertEquals("Test P2B", c.authorName());
    assertEquals("Test P2B", posts.listComments(post.id()).get(0).authorName());
    posts.bookmark(b, "POST", post.id());
    var marks = posts.listBookmarks(b.userId());
    assertEquals(1, marks.size());
    assertEquals("Titled post", marks.get(0).title());
    posts.bookmark(b, "USER", a.userId());
    assertEquals(2, posts.listBookmarks(b.userId()).size());
    assertNull(posts.listBookmarks(b.userId()).stream()
      .filter(m -> "USER".equals(m.targetType())).findFirst().orElseThrow().title());
  }

  @Test void eventDetailUpcomingEditDelete() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    var ev = posts.createEvent(a, "DEPARTMENT", "Seminar", "About the seminar",
      LocalDateTime.now().plusDays(3), null, "Hall A", List.of());
    assertEquals("Seminar", posts.getEvent(ev.id(), a).title());
    assertEquals("Hall A", posts.getEvent(ev.id(), a).location());
    assertTrue(posts.listEvents(a, false).stream().anyMatch(e -> e.id().equals(ev.id())));
    assertTrue(posts.listEvents(a, true).stream().noneMatch(e -> e.id().equals(ev.id())));
    var edited = posts.updateEvent(a, ev.id(), null, null, LocalDateTime.now().plusDays(5), null, "Hall B", null);
    assertEquals("Hall B", edited.location());
    assertThrows(com.unilink.common.Exceptions.NotFound.class,
      () -> posts.getEvent(posts.createPost(a, "POST", "DEPARTMENT", "Not an event", "Body", List.of()).id(), a));
    posts.deleteEvent(a, ev.id());
    assertEquals("REMOVED", postRepo.findById(ev.id()).orElseThrow().getStatus());
  }

  @Test void postAndMessageAttachments() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    var b = principal(user("phase2-b@example.com"));
    var post = posts.createPost(a, "POST", "DEPARTMENT", "With file", "See attached", List.of());
    var att = posts.addPostAttachment(a, post.id(), "notes.png", "image/png", PNG);
    assertEquals("notes.png", att.fileName());
    assertEquals(1, posts.listPostAttachments(a, post.id()).size());
    assertThrows(com.unilink.common.Exceptions.NotFound.class,
      () -> posts.addPostAttachment(b, post.id(), "notes.png", "image/png", PNG));
    var room = messaging.createPrivateRoom(a, b.userId());
    var msg = messaging.sendMessageWithAttachment(a, room.publicId(), "diagram", "d.png", "image/png", PNG);
    assertTrue(msg.hasAttachment());
    assertEquals(1, messaging.listMessageAttachments(a, room.publicId(), msg.id()).size());
    assertTrue(messaging.listMessages(a, room.publicId(), 10).get(0).hasAttachment());
  }

  @Test void sectorFollowAndUnfollow() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    SectorTag s = new SectorTag(); s.setName("Phase2 Sector"); s.setActive(true); s = sectors.save(s);
    profiles.followSector(a.userId(), s.getId());
    assertEquals(1, profiles.getProfile(a.userId(), a).sectors().size());
    profiles.unfollowSector(a.userId(), s.getId());
    assertEquals(0, profiles.getProfile(a.userId(), a).sectors().size());
  }

  @Test void portfolioListEditDeleteAndActivity() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    var pr = profiles.addProject(a.userId(), "Proj", "Desc", "https://example.com");
    assertEquals(1, profiles.listProjects(a.userId()).size());
    assertEquals("Proj 2", profiles.updateProject(a.userId(), pr.id(), "Proj 2", null, null).title());
    var ed = profiles.addEducation(a.userId(), "MU", "BSc", 2026);
    assertEquals("DU", profiles.updateEducation(a.userId(), ed.id(), "DU", null, null).institution());
    profiles.deleteEducation(a.userId(), ed.id());
    assertEquals(0, profiles.listEducation(a.userId()).size());
    var ach = profiles.addAchievement(a.userId(), "Won", "Hackathon", 2026);
    profiles.deleteAchievement(a.userId(), ach.id());
    assertEquals(0, profiles.listAchievements(a.userId()).size());
    profiles.deleteProject(a.userId(), pr.id());
    assertEquals(0, profiles.listProjects(a.userId()).size());
    posts.createPost(a, "POST", "DEPARTMENT", "Activity post", "Body text", List.of());
    assertEquals(1, profiles.activity(a.userId(), a).size());
  }

  @Test void photoAndCoverUploadRoundTrip() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    profiles.uploadPhoto(a.userId(), "me.png", "image/png", PNG);
    var photo = profiles.openPhoto(a.userId());
    assertArrayEquals(PNG, photo.bytes());
    assertEquals("image/png", photo.contentType());
    profiles.uploadCover(a.userId(), "cover.png", "image/png", PNG);
    assertArrayEquals(PNG, profiles.openCover(a.userId()).bytes());
    assertThrows(com.unilink.common.Exceptions.BadRequest.class,
      () -> profiles.uploadPhoto(a.userId(), "evil.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)));
  }

  @Test void coverUploadAccepts3MbAndRejects6Mb() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    byte[] threeMb = pngOfSize(3*1024*1024);
    profiles.uploadCover(a.userId(), "cover.png", "image/png", threeMb);
    assertArrayEquals(threeMb, profiles.openCover(a.userId()).bytes());
    assertThrows(com.unilink.common.Exceptions.BadRequest.class,
      () -> profiles.uploadCover(a.userId(), "big.png", "image/png", pngOfSize(6*1024*1024)));
    // Photo limit is unchanged at 2 MB (D-029a).
    assertThrows(com.unilink.common.Exceptions.BadRequest.class,
      () -> profiles.uploadPhoto(a.userId(), "big.png", "image/png", threeMb));
  }

  private static byte[] pngOfSize(int n){
    byte[] b = new byte[n];
    System.arraycopy(PNG, 0, b, 0, PNG.length);
    return b;
  }

  @Test void teamCreationEnforcesEventVisibilityAndArchive() {
    var f = fixtureWithStudents();
    factory.verifiedStudent("phase2-du@example.com", "P2DU", f.du(), f.duCse(), f.duBatch());
    var a = principal(user("phase2-a@example.com")); // Metropolitan University
    var b = principal(user("phase2-b@example.com")); // Metropolitan University
    var du = principal(user("phase2-du@example.com")); // University of Dhaka
    // Cross-university DEPARTMENT-scoped event: 404, existence not leaked.
    var duDeptEvent = posts.createEvent(du, "DEPARTMENT", "DU seminar", "Body text",
      LocalDateTime.now().plusDays(3), null, null, List.of());
    assertThrows(com.unilink.common.Exceptions.NotFound.class,
      () -> teams.createTeam(a, duDeptEvent.id(), "Squad", 4));
    // NATIONAL event from another university is open to verified students.
    var duNational = posts.createEvent(du, "NATIONAL", "Open hackathon", "Body text",
      LocalDateTime.now().plusDays(3), null, null, List.of());
    assertNotNull(teams.createTeam(a, duNational.id(), "Squad", 4).id());
    // Archived event in the caller's own scope is rejected (400).
    var own = posts.createEvent(a, "DEPARTMENT", "MU seminar", "Body text",
      LocalDateTime.now().plusDays(3), null, null, List.of());
    var ev = eventDetails.findById(own.id()).orElseThrow();
    ev.setArchivedAt(LocalDateTime.now());
    eventDetails.save(ev);
    assertThrows(com.unilink.common.Exceptions.BadRequest.class,
      () -> teams.createTeam(b, own.id(), "Late squad", 4));
  }

  @Test void communityContextPlacesStudentInRooms() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    var ctx = messaging.communityContext(a);
    assertEquals("Metropolitan University", ctx.universityName());
    assertNotNull(ctx.departmentRoomPublicId());
    assertNotNull(ctx.universityRoomPublicId());
    assertTrue(rooms.findByPublicId(ctx.departmentRoomPublicId()).isPresent());
  }

  @Test void moderatorsListEditAndResetCredentials() {
    var f = factory.seedMaster();
    var platform = platformAdmin();
    AdminAccount mod = factory.admin("phase2-mod@example.com", "UNIVERSITY_MODERATOR", f.mu().getId());
    assertTrue(admin.listModerators(platform).stream().anyMatch(m -> m.email().equals("phase2-mod@example.com")));
    var updated = admin.updateModerator(platform, mod.getId(), "phase2-mod2@example.com", null, null);
    assertEquals("phase2-mod2@example.com", updated.email());
    String before = admins.findById(mod.getId()).orElseThrow().getPasswordHash();
    var reset = admin.resetModeratorCredentials(platform, mod.getId(), "127.0.0.1");
    assertNotNull(reset.temporaryPassword());
    assertTrue(reset.temporaryPassword().length() >= 12);
    String after = admins.findById(mod.getId()).orElseThrow().getPasswordHash();
    assertNotEquals(before, after);
    assertTrue(encoder.matches(reset.temporaryPassword(), after));
  }

  @Test void masterDataBatchesAndDeactivate() {
    var f = factory.seedMaster();
    var platform = platformAdmin();
    BatchDto batch = admin.createBatch(platform, f.mu().getId(), f.muCse().getId(), "46", 2023, "127.0.0.1");
    assertEquals("46", batch.label());
    assertEquals("46-B", admin.updateBatch(platform, batch.id(), "46-B", null, null, "127.0.0.1").label());
    SectorTag s = new SectorTag(); s.setName("Phase2 Deactivate"); s.setActive(true); s = sectors.save(s);
    admin.deactivateMasterData(platform, "sectors", s.getId(), "127.0.0.1");
    assertEquals(false, sectors.findById(s.getId()).orElseThrow().getActive());
    assertEquals("Metropolitan University X",
      admin.updateUniversity(platform, f.mu().getId(), "Metropolitan University X", null, null, null, "127.0.0.1").name());
  }

  @Test void announcementsListUpdateDelete() {
    var f = factory.seedMaster();
    var platform = platformAdmin();
    var created = admin.createAnnouncement(platform, "Hello", "Body", "GLOBAL", null, null, "127.0.0.1");
    assertTrue(admin.listAnnouncements(platform).stream().anyMatch(x -> x.id().equals(created.id())));
    assertEquals("Hello 2", admin.updateAnnouncement(platform, created.id(), "Hello 2", null, null, null, "127.0.0.1").title());
    admin.deleteAnnouncement(platform, created.id(), "127.0.0.1");
    assertEquals(false, admin.listAnnouncements(platform).stream()
      .filter(x -> x.id().equals(created.id())).findFirst().orElseThrow().active());
  }

  @Test void eventModerationRemovesAndClosesRegistration() {
    fixtureWithStudents();
    var a = principal(user("phase2-a@example.com"));
    var platform = platformAdmin();
    var ev = posts.createEvent(a, "DEPARTMENT", "Fake event", "Body text",
      LocalDateTime.now().plusDays(2), null, null, List.of());
    var removed = admin.moderateEvent(platform, ev.id(), "REMOVE", null, null, null, "127.0.0.1");
    assertEquals("REMOVED", removed.status());
    var ev2 = posts.createEvent(a, "DEPARTMENT", "Real event", "Body text",
      LocalDateTime.now().plusDays(2), null, null, List.of());
    admin.moderateEvent(platform, ev2.id(), null, null, "UNIVERSITY", true, "127.0.0.1");
    assertEquals("UNIVERSITY", postRepo.findById(ev2.id()).orElseThrow().getVisibility());
    assertTrue(admin.listEventsForModeration(platform).stream().anyMatch(e -> e.id().equals(ev2.id())));
  }

  @Test void v009AddsClosedAtToChatRooms() throws Exception {
    jakarta.persistence.Column c = ChatRoom.class.getDeclaredField("closedAt").getAnnotation(jakarta.persistence.Column.class);
    assertNotNull(c);
    assertEquals("closed_at", c.name());
    String sql;
    try (InputStream in = Phase2ServiceTest.class.getResourceAsStream("/db/migration/V009__chat_room_closed_at.sql")) {
      assertNotNull(in, "V009 must be on the classpath");
      sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertTrue(sql.contains("ALTER TABLE chat_rooms ADD COLUMN closed_at"),
      "V009 must add closed_at to chat_rooms");
  }
}
