package com.unilink.workspace;
import com.unilink.common.Exceptions; import com.unilink.config.AppProperties;
import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import com.unilink.security.AesGcm;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.net.*; import java.net.http.*; import java.nio.charset.StandardCharsets;
import java.time.Duration; import java.util.*;

/** GitHub integration for the Developer Workspace.
 *  OAuth code exchange happens server-side; the access token is stored
 *  AES-GCM encrypted and never returned to the client. Repository reads
 *  are read-only and work for PUBLIC repositories with or without a
 *  connection (a connection token only raises rate limits and proves
 *  ownership for the Creator badge). When OAuth credentials are not
 *  configured the service reports that state — it never simulates a
 *  connection. */
@Service
public class GithubService {
  public record ConnectionStatus(boolean configured, boolean connected, String username, String message) {}
  public record RepoDto(String owner, String name, String fullName, String description, String language,
                        long stars, long forks, boolean isPrivate, String htmlUrl) {}
  public record ContentEntry(String name, String path, String type, Long size) {}
  public record FileContent(String path, String content, long size) {}
  public record CommitDto(String sha, String message, String authorName, String authorLogin, String createdAt) {}
  private final GithubConnectionRepository connections; private final AppProperties props;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final ObjectMapper json = new ObjectMapper();
  public GithubService(GithubConnectionRepository connections, AppProperties props){
    this.connections=connections; this.props=props;
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
      JsonNode me = apiGet("https://api.github.com/user", access);
      GithubConnection c = connections.findByUserId(userId).orElseGet(GithubConnection::new);
      c.setUserId(userId);
      c.setGithubUserId(me.path("id").asLong());
      c.setUsername(me.path("login").asText());
      c.setAccessTokenEnc(AesGcm.encrypt(props.encryptionKey(), access));
      c.setScopes(token.path("scope").asText("read:user"));
      return connections.save(c);
    } catch (Exceptions.BadRequest e){ throw e; }
      catch (Exception e){ throw new Exceptions.BadRequest("GITHUB_UNREACHABLE","Could not reach GitHub to complete sign-in"); }
  }
  @Transactional
  public void disconnect(Long userId){ connections.deleteByUserId(userId); }

  private String tokenFor(Long userId){
    if (userId==null) return null;
    return connections.findByUserId(userId).map(c -> AesGcm.decrypt(props.encryptionKey(), c.getAccessTokenEnc())).orElse(null);
  }
  private JsonNode apiGet(String url, String token){
    try {
      HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
        .header("Accept","application/vnd.github+json").header("User-Agent","UniLink")
        .timeout(Duration.ofSeconds(15)).GET();
      if (token!=null) b.header("Authorization","Bearer " + token);
      HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
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
    JsonNode arr = apiGet("https://api.github.com/user/repos?per_page=100&sort=updated", token);
    List<RepoDto> out = new ArrayList<>();
    for (JsonNode r : arr) if (!r.path("private").asBoolean(false)) out.add(toRepo(r));
    return out;
  }
  private RepoDto toRepo(JsonNode r){
    return new RepoDto(r.path("owner").path("login").asText(), r.path("name").asText(), r.path("full_name").asText(),
      r.path("description").isNull()?null:r.path("description").asText(), r.path("language").isNull()?null:r.path("language").asText(),
      r.path("stargazers_count").asLong(), r.path("forks_count").asLong(), r.path("private").asBoolean(false), r.path("html_url").asText());
  }
  @Transactional(readOnly=true)
  public RepoDto repo(Long viewerId, String owner, String repo){
    JsonNode r = apiGet("https://api.github.com/repos/" + owner + "/" + repo, tokenFor(viewerId));
    if (r.path("private").asBoolean(false)) throw new Exceptions.Forbidden("Only public repositories can be viewed in UniLink");
    return toRepo(r);
  }
  @Transactional(readOnly=true)
  public List<ContentEntry> contents(Long viewerId, String owner, String repo, String path){
    String url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents" + (path==null||path.isBlank() ? "" : "/" + path);
    JsonNode arr = apiGet(url, tokenFor(viewerId));
    List<ContentEntry> out = new ArrayList<>();
    for (JsonNode e : arr) out.add(new ContentEntry(e.path("name").asText(), e.path("path").asText(), e.path("type").asText(),
      e.path("size").isNumber()?e.path("size").asLong():null));
    out.sort((a,b) -> a.type().equals(b.type()) ? a.name().compareToIgnoreCase(b.name()) : (a.type().equals("dir") ? -1 : 1));
    return out;
  }
  @Transactional(readOnly=true)
  public FileContent file(Long viewerId, String owner, String repo, String path){
    JsonNode f = apiGet("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path, tokenFor(viewerId));
    String encoded = f.path("content").asText("").replaceAll("\\s", "");
    String text = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    return new FileContent(path, text, f.path("size").asLong());
  }
  @Transactional(readOnly=true)
  public String readme(Long viewerId, String owner, String repo){
    try {
      JsonNode f = apiGet("https://api.github.com/repos/" + owner + "/" + repo + "/readme", tokenFor(viewerId));
      String encoded = f.path("content").asText("").replaceAll("\\s", "");
      return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    } catch (Exceptions.AppException e){ return null; }
  }
  @Transactional(readOnly=true)
  public List<CommitDto> commits(Long viewerId, String owner, String repo){
    JsonNode arr = apiGet("https://api.github.com/repos/" + owner + "/" + repo + "/commits?per_page=20", tokenFor(viewerId));
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
}
