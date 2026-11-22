package com.unilink.integration;

import com.unilink.admin.AdminService;
import com.unilink.common.Exceptions;
import com.unilink.community.FeedService;
import com.unilink.content.PostService;
import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*;
import com.unilink.identity.repo.FollowRepository;
import com.unilink.security.Principals;
import com.unilink.workspace.api.WorkspaceController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Full-stack integration build (2026-11-22): admin-managed events with
 *  draft/publish/cancel + capacity, five persisted reaction types,
 *  threaded comments, the Top feed ranking, and persisted project
 *  discussions. Service/controller level on H2, mirroring the existing
 *  integration tests. */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-fullstack;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage",
  "app.encryption-key=test-fullstack-key"
})
@Transactional
class FullStackIntegrationTest {
  @Autowired TestDataFactory factory;
  @Autowired AdminService admin;
  @Autowired PostService posts;
  @Autowired FeedService feed;
  @Autowired WorkspaceController workspace;
  @Autowired UserRepository users;
  @Autowired DepartmentRepository departments;
  @Autowired FollowRepository follows;
  @Autowired NotificationRepository notifications;

  private Principals.UnilinkUserPrincipal principal(User u) {
    Long catalog = departments.findById(u.getDepartmentId()).map(Department::getCatalogId).orElse(null);
    return new Principals.UnilinkUserPrincipal(u.getId(), u.getEmail(), "x", u.getUniversityId(),
      u.getDepartmentId(), catalog, u.getState(), true);
  }
  private Principals.AdminPrincipal platformAdmin() {
    AdminAccount a = factory.admin("fs-platform@example.com", "PLATFORM_ADMIN", null);
    return new Principals.AdminPrincipal(a.getId(), a.getEmail(), "x", "PLATFORM_ADMIN", null, true, true);
  }
  private Authentication auth(User u) {
    return new UsernamePasswordAuthenticationToken(principal(u), null, List.of());
  }
  private AdminService.AdminEventInput eventInput(String title, String status, Integer capacity) {
    return new AdminService.AdminEventInput(title, "Body of " + title, "Short", "NATIONAL",
      LocalDateTime.now().plusDays(10), null, "Asia/Dhaka", "Main Auditorium", null, null, null,
      "Physical", "UniLink Events Team", null, null, true, null, null, capacity, List.of(), null, null, status);
  }

  @Test void draftEventHiddenFromStudentsUntilPublished() {
    var f = factory.seedMaster();
    User student = factory.verifiedStudent("fs-a@example.com", "FSA", f.mu(), f.muCse(), f.muBatch());
    var dto = admin.createAdminEvent(platformAdmin(), eventInput("Draft Fest", "DRAFT", null), "127.0.0.1");
    assertFalse(posts.listEvents(principal(student), false).stream().anyMatch(e -> e.id().equals(dto.id())),
      "draft events must not reach the student Events page");
    admin.publishAdminEvent(platformAdmin(), dto.id(), "127.0.0.1");
    assertTrue(posts.listEvents(principal(student), false).stream().anyMatch(e -> e.id().equals(dto.id())),
      "published events must appear on the student Events page immediately");
  }

  @Test void capacityEnforcedAndCancellationNotifiesRespondents() {
    var f = factory.seedMaster();
    User a = factory.verifiedStudent("fs-cap-a@example.com", "FSCA", f.mu(), f.muCse(), f.muBatch());
    User b = factory.verifiedStudent("fs-cap-b@example.com", "FSCB", f.mu(), f.muCse(), f.muBatch());
    var dto = admin.createAdminEvent(platformAdmin(), eventInput("Tiny Workshop", "PUBLISHED", 1), "127.0.0.1");
    posts.respondToEvent(principal(a), dto.id(), "GOING");
    var full = assertThrows(Exceptions.AppException.class, () -> posts.respondToEvent(principal(b), dto.id(), "GOING"));
    assertEquals("EVENT_FULL", full.code());
    var regs = admin.eventRegistrations(platformAdmin(), dto.id());
    assertEquals(1, regs.size());
    assertEquals(a.getId(), regs.get(0).userId());
    admin.cancelAdminEvent(platformAdmin(), dto.id(), "127.0.0.1");
    assertTrue(notifications.findByRecipientIdOrderByCreatedAtDesc(a.getId(), PageRequest.of(0, 20))
      .stream().anyMatch(n -> "EVENT_CANCELLED".equals(n.getType())), "respondents are notified on cancellation");
    var closed = assertThrows(Exceptions.AppException.class, () -> posts.respondToEvent(principal(b), dto.id(), "INTERESTED"));
    assertEquals("EVENT_NOT_OPEN", closed.code());
  }

