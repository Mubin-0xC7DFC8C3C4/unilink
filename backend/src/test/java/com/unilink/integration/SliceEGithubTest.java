package com.unilink.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unilink.common.Exceptions;
import com.unilink.config.AppProperties;
import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*;
import com.unilink.profile.ProfileService;
import com.unilink.security.AccessPolicy;
import com.unilink.security.AesGcm;
import com.unilink.security.Principals;
import com.unilink.workspace.GithubService;
import com.unilink.workspace.api.WorkspaceController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Slice E (D-203): GitHub Pin to Profile + token lifecycle.
 *  Service-level tests on H2, same setup as SliceDTest. The GitHub
 *  transport is a mocked HttpClient bean (@Primary, so the Spring-built
 *  GithubService talks to the fake, never the real api.github.com):
 *  each test routes requests to canned responses. */
@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-slicee-gh;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage",
  "app.github.client-id=test-client-id",
  "app.github.client-secret=test-client-secret"
})
@Transactional
class SliceEGithubTest {
  @TestConfiguration
  static class GithubMockConfig {
    @Bean @Primary HttpClient httpClient(){ return mock(HttpClient.class); }
  }

  @Autowired TestDataFactory factory;
  @Autowired ProfileService profiles;
  @Autowired GithubService github;
  @Autowired AppProperties props;
  @Autowired HttpClient httpMock;
  @Autowired UserRepository users;
  @Autowired ProjectRepository projectRepo;
  @Autowired GithubConnectionRepository connections;
  @Autowired GithubOAuthStateRepository states;
  @Autowired JdbcTemplate jdbc;

  private Function<HttpRequest, HttpResponse<String>> route = this::happyPath;

