package com.unilink.workspace;
import com.unilink.common.Exceptions; import com.unilink.config.AppProperties;
import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import com.unilink.security.AesGcm;
import com.fasterxml.jackson.databind.*;
import org.slf4j.*; import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import java.net.*; import java.net.http.*; import java.nio.charset.StandardCharsets;
import java.time.Duration; import java.time.LocalDateTime; import java.util.*;

/** GitHub integration for the Developer Workspace.
 *  OAuth code exchange happens server-side; the access token is stored
 *  AES-GCM encrypted and never returned to the client. Repository reads
 *  are read-only and work for PUBLIC repositories with or without a
 *  connection (a connection token only raises rate limits and proves
 *  ownership for the Creator badge). When OAuth credentials are not
 *  configured the service reports that state — it never simulates a
 *  connection.
 *  D-203 token lifecycle: a GitHub 401 clears the stale connection and
 *  surfaces GITHUB_TOKEN_INVALID (instead of the old generic
 *  GITHUB_API_ERROR); disconnect revokes the token at GitHub before
 *  deleting the local row; the OAuth state carries a 10-minute expiry and
 *  a one-time nonce row, so a replayed or expired callback is rejected. */
@Service
public class GithubService {
  private static final Logger log = LoggerFactory.getLogger(GithubService.class);
  /** D-203: OAuth states live 10 minutes; a callback after that is rejected. */
  public static final Duration STATE_TTL = Duration.ofMinutes(10);
  public record ConnectionStatus(boolean configured, boolean connected, String username, String message) {}
  public record RepoDto(String owner, String name, String fullName, String description, String language,
                        long stars, long forks, boolean isPrivate, String htmlUrl, String defaultBranch) {}
  public record ContentEntry(String name, String path, String type, Long size) {}
  public record FileContent(String path, String content, long size) {}
  public record CommitDto(String sha, String message, String authorName, String authorLogin, String createdAt) {}
  private final GithubConnectionRepository connections;
  private final GithubOAuthStateRepository states;
  private final AppProperties props;
  private final HttpClient http;
  private final TransactionTemplate tx;
  private final ObjectMapper json = new ObjectMapper();
  public GithubService(GithubConnectionRepository connections, GithubOAuthStateRepository states,
      AppProperties props, HttpClient http, TransactionTemplate tx){
    this.connections=connections; this.states=states; this.props=props; this.http=http; this.tx=tx;
  }
  public boolean configured(){ return props.github()!=null && props.github().configured(); }