  @Test void allFiveReactionTypesPersistAndReplace() {
    var f = factory.seedMaster();
    User author = factory.verifiedStudent("fs-rx-a@example.com", "FSRA", f.mu(), f.muCse(), f.muBatch());
    User fan = factory.verifiedStudent("fs-rx-b@example.com", "FSRB", f.mu(), f.muCse(), f.muBatch());
    Post post = factory.post(author, "DEPARTMENT", "Reaction target");
    posts.react(principal(fan), "POST", post.getId(), "CELEBRATE");
    var page = feed.feed(principal(fan), "DEPARTMENT", null, 10, null);
    var dto = page.items().stream().filter(d -> d.id().equals(post.getId())).findFirst().orElseThrow();
    assertEquals("CELEBRATE", dto.viewerReaction());
    assertEquals(1, dto.reactionCount());
    posts.react(principal(fan), "POST", post.getId(), "INSIGHTFUL");
    var dto2 = feed.feed(principal(fan), "DEPARTMENT", null, 10, null).items().stream()
      .filter(d -> d.id().equals(post.getId())).findFirst().orElseThrow();
    assertEquals("INSIGHTFUL", dto2.viewerReaction());
    assertEquals(1, dto2.reactionCount(), "changing a reaction replaces it — still one row");
    assertThrows(Exceptions.AppException.class, () -> posts.react(principal(fan), "POST", post.getId(), "WOW"));
  }

  @Test void nestedCommentsPersistWithParentValidation() {
    var f = factory.seedMaster();
    User author = factory.verifiedStudent("fs-cm-a@example.com", "FSCMA", f.mu(), f.muCse(), f.muBatch());
    User guest = factory.verifiedStudent("fs-cm-b@example.com", "FSCMB", f.mu(), f.muCse(), f.muBatch());
    Post post = factory.post(author, "DEPARTMENT", "Comment target");
    Post other = factory.post(author, "DEPARTMENT", "Other post");
    var parent = posts.addComment(principal(guest), post.getId(), "First!", List.of());
    var reply = posts.addComment(principal(author), post.getId(), "Thanks!", List.of(), parent.id());
    assertEquals(parent.id(), reply.parentId());
    assertEquals(2, posts.listComments(post.getId(), principal(author)).size());
    var foreign = posts.addComment(principal(guest), other.getId(), "Elsewhere", List.of());
    assertThrows(Exceptions.AppException.class,
      () -> posts.addComment(principal(author), post.getId(), "Wrong thread", List.of(), foreign.id()));
  }

  @Test void topFeedRanksFollowedAuthorAboveSameDepartment() {
    var f = factory.seedMaster();
    User viewer = factory.verifiedStudent("fs-top-v@example.com", "FSTV", f.mu(), f.muCse(), f.muBatch());
    User followed = factory.verifiedStudent("fs-top-f@example.com", "FSTF", f.mu(), f.muCse(), f.muBatch());
    User peer = factory.verifiedStudent("fs-top-p@example.com", "FSTP", f.mu(), f.muCse(), f.muBatch());
    Follow fl = new Follow(); fl.setFollowerId(viewer.getId()); fl.setFollowedId(followed.getId()); follows.save(fl);
    Post peerPost = factory.post(peer, "DEPARTMENT", "Peer post");
    Post followedPost = factory.post(followed, "DEPARTMENT", "Followed post");
    var top = feed.feed(principal(viewer), "DEPARTMENT", null, 10, null, "top");
    assertEquals(followedPost.getId(), top.items().get(0).id(),
      "affinity: followed author (+15) beats same-department (+10) at equal engagement");
    assertTrue(top.items().stream().anyMatch(d -> d.id().equals(peerPost.getId())));
  }

  @Test void githubStatusHonestAndDiscussionsPersist() {
    var f = factory.seedMaster();
    User student = factory.verifiedStudent("fs-gh@example.com", "FSGH", f.mu(), f.muCse(), f.muBatch());
    var status = workspace.status(auth(student));
    assertFalse(status.configured(), "no GitHub OAuth app is configured in the test environment");
    assertFalse(status.connected());
    var posted = workspace.postDiscussion(auth(student),
      new com.unilink.workspace.DiscussionService.DiscussionBody("octocat/hello", "How is the data loaded?", null, "src/App.tsx"));
    assertNotNull(posted.id());
    var reply = workspace.postDiscussion(auth(student),
      new com.unilink.workspace.DiscussionService.DiscussionBody("octocat/hello", "It fetches on mount.", posted.id(), null));
    assertEquals(posted.id(), reply.parentId());
    var list = workspace.listDiscussions(auth(student), "octocat/hello");
    assertEquals(2, list.size());
  }
}