  @BeforeEach void createH2OnlyTables(){
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION (PRIMARY_ID CHAR(36) PRIMARY KEY, SESSION_ID CHAR(36), CREATION_TIME BIGINT, LAST_ACCESS_TIME BIGINT, MAX_INACTIVE_INTERVAL INT, EXPIRY_TIME TIMESTAMP, PRINCIPAL_NAME VARCHAR(100))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID CHAR(36), ATTRIBUTE_NAME VARCHAR(200), ATTRIBUTE_BYTES BLOB, PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME))");
  }
  @BeforeEach void stubHttp() throws Exception {
    reset(httpMock);
    doAnswer(inv -> route.apply(inv.getArgument(0))).when(httpMock)
      .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  private HttpResponse<String> fake(int status, String body){
    HttpResponse<String> r = mock(HttpResponse.class);
    when(r.statusCode()).thenReturn(status);
    when(r.body()).thenReturn(body);
    return r;
  }
  private static final String REPOS_JSON = """
    [{"owner":{"login":"testdev"},"name":"demo","full_name":"testdev/demo","description":"Demo project","language":"TypeScript","stargazers_count":12,"forks_count":3,"private":false,"html_url":"https://github.com/testdev/demo","default_branch":"develop"},
     {"owner":{"login":"testdev"},"name":"notes","full_name":"testdev/notes","description":null,"language":null,"stargazers_count":0,"forks_count":0,"private":false,"html_url":"https://github.com/testdev/notes","default_branch":"main"},
     {"owner":{"login":"testdev"},"name":"secret","full_name":"testdev/secret","description":"private","language":"Java","stargazers_count":0,"forks_count":0,"private":true,"html_url":"https://github.com/testdev/secret","default_branch":"main"}]""";
  private static final String DEMO_JSON = """
    {"owner":{"login":"testdev"},"name":"demo","full_name":"testdev/demo","description":"Demo project","language":"TypeScript","stargazers_count":12,"forks_count":3,"private":false,"html_url":"https://github.com/testdev/demo","default_branch":"develop"}""";
  private HttpResponse<String> happyPath(HttpRequest req){
    String uri = req.uri().toString();
    String method = req.method();
    if (uri.contains("/applications/") && "DELETE".equals(method)) return fake(204, "");
    if (uri.contains("/user/repos")) return fake(200, REPOS_JSON);
    if (uri.endsWith("/user")) return fake(200, "{\"id\":4242,\"login\":\"testdev\"}");
    if (uri.contains("/repos/testdev/demo")) return fake(200, DEMO_JSON);
    return fake(404, "{}");
  }

  private User student(String tag){
    var f = factory.seedMaster();
    return factory.verifiedStudent("slicee-" + tag + "-" + System.nanoTime() + "@example.com",
      "SLE-" + tag + "-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
  }
  private void connect(User u){
    GithubConnection c = new GithubConnection();
    c.setUserId(u.getId()); c.setGithubUserId(4242L); c.setUsername("testdev");
    c.setAccessTokenEnc(AesGcm.encrypt(props.encryptionKey(), "gh-token-123"));
    c.setScopes("read:user");
    connections.save(c);
  }
  private static Principals.UnilinkUserPrincipal principal(User u){
    return new Principals.UnilinkUserPrincipal(u.getId(), u.getEmail(), "x", u.getUniversityId(),
      u.getDepartmentId(), null, "VERIFIED", true);
  }
  private static Authentication auth(User u){ return new UsernamePasswordAuthenticationToken(principal(u), null); }

  // ---- Pin to Profile (D-203) ----

  @Test void pinLinksProjectToOwnPublicRepo(){
    User u = student("pin"); connect(u);
    var project = profiles.addProject(u.getId(), "Demo app", "My demo", "https://example.com");
    var pinned = profiles.pinProject(u.getId(), project.id(), "testdev", "demo");
    assertEquals("testdev", pinned.githubOwner());
    assertEquals("demo", pinned.githubRepo());
    assertEquals("testdev", profiles.listProjects(u.getId()).get(0).githubOwner());
  }

  @Test void pinToRepoNotInPublicListIsRejected(){
    User u = student("pin-nope"); connect(u);
    var project = profiles.addProject(u.getId(), "Demo app", "My demo", null);
    var ex = assertThrows(Exceptions.BadRequest.class,
      () -> profiles.pinProject(u.getId(), project.id(), "testdev", "secret"));
    assertEquals("REPO_NOT_OWNED", ex.code());
    ex = assertThrows(Exceptions.BadRequest.class,
      () -> profiles.pinProject(u.getId(), project.id(), "someone-else", "demo"));
    assertEquals("REPO_NOT_OWNED", ex.code());
    assertNull(profiles.listProjects(u.getId()).get(0).githubRepo(), "a rejected pin must not persist");
  }

  @Test void pinByNonOwnerIs404AndPinWithoutConnectionIsNotConnected(){
    User owner = student("pin-owner"); connect(owner);
    User other = student("pin-other");
    var project = profiles.addProject(owner.getId(), "Demo app", "My demo", null);
    assertThrows(Exceptions.NotFound.class,
      () -> profiles.pinProject(other.getId(), project.id(), "testdev", "demo"));
    var otherProject = profiles.addProject(other.getId(), "Other app", null, null);
    var ex = assertThrows(Exceptions.BadRequest.class,
      () -> profiles.pinProject(other.getId(), otherProject.id(), "testdev", "demo"));
    assertEquals("GITHUB_NOT_CONNECTED", ex.code());
  }

  @Test void unpinClearsTheLinkButKeepsTheProject(){
    User u = student("unpin"); connect(u);
    var project = profiles.addProject(u.getId(), "Demo app", "My demo", null);
    profiles.pinProject(u.getId(), project.id(), "testdev", "demo");
    var cleared = profiles.unpinProject(u.getId(), project.id());
    assertNull(cleared.githubOwner());
    assertNull(cleared.githubRepo());
    assertEquals(1, profiles.listProjects(u.getId()).size());
  }

  @Test void repoDtoCarriesDefaultBranch(){
    User u = student("branch"); connect(u);
    var repo = github.repo(u.getId(), "testdev", "demo");
    assertEquals("develop", repo.defaultBranch(), "the viewer must show the repo's real default branch, not 'main'");
  }

  // ---- OAuth state: expiry + single-use (D-203) ----

  @Test void stateIsSingleUse(){
    User u = student("state");
    String state = github.newState(u.getId());
    assertEquals(u.getId(), github.consumeState(state));
    var ex = assertThrows(Exceptions.BadRequest.class, () -> github.consumeState(state));
    assertEquals("INVALID_STATE", ex.code(), "a replayed state must be rejected");
  }

  @Test void unknownNonceIsRejected(){
    User u = student("state-unknown");
    // A well-formed, fresh, correctly HMAC-signed state whose nonce was
    // never persisted (HMAC computed with the test encryption key).
    String payload = u.getId() + ".deadbeefdeadbeefdeadbeefdeadbeef." + java.time.Instant.now().getEpochSecond();
    String state = payload + "." + hmac(payload, props.encryptionKey());
    var ex = assertThrows(Exceptions.BadRequest.class, () -> github.consumeState(state));
    assertEquals("INVALID_STATE", ex.code());
  }

  @Test void expiredStateIsRejected(){
    User u = student("state-expired");
    long issuedAt = java.time.Instant.now().getEpochSecond() - GithubService.STATE_TTL.getSeconds() - 60;
    String payload = u.getId() + ".deadbeefdeadbeefdeadbeefdeadbeef." + issuedAt;
    String state = payload + "." + hmac(payload, props.encryptionKey());
    var ex = assertThrows(Exceptions.BadRequest.class, () -> github.consumeState(state));
    assertEquals("INVALID_STATE", ex.code());
  }

  private static String hmac(String payload, String key){
    try {
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e){ throw new IllegalStateException(e); }
  }

  // ---- disconnect revokes at GitHub (D-203) ----

  @Test void disconnectRevokesTokenAtGithubThenDeletesLocalRow() throws Exception {
    User u = student("disc"); connect(u);
    github.disconnect(u.getId());
    assertTrue(connections.findByUserId(u.getId()).isEmpty());
    verify(httpMock, times(1)).send(
      argThat((HttpRequest req) -> "DELETE".equals(req.method()) && req.uri().toString().contains("/applications/")),
      any(HttpResponse.BodyHandler.class));
  }

  @Test void disconnectStillDeletesLocalRowWhenGithubIsUnreachable() throws Exception {
    User u = student("disc-fail"); connect(u);
    route = req -> { throw new RuntimeException(new java.io.IOException("github down")); };
    github.disconnect(u.getId());
    assertTrue(connections.findByUserId(u.getId()).isEmpty(),
      "a failed revocation must not strand the student connected");
  }

  // ---- callback: access_denied redirects, never a raw 400 (D-203) ----

  @Test void callbackWithAccessDeniedRedirectsToError() throws Exception {
    var controller = new WorkspaceController(github, new AccessPolicy(), props, mock(com.unilink.workspace.DiscussionService.class));
    var res = mock(jakarta.servlet.http.HttpServletResponse.class);
    controller.callback(null, null, "access_denied", res);
    verify(res).sendRedirect("http://localhost:5173/workspace?github=error");
    var res2 = mock(jakarta.servlet.http.HttpServletResponse.class);
    controller.callback(null, null, null, res2);
    verify(res2).sendRedirect("http://localhost:5173/workspace?github=error");
  }

  // ---- RepoDto JSON shape (pin endpoint contract) ----

  @Test void pinnedProjectDtoSerialisesGithubFields() throws Exception {
    User u = student("json"); connect(u);
    var project = profiles.addProject(u.getId(), "Demo app", "My demo", null);
    var pinned = profiles.pinProject(u.getId(), project.id(), "testdev", "demo");
    String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(pinned);
    assertTrue(json.contains("\"githubOwner\":\"testdev\""), json);
    assertTrue(json.contains("\"githubRepo\":\"demo\""), json);
  }
}