  @Transactional(readOnly=true)
  public ConnectionStatus status(Long userId){
    if (!configured()) return new ConnectionStatus(false, false, null,
      "GitHub integration is not configured on this server. Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET to enable it.");
    return connections.findByUserId(userId).map(c -> new ConnectionStatus(true, true, c.getUsername(), null))
      .orElseGet(() -> new ConnectionStatus(true, false, null, null));
  }
  public String authorizeUrl(Long userId, String state){
    if (!configured()) throw new Exceptions.BadRequest("GITHUB_NOT_CONFIGURED",
      "GitHub integration is not configured on this server. Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET to enable it.");
    return "https://github.com/login/oauth/authorize?client_id=" + enc(props.github().clientId())
      + "&redirect_uri=" + enc(props.github().redirectUri())
      + "&scope=" + enc("read:user") + "&state=" + enc(state);
  }
  /** D-203: mint a one-time OAuth state. The nonce row (10-minute expiry)
   *  is what makes the state single-use — consumeState marks it used, so
   *  a replayed callback finds no usable row. The HMAC still binds the
   *  payload to this server's ENCRYPTION_KEY. */
  @Transactional
  public String newState(Long userId){
    byte[] raw = new byte[16]; new java.security.SecureRandom().nextBytes(raw);
    String nonce = HexFormat.of().formatHex(raw);
    String issuedAt = String.valueOf(java.time.Instant.now().getEpochSecond());
    GithubOAuthState row = new GithubOAuthState();
    row.setNonce(nonce); row.setUserId(userId);
    row.setExpiresAt(LocalDateTime.now().plus(STATE_TTL));
    states.save(row);
    String payload = userId + "." + nonce + "." + issuedAt;
    return payload + "." + hmac(payload);
  }
  /** D-203: verify a returned state — HMAC, 10-minute expiry, and the
   *  one-time nonce row. Any failure is INVALID_STATE; the caller
   *  redirects to ?github=error instead of surfacing a raw 400. */
  @Transactional
  public Long consumeState(String state){
    try {
      String[] parts = state==null ? new String[0] : state.split("\\.");
      if (parts.length!=4 || !hmac(parts[0]+"."+parts[1]+"."+parts[2]).equals(parts[3]))
        throw new Exceptions.BadRequest("INVALID_STATE","GitHub sign-in state is invalid");
      long issuedAt = Long.parseLong(parts[2]);
      long now = java.time.Instant.now().getEpochSecond();
      if (issuedAt > now + 60 || now - issuedAt > STATE_TTL.getSeconds())
        throw new Exceptions.BadRequest("INVALID_STATE","GitHub sign-in state has expired");
      Long userId = Long.parseLong(parts[0]);
      String nonce = parts[1];
      GithubOAuthState row = states.findByNonce(nonce)
        .orElseThrow(() -> new Exceptions.BadRequest("INVALID_STATE","GitHub sign-in state is unknown or already used"));
      if (!userId.equals(row.getUserId()) || row.getUsedAt()!=null || row.getExpiresAt().isBefore(LocalDateTime.now()))
        throw new Exceptions.BadRequest("INVALID_STATE","GitHub sign-in state is unknown or already used");
      row.setUsedAt(LocalDateTime.now());
      states.save(row);
      states.deleteByExpiresAtBefore(LocalDateTime.now());
      return userId;
    } catch (NumberFormatException e){ throw new Exceptions.BadRequest("INVALID_STATE","GitHub sign-in state is invalid"); }
  }
  @Transactional
  public GithubConnection exchangeCode(Long userId, String code){
    if (!configured()) throw new Exceptions.BadRequest("GITHUB_NOT_CONFIGURED","GitHub integration is not configured");
    try {
      String form = "client_id=" + enc(props.github().clientId()) + "&client_secret=" + enc(props.github().clientSecret())
        + "&code=" + enc(code) + "&redirect_uri=" + enc(props.github().redirectUri());
      HttpRequest req = HttpRequest.newBuilder(URI.create("https://github.com/login/oauth/access_token"))
        .header("Accept","application/json").header("Content-Type","application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form)).timeout(Duration.ofSeconds(15)).build();
      JsonNode token = json.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
      String access = token.path("access_token").asText(null);
      if (access==null) throw new Exceptions.BadRequest("GITHUB_AUTH_FAILED","GitHub did not return an access token");
      JsonNode me = apiGet("https://api.github.com/user", access, userId);
      GithubConnection c = connections.findByUserId(userId).orElseGet(GithubConnection::new);
      c.setUserId(userId);
      c.setGithubUserId(me.path("id").asLong());
      c.setUsername(me.path("login").asText());
      c.setAccessTokenEnc(AesGcm.encrypt(props.encryptionKey(), access));
      c.setScopes(token.path("scope").asText("read:user"));
      return connections.save(c);
    } catch (Exceptions.AppException e){ throw e; }
      catch (Exception e){ throw new Exceptions.BadRequest("GITHUB_UNREACHABLE","Could not reach GitHub to complete sign-in"); }
  }
  /** D-203: disconnect revokes the token at GitHub first (DELETE
   *  /applications/{client_id}/token, Basic auth with the OAuth App
   *  credentials), then deletes the local row. Revocation is best-effort:
   *  if GitHub cannot be reached or refuses, the local row is still
   *  deleted so the student is never stranded connected, and the failure
   *  is logged — a stale token at GitHub then expires with the user, and
   *  any later 401 clears it via the token-invalid path. */
  @Transactional
  public void disconnect(Long userId){
    GithubConnection c = connections.findByUserId(userId).orElse(null);
    if (c!=null && configured()){
      try {
        String token = AesGcm.decrypt(props.encryptionKey(), c.getAccessTokenEnc());
        revokeAtGithub(token);
      } catch (Exception e){
        log.warn("GitHub token revocation failed for user {}; deleting the local connection anyway", userId, e);
      }
    }
    connections.deleteByUserId(userId);
  }
  private void revokeAtGithub(String accessToken) throws Exception {
    String basic = Base64.getEncoder().encodeToString(
      (props.github().clientId() + ":" + props.github().clientSecret()).getBytes(StandardCharsets.UTF_8));
    String body = json.writeValueAsString(Map.of("access_token", accessToken));
    HttpRequest req = HttpRequest.newBuilder(
        URI.create("https://api.github.com/applications/" + enc(props.github().clientId()) + "/token"))
      .header("Accept","application/vnd.github+json").header("User-Agent","UniLink")
      .header("Authorization","Basic " + basic)
      .method("DELETE", HttpRequest.BodyPublishers.ofString(body)).timeout(Duration.ofSeconds(15)).build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode()!=204 && res.statusCode()!=200)
      throw new IllegalStateException("GitHub token revocation returned " + res.statusCode());
  }

