package com.unilink.integration;

import com.unilink.common.Exceptions;
import com.unilink.config.AppProperties;
import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*;
import com.unilink.security.AesGcm;
import com.unilink.workspace.GithubService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Slice E (D-203): a GitHub 401 clears the stale connection and surfaces
 *  GITHUB_TOKEN_INVALID. Deliberately NOT @Transactional: the 401 path
 *  deletes the connection in a REQUIRES_NEW transaction, which can only
 *  see (and delete) a COMMITTED row — inside a test transaction the row
 *  would be invisible to it. Rows created here are deleted in @AfterEach. */
@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-slicee-401;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
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
class GithubTokenInvalidTest {
  @TestConfiguration
  static class GithubMockConfig {
    @Bean @Primary HttpClient httpClient(){ return mock(HttpClient.class); }
  }

  @Autowired TestDataFactory factory;
  @Autowired GithubService github;
  @Autowired AppProperties props;
  @Autowired HttpClient httpMock;
  @Autowired GithubConnectionRepository connections;
  @Autowired JdbcTemplate jdbc;

  private final List<Long> userIds = new ArrayList<>();
  private Function<HttpRequest, HttpResponse<String>> route = req -> fake(200, "[]");

  @BeforeEach void stubHttp() throws Exception {
    reset(httpMock);
    doAnswer(inv -> route.apply(inv.getArgument(0))).when(httpMock)
      .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }
  @AfterEach void cleanup(){
    for (Long id : userIds){
      jdbc.update("DELETE FROM github_oauth_states WHERE user_id=?", id);
      jdbc.update("DELETE FROM github_connections WHERE user_id=?", id);
      jdbc.update("DELETE FROM verification_requests WHERE user_id=?", id);
      jdbc.update("DELETE FROM user_credentials WHERE user_id=?", id);
      jdbc.update("DELETE FROM profiles WHERE user_id=?", id);
      jdbc.update("DELETE FROM users WHERE id=?", id);
    }
    userIds.clear();
  }

  private HttpResponse<String> fake(int status, String body){
    HttpResponse<String> r = mock(HttpResponse.class);
    when(r.statusCode()).thenReturn(status);
    when(r.body()).thenReturn(body);
    return r;
  }
  private User connectedStudent(String tag){
    var f = factory.seedMaster();
    User u = factory.verifiedStudent("slicee401-" + tag + "-" + System.nanoTime() + "@example.com",
      "SL401-" + tag + "-" + System.nanoTime(), f.mu(), f.muCse(), f.muBatch());
    userIds.add(u.getId());
    GithubConnection c = new GithubConnection();
    c.setUserId(u.getId()); c.setGithubUserId(4242L); c.setUsername("testdev");
    c.setAccessTokenEnc(AesGcm.encrypt(props.encryptionKey(), "gh-token-123"));
    c.setScopes("read:user");
    connections.save(c);
    return u;
  }

  @Test void github401ClearsConnectionAndSurfacesTokenInvalid(){
    User u = connectedStudent("401");
    route = req -> fake(401, "{\"message\":\"Bad credentials\"}");
    var ex = assertThrows(Exceptions.AppException.class, () -> github.myRepos(u.getId()));
    assertEquals("GITHUB_TOKEN_INVALID", ex.code());
    assertEquals(401, ex.status().value());
    assertTrue(connections.findByUserId(u.getId()).isEmpty(),
      "the stale connection must be cleared so status stops reporting connected:true");
    assertFalse(github.status(u.getId()).connected());
  }

  @Test void non401ErrorsKeepTheConnection(){
    User u = connectedStudent("500");
    route = req -> fake(500, "{}");
    var ex = assertThrows(Exceptions.BadRequest.class, () -> github.myRepos(u.getId()));
    assertEquals("GITHUB_API_ERROR", ex.code());
    assertTrue(connections.findByUserId(u.getId()).isPresent(),
      "only a 401 means the token is dead — a 500 must not disconnect the student");
  }

  @Test void undecryptableTokenIsTreatedAsInvalid(){
    User u = connectedStudent("corrupt");
    GithubConnection c = connections.findByUserId(u.getId()).orElseThrow();
    c.setAccessTokenEnc("not-base64-and-not-a-ciphertext");
    connections.save(c);
    var ex = assertThrows(Exceptions.AppException.class, () -> github.myRepos(u.getId()));
    assertEquals("GITHUB_TOKEN_INVALID", ex.code());
    assertTrue(connections.findByUserId(u.getId()).isEmpty());
  }
}
