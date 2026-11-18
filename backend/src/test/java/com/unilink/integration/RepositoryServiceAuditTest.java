package com.unilink.integration;

import com.unilink.admin.AdminService;
import com.unilink.common.Exceptions;
import com.unilink.community.FeedService;
import com.unilink.content.PostService;
import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*;
import com.unilink.identity.service.AccountDeletionService;
import com.unilink.identity.service.AuthService;
import com.unilink.identity.service.OtpService;
import com.unilink.identity.service.PublicCatalogService;
import com.unilink.jobs.ScheduledJobs;
import com.unilink.messaging.MessagingService;
import com.unilink.moderation.ModerationService;
import com.unilink.notification.NotificationService;
import com.unilink.profile.ProfileService;
import com.unilink.security.Principals;
import com.unilink.storage.StorageGateway;
import com.unilink.team.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Repository/service-layer audit (Worker 2, D-142–D-156). Deliberately NOT
 *  @Transactional — several fixes concern what survives a service call's
 *  own transaction (e.g. the team-decide ordering), and inside one
 *  @Transactional test the persistence context would hide a rolled-back
 *  or prematurely-saved state (same trap as D-117). Every service call
 *  here runs in its own transaction, as in production. H2 only; no live
 *  MySQL is involved. */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-audit-w2;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage",
  "app.encryption-key=test-audit-w2-key"
})
class RepositoryServiceAuditTest {
  @Autowired TestDataFactory factory;
  @Autowired ModerationService moderation;
  @Autowired TeamService teams;
  @Autowired MessagingService messaging;
  @Autowired NotificationService notifications;
  @Autowired AuthService auth;
  @Autowired OtpService otp;
  @Autowired AdminService admin;
  @Autowired PostService posts;
  @Autowired ProfileService profiles;
  @Autowired FeedService feed;
  @Autowired PublicCatalogService catalog;
  @Autowired AccountDeletionService deletion;
  @Autowired ScheduledJobs jobs;
  @Autowired JdbcTemplate jdbc;
  @Autowired StorageGateway storage;
  @Autowired UserRepository users;
  @Autowired DepartmentRepository departments;
  @Autowired SectorTagRepository sectors;
  @Autowired PostSectorTagRepository postSectors;
  @Autowired PostRepository postRepo;
  @Autowired EventDetailRepository events;
  @Autowired AdminAccountRepository admins;
  @Autowired AdminAlertRepository alerts;
  @Autowired ModerationSnapshotRepository snapshots;
  @Autowired TeamJoinRequestRepository joinRequests;
  @Autowired TeamMemberRepository teamMembers;
  @Autowired RoomMemberRepository roomMembers;
  @Autowired ChatRoomRepository rooms;
  @Autowired MessageRepository messageRepo;
  @Autowired MessageAttachmentRepository messageAttachments;
  @Autowired ProfileRepository profileRepo;
  @Autowired AttachmentRepository attachmentRepo;

  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4};

  @BeforeEach void createH2OnlyTables(){
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION (PRIMARY_ID CHAR(36) PRIMARY KEY, SESSION_ID CHAR(36), CREATION_TIME BIGINT, LAST_ACCESS_TIME BIGINT, MAX_INACTIVE_INTERVAL INT, EXPIRY_TIME TIMESTAMP, PRINCIPAL_NAME VARCHAR(100))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID CHAR(36), ATTRIBUTE_NAME VARCHAR(200), ATTRIBUTE_BYTES BLOB, PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME))");
    TestProviders.reset();
  }

  private Principals.UnilinkUserPrincipal principal(User u){
    Long catalogId = departments.findById(u.getDepartmentId()).map(Department::getCatalogId).orElse(null);
    return new Principals.UnilinkUserPrincipal(u.getId(), u.getEmail(), "x", u.getUniversityId(),
      u.getDepartmentId(), catalogId, u.getState(), true);
  }
  private Principals.AdminPrincipal platformAdmin(String tag){
    AdminAccount a = factory.admin("w2-platform-" + tag + "@example.com", "PLATFORM_ADMIN", null);
    return new Principals.AdminPrincipal(a.getId(), a.getEmail(), "x", "PLATFORM_ADMIN", null, true, true);
  }
  private User student(String tag, String sid, TestDataFactory.Fixture f){
    return factory.verifiedStudent("w2-" + tag + "@example.com", sid, f.mu(), f.muCse(), f.muBatch());
  }
  private static <T extends Throwable> T assertCode(Class<T> type, String code, org.junit.jupiter.api.function.Executable exec){
    T t = assertThrows(type, exec);
    assertEquals(code, ((Exceptions.AppException) t).code());
    return t;
  }
  private Post makeEvent(User author, String title, LocalDateTime deadline){
    Post p = new Post(); p.setAuthorId(author.getId()); p.setType("EVENT"); p.setVisibility("NATIONAL");
    p.setScopeUniversityId(author.getUniversityId()); p.setScopeDepartmentId(author.getDepartmentId());
    p.setScopeCatalogId(departments.findById(author.getDepartmentId()).map(Department::getCatalogId).orElse(null));
    p.setTitle(title); p.setBody("Body of " + title); p.setStatus("ACTIVE");
    p = postRepo.save(p);
    EventDetail ev = new EventDetail(); ev.setId(p.getId()); ev.setDeadline(deadline);
    ev.setRegistrationClosed(false); events.save(ev);
    return p;
  }

  // D-142: strike alerts must be addressed to a real admin (and the hide
  // must count in the same transaction). Before the fix the alert row had
  // adminId null — invisible to every admin — and the count relied on a
  // DB-maintained updated_at the persistence context had not seen, so the
  // alert fired one hide late.
  @Test void strikeAlertIsAddressedToUniversityModerator(){
    var f = factory.seedMaster();
    AdminAccount mod = factory.admin("w2-mod-s1@example.com", "UNIVERSITY_MODERATOR", f.mu().getId());
    User author = student("s1-author", "S1A", f);
    List<User> reporters = List.of(student("s1-r1", "S1R1", f), student("s1-r2", "S1R2", f),
      student("s1-r3", "S1R3", f), student("s1-r4", "S1R4", f), student("s1-r5", "S1R5", f));
    for (int i = 0; i < 3; i++){
      Post p = factory.post(author, "NATIONAL", "Strike post " + i);
      for (User r : reporters) moderation.report(principal(r), "POST", p.getId(), "spam");
      assertEquals("HIDDEN_AUTO", postRepo.findById(p.getId()).orElseThrow().getStatus(),
        "5 distinct reporters must auto-hide the post");
    }
    List<AdminAlert> mine = alerts.findByAdminIdOrderByCreatedAtDesc(mod.getId());
    assertTrue(mine.stream().anyMatch(a -> "STRIKE_FLAG".equals(a.getType()) && author.getId().equals(a.getTargetId())),
      "the moderator of the author's university must receive the STRIKE_FLAG alert");
    assertTrue(alerts.findAll().stream().noneMatch(a -> a.getAdminId() == null),
      "no strike alert may be written without an admin addressee");
  }

  // D-143: a suspension-appeal link is single-use.
  @Test void appealTokenIsSingleUse(){
    var f = factory.seedMaster();
    User u = student("s2-user", "S2U", f);
    var token = moderation.createSuspensionAppealToken(u.getId());
    var first = moderation.appealByToken(token.token(), "USER", u.getId(), "please reconsider");
    assertEquals("PENDING", first.status());
    assertCode(Exceptions.BadRequest.class, "TOKEN_USED",
      () -> moderation.appealByToken(token.token(), "USER", u.getId(), "second try"));
  }

  // D-144: reporting a message snapshots it plus the 10 preceding messages.
  @Test void messageReportSnapshotsTargetPlusPreceding(){
    var f = factory.seedMaster();
    User a = student("s3-a", "S3A", f);
    User b = student("s3-b", "S3B", f);
    var room = messaging.createPrivateRoom(principal(a), b.getId());
    messaging.sendMessage(principal(a), room.publicId(), "first", "w2-s3-1");
    messaging.sendMessage(principal(a), room.publicId(), "second", "w2-s3-2");
    var third = messaging.sendMessage(principal(a), room.publicId(), "third", "w2-s3-3");
    var report = moderation.report(principal(b), "MESSAGE", third.id(), "abusive");
    List<ModerationSnapshot> snaps = snapshots.findByReportIdOrderByCreatedAtAsc(report.id());
    assertEquals(3, snaps.size(), "target + 2 preceding messages");
    assertTrue(snaps.stream().anyMatch(s -> third.id().equals(s.getMessageId())));
  }

  // D-145: a failed team accept must not consume the join request.
  @Test void failedTeamAcceptLeavesRequestPending(){
    var f = factory.seedMaster();
    User creator = student("s4-creator", "S4C", f);
    User u = student("s4-user", "S4U", f);
    Post event = makeEvent(creator, "S4 hackathon", LocalDateTime.now().plusHours(2));
    var team1 = teams.createTeam(principal(creator), event.getId(), "Team One", 4);
    teams.requestJoin(principal(u), team1.id(), "let me in");
    // U joins a second team for the same event first.
    teams.createTeam(principal(u), event.getId(), "Team Two", 4);
    Long reqId = joinRequests.findByTeamIdAndStatusOrderByCreatedAtAsc(team1.id(), "PENDING")
      .stream().findFirst().orElseThrow().getId();
    assertCode(Exceptions.Conflict.class, "ALREADY_IN_TEAM",
      () -> teams.decideRequest(principal(creator), reqId, true));
    assertEquals("PENDING", joinRequests.findById(reqId).orElseThrow().getStatus(),
      "the request must stay PENDING so it can be decided once the conflict clears");
    assertTrue(teamMembers.findByTeamIdAndUserId(team1.id(), u.getId()).isEmpty(),
      "the user must not be a member of the team the accept failed for");
  }

  // D-146: creating a private room twice returns the same room.
  @Test void privateRoomIsReusedForSamePair(){
    var f = factory.seedMaster();
    User a = student("s5-a", "S5A", f);
    User b = student("s5-b", "S5B", f);
    var first = messaging.createPrivateRoom(principal(a), b.getId());
    var second = messaging.createPrivateRoom(principal(a), b.getId());
    assertEquals(first.publicId(), second.publicId());
    long privateRooms = rooms.findAll().stream()
      .filter(r -> "PRIVATE".equals(r.getType())
        && roomMembers.existsByRoomIdAndUserId(r.getId(), a.getId())
        && roomMembers.existsByRoomIdAndUserId(r.getId(), b.getId()))
      .count();
    assertEquals(1, privateRooms, "exactly one PRIVATE room may exist for the pair");
  }

  // D-146: markRead must reject messages that do not exist or belong to another room.
  @Test void markReadRejectsForeignAndMissingMessages(){
    var f = factory.seedMaster();
    User a = student("s6-a", "S6A", f);
    User b = student("s6-b", "S6B", f);
    User c = student("s6-c", "S6C", f);
    var room1 = messaging.createPrivateRoom(principal(a), b.getId());
    var room2 = messaging.createPrivateRoom(principal(a), c.getId());
    var inRoom2 = messaging.sendMessage(principal(a), room2.publicId(), "elsewhere", "w2-s6-1");
    assertCode(Exceptions.NotFound.class, "NOT_FOUND",
      () -> messaging.markRead(principal(b), room1.publicId(), inRoom2.id()));
    assertCode(Exceptions.NotFound.class, "NOT_FOUND",
      () -> messaging.markRead(principal(b), room1.publicId(), 999999L));
    var inRoom1 = messaging.sendMessage(principal(a), room1.publicId(), "here", "w2-s6-2");
    assertDoesNotThrow(() -> messaging.markRead(principal(b), room1.publicId(), inRoom1.id()));
  }

  // D-147: mark-all-read must clear more than the first 50 unread rows.
  @Test void markAllReadMarksBeyondFifty(){
    var f = factory.seedMaster();
    User u = student("s7-user", "S7U", f);
    for (int i = 0; i < 55; i++)
      notifications.notify(u.getId(), "TEST", "POST", (long) i, "title " + i, "body");
    assertEquals(55, notifications.unreadCount(u.getId()));
    notifications.markAllRead(u.getId());
    assertEquals(0, notifications.unreadCount(u.getId()), "all 55 unread rows must be marked");
  }

  // D-148: the email-verification link resolves by token hash, not a table scan.
  @Test void emailVerificationByTokenWorks(){
    var f = factory.seedMaster();
    User u = student("s8-user", "S8U", f);
    String token = otp.sendEmailVerificationLink(u, "http://localhost");
    var dto = auth.verifyEmailByToken(token);
    assertTrue(dto.emailVerified(), "the link must verify the email");
    assertCode(Exceptions.BadRequest.class, "TOKEN_NOT_FOUND",
      () -> auth.verifyEmailByToken("no-such-token"));
  }

  // D-149: duplicate master data is a 409, not a 500 from the unique key.
  @Test void duplicateMasterDataIsConflictNot500(){
    var f = factory.seedMaster();
    var platform = platformAdmin("s9");
    assertCode(Exceptions.Conflict.class, "UNIVERSITY_EXISTS",
      () -> admin.createUniversity(platform, "Metropolitan University", "MU", "", "127.0.0.1"));
    admin.createSector(platform, "W2 Sector", "127.0.0.1");
    assertCode(Exceptions.Conflict.class, "SECTOR_EXISTS",
      () -> admin.createSector(platform, "W2 Sector", "127.0.0.1"));
    assertCode(Exceptions.Conflict.class, "DEPARTMENT_EXISTS",
      () -> admin.createDepartment(platform, f.mu().getId(), f.muCse().getCatalogId(), "CSE again", "127.0.0.1"));
    assertCode(Exceptions.NotFound.class, "NOT_FOUND",
      () -> admin.createDepartment(platform, 999999L, f.muCse().getCatalogId(), "Nope", "127.0.0.1"));
  }

  // D-150: unknown sector ids are 404 and repeated ids are deduped.
  @Test void postSectorIdsAreValidatedAndDeduped(){
    var f = factory.seedMaster();
    User u = student("s10-user", "S10U", f);
    assertCode(Exceptions.NotFound.class, "NOT_FOUND",
      () -> posts.createPost(principal(u), "POST", "NATIONAL", "t", "body", List.of(999999L)));
    SectorTag s = new SectorTag(); s.setName("W2 Post Sector"); s.setActive(true); s = sectors.save(s);
    var dto = posts.createPost(principal(u), "POST", "NATIONAL", "t2", "body",
      List.of(s.getId(), s.getId()));
    assertEquals(1, postSectors.findByPostId(dto.id()).size(), "a repeated sector id must tag once");
  }

  // D-150: following a sector that does not exist is 404, not a 500 FK failure.
  @Test void followUnknownSectorIsNotFound(){
    var f = factory.seedMaster();
    User u = student("s11-user", "S11U", f);
    assertCode(Exceptions.NotFound.class, "NOT_FOUND", () -> profiles.followSector(u.getId(), 999999L));
  }

  // D-151: a second identity-change request while one is PENDING is a Conflict.
  @Test void duplicatePendingIdentityChangeIsConflict(){
    var f = factory.seedMaster();
    User u = student("s12-user", "S12U", f);
    profiles.requestIdentityChange(u.getId(), f.du().getId(), f.duCse().getId(), "S12NEW");
    assertCode(Exceptions.Conflict.class, "REQUEST_PENDING",
      () -> profiles.requestIdentityChange(u.getId(), f.du().getId(), f.duCse().getId(), "S12NEW2"));
  }

  // D-152: the feed comment count comes from a COUNT, not a full comment load.
  @Test void feedCommentCountCountsWithoutLoadingComments(){
    var f = factory.seedMaster();
    User author = student("s13-author", "S13A", f);
    User commenter = student("s13-c", "S13C", f);
    Post p = factory.post(author, "NATIONAL", "Count me");
    for (int i = 0; i < 3; i++) posts.addComment(principal(commenter), p.getId(), "comment " + i, null);
    assertEquals(3, feed.toDto(postRepo.findById(p.getId()).orElseThrow()).commentCount());
  }

  // D-153: batches without a department filter are scoped to the university.
  @Test void batchesForUniversityAreScoped(){
    var f = factory.seedMaster();
    var rows = catalog.batches(f.mu().getId(), null);
    assertFalse(rows.isEmpty());
    assertTrue(rows.stream().allMatch(b -> f.mu().getId().equals(b.universityId())),
      "every returned batch must belong to the requested university");
    assertTrue(rows.stream().anyMatch(b -> "45".equals(b.label())));
  }

  // D-154: the event list is bounded to the 50 newest.
  @Test void eventListIsBoundedToFifty(){
    var f = factory.seedMaster();
    User u = student("s15-user", "S15U", f);
    for (int i = 0; i < 55; i++) makeEvent(u, "Bounded event " + i, LocalDateTime.now().plusHours(2));
    assertEquals(50, posts.listEvents(principal(u), false).size());
  }

  // D-155: account deletion also removes the cover photo and the files/rows
  // of the user's post attachments; message attachments stay with the
  // conversation history (sent messages are kept, sender SET NULL).
  @Test void accountDeletionRemovesCoverAndPostAttachmentFiles(){
    var f = factory.seedMaster();
    User u = student("s16-user", "S16U", f);
    User v = student("s16-friend", "S16V", f);
    String coverKey = storage.store("profile-covers", "cover.png", "image/png", PNG);
    Profile p = profileRepo.findById(u.getId()).orElseThrow();
    p.setCoverKey(coverKey); profileRepo.save(p);
    Post post = factory.post(u, "NATIONAL", "S16 post");
    String attKey = storage.store("post-attachments", "a.png", "image/png", PNG);
    Attachment a = new Attachment(); a.setOwnerType("POST"); a.setOwnerId(post.getId());
    a.setStorageKey(attKey); a.setFileName("a.png"); a.setContentType("image/png"); a.setSizeBytes(4L);
    attachmentRepo.save(a);
    var room = messaging.createPrivateRoom(principal(u), v.getId());
    var sent = messaging.sendMessageWithAttachment(principal(u), room.publicId(), "hi",
      "f.png", "image/png", PNG);
    Long msgId = sent.id();
    String msgAttKey = messageAttachments.findByMessageId(msgId).stream().findFirst()
      .orElseThrow().getStorageKey();
    // Sanity: everything exists before deletion.
    assertTrue(storage.exists(coverKey) && storage.exists(attKey) && storage.exists(msgAttKey));
    deletion.deleteOwnAccount(u.getId(), "Student123", "127.0.0.1");
    assertTrue(users.findById(u.getId()).isEmpty());
    assertFalse(storage.exists(coverKey), "cover photo file must go with the account");
    assertFalse(storage.exists(attKey), "post attachment file must go with the deleted post");
    assertTrue(attachmentRepo.findByOwnerTypeAndOwnerId("POST", post.getId()).isEmpty(),
      "post attachment rows must not orphan");
    assertTrue(storage.exists(msgAttKey),
      "message attachment files stay: the message remains as conversation history");
    assertTrue(messageRepo.findById(msgId).isPresent(), "the sent message itself stays");
  }

  // D-156: the archive job must not overwrite a moderation state.
  @Test void archivePastEventsKeepsModerationState(){
    var f = factory.seedMaster();
    User u = student("s17-user", "S17U", f);
    Post active = makeEvent(u, "S17 active past", LocalDateTime.now().minusHours(1));
    Post removed = makeEvent(u, "S17 removed past", LocalDateTime.now().minusHours(1));
    removed.setStatus("REMOVED"); postRepo.save(removed);
    jobs.archivePastEvents();
    assertEquals("ARCHIVED", postRepo.findById(active.getId()).orElseThrow().getStatus());
    assertEquals("REMOVED", postRepo.findById(removed.getId()).orElseThrow().getStatus(),
      "a REMOVED event must keep its moderation state, not become ARCHIVED");
  }
}
