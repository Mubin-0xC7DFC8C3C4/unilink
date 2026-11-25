package com.unilink.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unilink.common.Exceptions;
import com.unilink.community.FeedService;
import com.unilink.connection.ConnectionService;
import com.unilink.content.PostService;
import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*;
import com.unilink.moderation.ModerationService;
import com.unilink.profile.ProfileService;
import com.unilink.search.SearchService;
import com.unilink.search.api.SearchController;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Slice D (D-200, D-201): search, connections and feed visibility.
 *  Same H2 context pattern as Phase2ServiceTest — real services against
 *  an in-memory H2 database (ddl-auto=create-drop); no live MySQL. */
@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-sliced;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage"
})
@Transactional
class SliceDTest {
  @Autowired TestDataFactory factory;
  @Autowired SearchService search;
  @Autowired ConnectionService connections;
  @Autowired FeedService feed;
  @Autowired PostService posts;
  @Autowired ProfileService profiles;
  @Autowired ModerationService moderation;
  @Autowired UserRepository users;
  @Autowired DepartmentRepository departments;
  @Autowired PostRepository postRepo;
  @Autowired EventDetailRepository eventDetails;
  @Autowired NotificationRepository notifications;
  @Autowired FollowRepository follows;
  @Autowired ConnectionRequestRepository connectionRequests;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach void createH2OnlyTables(){
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION (PRIMARY_ID CHAR(36) PRIMARY KEY, SESSION_ID CHAR(36), CREATION_TIME BIGINT, LAST_ACCESS_TIME BIGINT, MAX_INACTIVE_INTERVAL INT, EXPIRY_TIME TIMESTAMP, PRINCIPAL_NAME VARCHAR(100))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID CHAR(36), ATTRIBUTE_NAME VARCHAR(200), ATTRIBUTE_BYTES BLOB, PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME))");
  }

  private Principals.UnilinkUserPrincipal principal(User u){
    Long catalog = u.getDepartmentId()==null ? null
      : departments.findById(u.getDepartmentId()).map(Department::getCatalogId).orElse(null);
    return new Principals.UnilinkUserPrincipal(u.getId(), u.getEmail(), "x", u.getUniversityId(),
      u.getDepartmentId(), catalog, u.getState(), true);
  }
  private Authentication auth(User u){ return new UsernamePasswordAuthenticationToken(principal(u), null); }
  private SearchController searchController(){ return new SearchController(search, new AccessPolicy()); }

  private record People(User a, User b, User c, User d, User eee, User unverified, TestDataFactory.Fixture f) {}
  private People people(){
    var f = factory.seedMaster();
    Department eee = new Department();
    eee.setUniversityId(f.mu().getId()); eee.setCatalogId(f.muCse().getCatalogId());
    eee.setName("EEE — MU"); eee.setActive(true); eee = departments.save(eee);
    User a = factory.verifiedStudent("sliced-a@example.com", "SDA", f.mu(), f.muCse(), f.muBatch());
    User b = factory.verifiedStudent("sliced-b@example.com", "SDB", f.mu(), f.muCse(), f.muBatch());
    User c = factory.verifiedStudent("sliced-c@example.com", "SDC", f.mu(), f.muCse(), f.muBatch());
    User d = factory.verifiedStudent("sliced-d@example.com", "SDD", f.du(), f.duCse(), f.duBatch());
    User eeeUser = factory.verifiedStudent("sliced-eee@example.com", "SDE", f.mu(), eee, f.muBatch());
    a.setFullName("Sadia Islam"); users.saveAndFlush(a); // saveAndFlush:
    // a dirty-checked UPDATE is not visible to the raw JdbcTemplate
    // searches until flush (IDENTITY inserts are immediate, updates are not).
    User unverified = new User();
    unverified.setFullName("Pending Person"); unverified.setEmail("sliced-pending@example.com");
    unverified.setPhone("01700000009"); unverified.setAddress("Dhaka");
    unverified.setStudentId("SDP"); unverified.setUniversityId(f.mu().getId());
    unverified.setDepartmentId(f.muCse().getId()); unverified.setState("PENDING");
    unverified = users.save(unverified);
    return new People(a, b, c, d, eeeUser, unverified, f);
  }
  private Post eventPost(User author, String title, LocalDateTime deadline, String location, String organiser){
    Post p = new Post(); p.setAuthorId(author.getId()); p.setType("EVENT"); p.setVisibility("DEPARTMENT");
    p.setScopeUniversityId(author.getUniversityId()); p.setScopeDepartmentId(author.getDepartmentId());
    p.setScopeCatalogId(departments.findById(author.getDepartmentId()).map(Department::getCatalogId).orElse(null));
    p.setTitle(title); p.setBody("Event body"); p.setStatus("ACTIVE");
    p = postRepo.saveAndFlush(p);
    EventDetail ev = new EventDetail(); ev.setId(p.getId()); ev.setDeadline(deadline);
    ev.setLocation(location); ev.setOrganizerName(organiser); eventDetails.saveAndFlush(ev);
    return p;
  }
  private Post postAt(User author, String visibility, String title, LocalDateTime createdAt){
    Post p = factory.post(author, visibility, title);
    p.setCreatedAt(createdAt); return postRepo.saveAndFlush(p);
  }

  // ---- search (D-201) ----

  @Test void escapedPercentAndUnderscoreMatchLiterally(){
    var p = people();
    factory.post(p.a(), "NATIONAL", "100% sure about the result");
    factory.post(p.a(), "NATIONAL", "100_ proof of concept");
    var pct = search.searchPosts(principal(p.a()), "100%", null, null, 20);
    assertEquals(List.of("100% sure about the result"), pct.items().stream().map(SearchService.PostHit::title).toList());
    var under = search.searchPosts(principal(p.a()), "100_", null, null, 20);
    assertEquals(List.of("100_ proof of concept"), under.items().stream().map(SearchService.PostHit::title).toList());
  }

  @Test void multiTokenAndMatchesInAnyOrder(){
    var p = people();
    factory.post(p.a(), "NATIONAL", "Sadia Islam workshop");
    var reversed = search.searchPosts(principal(p.a()), "Islam Sadia", null, null, 20);
    assertTrue(reversed.items().stream().anyMatch(h -> "Sadia Islam workshop".equals(h.title())),
      "reversed tokens must still match (AND of per-token LIKEs)");
    var partial = search.searchPosts(principal(p.a()), "Sadia Rahman", null, null, 20);
    assertTrue(partial.items().isEmpty(), "every token must match");
    var usersReversed = search.searchUsers("Islam Sadia", null, 20);
    assertTrue(usersReversed.items().stream().anyMatch(h -> h.id().equals(p.a().getId())));
    var usersPartial = search.searchUsers("Sadia Rahman", null, 20);
    assertTrue(usersPartial.items().stream().noneMatch(h -> h.id().equals(p.a().getId())));
  }

  @Test void unverifiedSearchIsForbidden(){
    var p = people();
    assertThrows(Exceptions.Forbidden.class,
      () -> searchController().search(auth(p.unverified()), "dhaka", "all", null, null, null, 20));
  }

  @Test void blankQueryReturnsEmpty(){
    var p = people();
    assertEquals(0, search.searchPosts(principal(p.a()), "   ", null, null, 20).total());
    assertEquals(0, search.searchUsers("", null, 20).total());
    assertTrue(search.searchUniversities("  ").isEmpty());
    var res = searchController().search(auth(p.a()), "  ", "all", null, null, null, 20);
    assertEquals(0L, res.get("total"));
    assertEquals(List.of(), res.get("posts"));
    assertEquals(List.of(), res.get("users"));
  }

  /** D-201 keeps the search visibility predicate as-is: a DEPARTMENT
   *  post is visible to the whole of its university (the
   *  same-university UNIVERSITY/DEPARTMENT branch), not only to its own
   *  department — the same-university/other-department reader sees it,
   *  a reader from another university does not. (The brief's test line
   *  "invisible to other-department user" reads the other way; the
   *  predicate as specified in D-201 and in the audit context governs —
   *  flagged for the coordinator to rule on.) */
  @Test void departmentPostInvisibleOutsideUniversityButVisibleWithinIt(){
    var p = people();
    Post post = factory.post(p.a(), "DEPARTMENT", "CSE only announcement");
    var otherDeptSameUni = search.searchPosts(principal(p.eee()), "CSE only", null, null, 20);
    assertTrue(otherDeptSameUni.items().stream().anyMatch(h -> h.id().equals(post.getId())),
      "same-university reader sees a DEPARTMENT post (predicate's university branch)");
    var otherUni = search.searchPosts(principal(p.d()), "CSE only", null, null, 20);
    assertTrue(otherUni.items().stream().noneMatch(h -> h.id().equals(post.getId())),
      "DEPARTMENT post must not surface to another university");
    var sameDept = search.searchPosts(principal(p.b()), "CSE only", null, null, 20);
    assertTrue(sameDept.items().stream().anyMatch(h -> h.id().equals(post.getId())));
    assertThrows(Exceptions.NotFound.class, () -> posts.getPost(post.getId(), principal(p.d())));
    assertEquals(post.getId(), posts.getPost(post.getId(), principal(p.eee())).id());
  }

  @Test void nonVerifiedUserNeverReturned(){
    var p = people();
    var res = search.searchUsers("Pending Person", null, 20);
    assertTrue(res.items().isEmpty(), "only VERIFIED users are searchable");
  }

  @Test void phoneEmailAndStudentIdAbsentFromPayload() throws Exception {
    var p = people();
    var res = searchController().search(auth(p.a()), "Sadia", "all", null, null, null, 20);
    String json = new ObjectMapper().writeValueAsString(res);
    assertFalse(json.contains("phone"), "phone must never be selected/returned: " + json);
    assertFalse(json.contains("email"), "email must never be selected/returned: " + json);
    assertFalse(json.contains("studentId"), "student ID must never be selected/returned: " + json);
    JsonNode usersNode = new ObjectMapper().readTree(json).get("users");
    assertTrue(usersNode.isArray() && usersNode.size() == 1);
    java.util.Set<String> fields = new java.util.HashSet<>();
    usersNode.get(0).fieldNames().forEachRemaining(fields::add);
    assertEquals(Set.of("id", "fullName", "universityId", "departmentId", "matchedSkills"), fields);
  }

  @Test void expiredEventExcludedButLiveEventMatchesLocationAndOrganiser(){
    var p = people();
    Post live = eventPost(p.a(), "Robotics night", LocalDateTime.now().plusDays(3), "Central Library", "CSE Club");
    Post expired = eventPost(p.a(), "Old fest", LocalDateTime.now().minusDays(1), "Old Hall", "Old Club");
    var byLocation = search.searchPosts(principal(p.b()), "Library", null, null, 20);
    assertTrue(byLocation.items().stream().anyMatch(h -> h.id().equals(live.getId()) && "EVENT".equals(h.type())),
      "EVENT location must be searchable");
    var byOrganiser = search.searchPosts(principal(p.b()), "CSE Club", null, null, 20);
    assertTrue(byOrganiser.items().stream().anyMatch(h -> h.id().equals(live.getId())));
    var expiredSearch = search.searchPosts(principal(p.b()), "Old fest", null, null, 20);
    assertTrue(expiredSearch.items().stream().noneMatch(h -> h.id().equals(expired.getId())),
      "expired EVENT posts must not surface (liveEventsOnly)");
  }

  @Test void paginationCursorReturnsDisjointPages(){
    var p = people();
    factory.post(p.a(), "NATIONAL", "Pagetest alpha");
    factory.post(p.a(), "NATIONAL", "Pagetest beta");
    factory.post(p.a(), "NATIONAL", "Pagetest gamma");
    var page1 = search.searchPosts(principal(p.a()), "Pagetest", null, null, 2);
    assertEquals(2, page1.items().size());
    assertEquals(3, page1.total());
    assertNotNull(page1.nextCursor());
    var page2 = search.searchPosts(principal(p.a()), "Pagetest", null, page1.nextCursor(), 2);
    assertEquals(1, page2.items().size());
    assertNull(page2.nextCursor());
    assertTrue(page1.items().stream().noneMatch(h -> h.id().equals(page2.items().get(0).id())),
      "pages must be disjoint");
  }

  @Test void invalidTypeReturns400(){
    var p = people();
    var ex = assertThrows(Exceptions.BadRequest.class,
      () -> searchController().search(auth(p.a()), "x", "bogus", null, null, null, 20));
    assertEquals("INVALID_TYPE", ex.code());
  }

  @Test void universityDepartmentAndSkillSearch(){
    var p = people();
    profiles.addSkill(p.a().getId(), "React");
    var unis = search.searchUniversities("Metropolitan");
    assertTrue(unis.stream().anyMatch(u -> "Metropolitan University".equals(u.name())));
    var depts = search.searchDepartments("CSE");
    assertTrue(depts.stream().anyMatch(d -> d.id().equals(p.f().muCse().getId())));
    var bySkill = search.searchUsers("React", null, 20);
    assertTrue(bySkill.items().stream().anyMatch(h -> h.id().equals(p.a().getId())
      && h.matchedSkills().contains("React")));
    var res = searchController().search(auth(p.a()), "Metropolitan", "all", null, null, null, 20);
    assertTrue(res.containsKey("universities") && res.containsKey("departments"));
  }

  // ---- connections (D-200) ----

  @Test void requestCreatesPendingAndNotifiesAddressee(){
    var p = people();
    var dto = connections.requestConnection(principal(p.a()), p.b().getId(), "Hi, let's connect");
    assertEquals("PENDING", dto.status());
    List<Notification> notes = notifications.findByRecipientIdOrderByCreatedAtDesc(p.b().getId(),
      org.springframework.data.domain.PageRequest.of(0, 10));
    assertTrue(notes.stream().anyMatch(n -> "CONNECTION_REQUEST".equals(n.getType())
      && dto.id().equals(n.getTargetId()) && "CONNECTION_REQUEST".equals(n.getTargetType())),
      "request notification must be keyed on the request id");
  }

  @Test void duplicatePendingAndReversePendingConflict(){
    var p = people();
    connections.requestConnection(principal(p.a()), p.b().getId(), null);
    var dup = assertThrows(Exceptions.Conflict.class,
      () -> connections.requestConnection(principal(p.a()), p.b().getId(), null));
    assertEquals("REQUEST_PENDING", dup.code());
    var reverse = assertThrows(Exceptions.Conflict.class,
      () -> connections.requestConnection(principal(p.b()), p.a().getId(), null));
    assertEquals("REQUEST_PENDING", reverse.code());
  }

  @Test void selfRequestIsBadRequestAndUnknownUserIsNotFound(){
    var p = people();
    var self = assertThrows(Exceptions.BadRequest.class,
      () -> connections.requestConnection(principal(p.a()), p.a().getId(), null));
    assertEquals("CANNOT_CONNECT_SELF", self.code());
    assertThrows(Exceptions.NotFound.class,
      () -> connections.requestConnection(principal(p.a()), 999999L, null));
  }

  @Test void acceptByNonAddresseeIsNotFound(){
    var p = people();
    var dto = connections.requestConnection(principal(p.a()), p.b().getId(), null);
    assertThrows(Exceptions.NotFound.class, () -> connections.decide(principal(p.c()), dto.id(), true));
  }

  @Test void acceptConnectsBothSidesAndNotifiesRequester(){
    var p = people();
    var dto = connections.requestConnection(principal(p.a()), p.b().getId(), null);
    connections.decide(principal(p.b()), dto.id(), true);
    assertEquals("CONNECTED", connections.status(principal(p.a()), p.b().getId()).status());
    assertEquals("CONNECTED", connections.status(principal(p.b()), p.a().getId()).status());
    assertTrue(connections.connections(principal(p.a())).stream().anyMatch(c -> c.userId().equals(p.b().getId())));
    List<Notification> notes = notifications.findByRecipientIdOrderByCreatedAtDesc(p.a().getId(),
      org.springframework.data.domain.PageRequest.of(0, 10));
    assertTrue(notes.stream().anyMatch(n -> "CONNECTION_ACCEPTED".equals(n.getType()) && dto.id().equals(n.getTargetId())));
    var again = assertThrows(Exceptions.Conflict.class, () -> connections.decide(principal(p.b()), dto.id(), true));
    assertEquals("ALREADY_DECIDED", again.code());
  }

  @Test void rejectAllowsFreshRequest(){
    var p = people();
    var dto = connections.requestConnection(principal(p.a()), p.b().getId(), null);
    connections.decide(principal(p.b()), dto.id(), false);
    assertEquals("NONE", connections.status(principal(p.a()), p.b().getId()).status());
    List<Notification> notes = notifications.findByRecipientIdOrderByCreatedAtDesc(p.a().getId(),
      org.springframework.data.domain.PageRequest.of(0, 10));
    assertTrue(notes.stream().anyMatch(n -> "CONNECTION_REJECTED".equals(n.getType()) && dto.id().equals(n.getTargetId())));
    var fresh = connections.requestConnection(principal(p.a()), p.b().getId(), null);
    assertEquals("PENDING", fresh.status());
    assertNotEquals(dto.id(), fresh.id(), "a fresh request after REJECTED is a new row");
  }

  @Test void cancelEmptiesIncomingList(){
    var p = people();
    var dto = connections.requestConnection(principal(p.a()), p.b().getId(), null);
    assertEquals(1, connections.incoming(principal(p.b())).size());
    connections.cancel(principal(p.a()), dto.id());
    assertTrue(connections.incoming(principal(p.b())).isEmpty());
    assertEquals("NONE", connections.status(principal(p.a()), p.b().getId()).status());
    var again = assertThrows(Exceptions.Conflict.class, () -> connections.cancel(principal(p.a()), dto.id()));
    assertEquals("ALREADY_DECIDED", again.code());
  }

  @Test void removeHidesTheirPostsFromConnectionsFeed(){
    var p = people();
    connections.requestConnection(principal(p.a()), p.b().getId(), null);
    var pending = connections.incoming(principal(p.b())).get(0);
    connections.decide(principal(p.b()), pending.id(), true);
    Post post = factory.post(p.b(), "DEPARTMENT", "Connected post");
    assertTrue(feed.feed(principal(p.a()), "CONNECTIONS", null, 20, null, "recent").items().stream()
      .anyMatch(d -> d.id().equals(post.getId())));
    connections.remove(principal(p.a()), p.b().getId());
    assertEquals("NONE", connections.status(principal(p.a()), p.b().getId()).status());
    assertTrue(feed.feed(principal(p.a()), "CONNECTIONS", null, 20, null, "recent").items().stream()
      .noneMatch(d -> d.id().equals(post.getId())), "removal must instantly remove their posts");
  }

  @Test void unfollowDoesNotRemoveConnectionAndViceVersa(){
    var p = people();
    profiles.follow(p.a().getId(), p.b().getId());
    var dto = connections.requestConnection(principal(p.a()), p.b().getId(), null);
    connections.decide(principal(p.b()), dto.id(), true);
    profiles.unfollow(p.a().getId(), p.b().getId());
    assertEquals("CONNECTED", connections.status(principal(p.a()), p.b().getId()).status());
    assertFalse(follows.existsByFollowerIdAndFollowedId(p.a().getId(), p.b().getId()));
    profiles.follow(p.a().getId(), p.b().getId());
    connections.remove(principal(p.a()), p.b().getId());
    assertTrue(follows.existsByFollowerIdAndFollowedId(p.a().getId(), p.b().getId()),
      "removing a connection must not unfollow");
  }

  // ---- feed (D-200) ----

  @Test void connectionsLevelReturnsOnlyConnectedAndSelfWithinScope(){
    var p = people();
    // A connects to B (same university/department) and D (other university).
    connect(p.a(), p.b());
    connect(p.a(), p.d());
    Post bDept = factory.post(p.b(), "DEPARTMENT", "B dept post");
    Post cDept = factory.post(p.c(), "DEPARTMENT", "C dept post");
    Post dNational = factory.post(p.d(), "NATIONAL", "D national post");
    Post dDept = factory.post(p.d(), "DEPARTMENT", "D dept post");
    Post own = factory.post(p.a(), "DEPARTMENT", "Own dept post");
    var items = feed.feed(principal(p.a()), "CONNECTIONS", null, 20, null, "recent").items();
    assertTrue(items.stream().anyMatch(d -> d.id().equals(bDept.getId())), "connected same-scope author visible");
    assertTrue(items.stream().noneMatch(d -> d.id().equals(cDept.getId())), "unconnected author invisible");
    assertTrue(items.stream().anyMatch(d -> d.id().equals(dNational.getId())), "connected NATIONAL post visible");
    assertTrue(items.stream().noneMatch(d -> d.id().equals(dDept.getId())),
      "cross-university connection cannot leak a DEPARTMENT-scoped post");
    assertTrue(items.stream().anyMatch(d -> d.id().equals(own.getId())), "own posts visible");
  }

  @Test void unverifiedFeedIsNationalOnlyEvenForConnectionsLevel(){
    var p = people();
    factory.post(p.b(), "NATIONAL", "National for all");
    factory.post(p.b(), "DEPARTMENT", "Dept only");
    var items = feed.feed(principal(p.unverified()), "CONNECTIONS", null, 20, null, "recent").items();
    assertTrue(items.stream().anyMatch(d -> "National for all".equals(d.title())));
    assertTrue(items.stream().noneMatch(d -> "Dept only".equals(d.title())));
  }

  @Test void hiddenAutoPostAbsentFromFeedAfterFifthReport(){
    var p = people();
    Post post = factory.post(p.b(), "DEPARTMENT", "Reportable post");
    assertTrue(feed.feed(principal(p.a()), "DEPARTMENT", null, 20, null, "recent").items().stream()
      .anyMatch(d -> d.id().equals(post.getId())));
    // Five distinct verified reporters (D-069 default threshold).
    List<User> reporters = List.of(
      factory.verifiedStudent("sliced-r1@example.com", "SDR1", p.f().mu(), p.f().muCse(), p.f().muBatch()),
      factory.verifiedStudent("sliced-r2@example.com", "SDR2", p.f().mu(), p.f().muCse(), p.f().muBatch()),
      factory.verifiedStudent("sliced-r3@example.com", "SDR3", p.f().mu(), p.f().muCse(), p.f().muBatch()),
      factory.verifiedStudent("sliced-r4@example.com", "SDR4", p.f().mu(), p.f().muCse(), p.f().muBatch()),
      factory.verifiedStudent("sliced-r5@example.com", "SDR5", p.f().mu(), p.f().muCse(), p.f().muBatch()));
    for (User r : reporters) moderation.report(principal(r), "POST", post.getId(), "spam");
    assertEquals("HIDDEN_AUTO", postRepo.findById(post.getId()).orElseThrow().getStatus());
    assertTrue(feed.feed(principal(p.a()), "DEPARTMENT", null, 20, null, "recent").items().stream()
      .noneMatch(d -> d.id().equals(post.getId())), "HIDDEN_AUTO posts vanish from the feed");
  }

  @Test void topSortAffinityChangesAfterUnfollow(){
    var p = people();
    LocalDateTime now = LocalDateTime.now();
    // Ages chosen so affinity, not decay, decides: followed author at
    // 1h scores 31/(3^1.5)=5.97 vs the plain author's 16/(2.1^1.5)=5.26;
    // after unfollow the followed author drops to 16/5.20=3.08 and loses.
    Post olderFollowed = postAt(p.b(), "DEPARTMENT", "Older followed post", now.minusHours(1));
    Post newerPlain = postAt(p.c(), "DEPARTMENT", "Newer plain post", now.minusMinutes(6));
    profiles.follow(p.a().getId(), p.b().getId());
    var top = feed.feed(principal(p.a()), "DEPARTMENT", null, 20, null, "top").items();
    assertEquals(olderFollowed.getId(), top.get(0).id(),
      "followed author (+15) outranks a newer post with no affinity");
    profiles.unfollow(p.a().getId(), p.b().getId());
    var topAfter = feed.feed(principal(p.a()), "DEPARTMENT", null, 20, null, "top").items();
    assertEquals(newerPlain.getId(), topAfter.get(0).id(),
      "after unfollow the newer post ranks first");
  }

  private void connect(User a, User b){
    var dto = connections.requestConnection(principal(a), b.getId(), null);
    connections.decide(principal(b), dto.id(), true);
  }
}