  /** D-203: the stored token is unreadable (corrupt ciphertext or a key
   *  problem) — treat it exactly like a revoked token: clear the stale
   *  connection and tell the client to reconnect, never a 500. */
  private String tokenFor(Long userId){
    if (userId==null) return null;
    return connections.findByUserId(userId).map(c -> {
      try { return AesGcm.decrypt(props.encryptionKey(), c.getAccessTokenEnc()); }
      catch (RuntimeException e){
        clearStaleConnection(userId);
        throw new Exceptions.AppException(HttpStatus.UNAUTHORIZED, "GITHUB_TOKEN_INVALID",
          "Your GitHub connection is no longer valid. Please connect GitHub again.");
      }
    }).orElse(null);
  }
  /** The 401 path runs inside @Transactional(readOnly=true) readers, so
   *  the delete needs its own read-write transaction. */
  private void clearStaleConnection(Long userId){
    // A fresh template per call: mutating the shared bean's propagation
    // behaviour would not be thread-safe.
    TransactionTemplate requiresNew = new TransactionTemplate(tx.getTransactionManager());
    requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    requiresNew.executeWithoutResult(s -> connections.deleteByUserId(userId));
  }
  private Exceptions.AppException tokenInvalid(){
    return new Exceptions.AppException(HttpStatus.UNAUTHORIZED, "GITHUB_TOKEN_INVALID",
      "Your GitHub connection is no longer valid. Please connect GitHub again.");
  }
  private JsonNode apiGet(String url, String token, Long userId){
    try {
      HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
        .header("Accept","application/vnd.github+json").header("User-Agent","UniLink")
        .timeout(Duration.ofSeconds(15)).GET();
      if (token!=null) b.header("Authorization","Bearer " + token);
      HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
      if (res.statusCode()==401){
        // D-203: a revoked/expired token clears the stale connection
        // instead of lingering as "connected" behind generic errors.
        if (userId!=null) clearStaleConnection(userId);
        throw tokenInvalid();
      }
      if (res.statusCode()==404) throw new Exceptions.NotFound("GitHub resource");
      if (res.statusCode()>=400) throw new Exceptions.BadRequest("GITHUB_API_ERROR","GitHub API returned " + res.statusCode());
      return json.readTree(res.body());
    } catch (Exceptions.AppException e){ throw e; }
      catch (Exception e){ throw new Exceptions.BadRequest("GITHUB_UNREACHABLE","Could not reach GitHub"); }
  }
  @Transactional(readOnly=true)
  public List<RepoDto> myRepos(Long userId){
    String token = tokenFor(userId);
    if (token==null) throw new Exceptions.BadRequest("GITHUB_NOT_CONNECTED","Connect GitHub first");
    JsonNode arr = apiGet("https://api.github.com/user/repos?per_page=100&sort=updated", token, userId);
    List<RepoDto> out = new ArrayList<>();
    for (JsonNode r : arr) if (!r.path("private").asBoolean(false)) out.add(toRepo(r));
    return out;
  }
  private RepoDto toRepo(JsonNode r){
    return new RepoDto(r.path("owner").path("login").asText(), r.path("name").asText(), r.path("full_name").asText(),
      r.path("description").isNull()?null:r.path("description").asText(), r.path("language").isNull()?null:r.path("language").asText(),
      r.path("stargazers_count").asLong(), r.path("forks_count").asLong(), r.path("private").asBoolean(false), r.path("html_url").asText(),
      r.path("default_branch").isNull()?null:r.path("default_branch").asText());
  }
  @Transactional(readOnly=true)
  public RepoDto repo(Long viewerId, String owner, String repo){
    JsonNode r = apiGet("https://api.github.com/repos/" + owner + "/" + repo, tokenFor(viewerId), viewerId);
    if (r.path("private").asBoolean(false)) throw new Exceptions.Forbidden("Only public repositories can be viewed in UniLink");
    return toRepo(r);
  }
  @Transactional(readOnly=true)
  public List<ContentEntry> contents(Long viewerId, String owner, String repo, String path){
    String url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents" + (path==null||path.isBlank() ? "" : "/" + path);
    JsonNode arr = apiGet(url, tokenFor(viewerId), viewerId);
    List<ContentEntry> out = new ArrayList<>();
    for (JsonNode e : arr) out.add(new ContentEntry(e.path("name").asText(), e.path("path").asText(), e.path("type").asText(),
      e.path("size").isNumber()?e.path("size").asLong():null));
    out.sort((a,b) -> a.type().equals(b.type()) ? a.name().compareToIgnoreCase(b.name()) : (a.type().equals("dir") ? -1 : 1));
    return out;
  }
  @Transactional(readOnly=true)
  public FileContent file(Long viewerId, String owner, String repo, String path){
    JsonNode f = apiGet("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path, tokenFor(viewerId), viewerId);
    String encoded = f.path("content").asText("").replaceAll("\\s", "");
    String text = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    return new FileContent(path, text, f.path("size").asLong());
  }
  @Transactional(readOnly=true)
  public String readme(Long viewerId, String owner, String repo){
    try {
      JsonNode f = apiGet("https://api.github.com/repos/" + owner + "/" + repo + "/readme", tokenFor(viewerId), viewerId);
      String encoded = f.path("content").asText("").replaceAll("\\s", "");
      return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    } catch (Exceptions.AppException e){ return null; }
  }
  @Transactional(readOnly=true)
  public List<CommitDto> commits(Long viewerId, String owner, String repo){
    JsonNode arr = apiGet("https://api.github.com/repos/" + owner + "/" + repo + "/commits?per_page=20", tokenFor(viewerId), viewerId);
    List<CommitDto> out = new ArrayList<>();
    for (JsonNode c : arr){
      JsonNode commit = c.path("commit");
      out.add(new CommitDto(c.path("sha").asText().substring(0,7),
        commit.path("message").asText().lines().findFirst().orElse(""),
        commit.path("author").path("name").asText(),
        c.path("author").isNull()?null:c.path("author").path("login").asText(),
        commit.path("author").path("date").asText()));
    }
    return out;
  }
  /** The viewer's own GitHub username, when connected (Creator checks). */
  @Transactional(readOnly=true)
  public String myUsername(Long userId){
    return connections.findByUserId(userId).map(GithubConnection::getUsername).orElse(null);
  }
  private static String enc(String v){ return URLEncoder.encode(v==null?"":v, StandardCharsets.UTF_8); }
  private String hmac(String payload){
    try {
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(props.encryptionKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e){ throw new IllegalStateException(e); }
  }
}
