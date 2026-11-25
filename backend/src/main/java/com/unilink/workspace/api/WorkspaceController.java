package com.unilink.workspace.api;
import com.unilink.common.Exceptions; import com.unilink.config.AppProperties;
import com.unilink.security.AccessPolicy;
import com.unilink.workspace.DiscussionService;
import com.unilink.workspace.GithubService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

/** Developer Workspace APIs: GitHub OAuth connection, read-only public
 *  repository browsing, and UniLink-native project discussions.
 *  Controllers depend on services and DTOs only (architecture rules);
 *  all persistence lives in GithubService / DiscussionService. */
@RestController @RequestMapping("/api/workspace")
public class WorkspaceController {
  private final GithubService github; private final AccessPolicy policy; private final AppProperties props;
  private final DiscussionService discussionService;
  public WorkspaceController(GithubService github, AccessPolicy policy, AppProperties props,
      DiscussionService discussionService){
    this.github=github; this.policy=policy; this.props=props; this.discussionService=discussionService;
  }

  @GetMapping("/github/status")
  public GithubService.ConnectionStatus status(Authentication a){
    var p = policy.requireStudent(a.getPrincipal());
    return github.status(p.userId());
  }
  /** Returns the GitHub authorize URL for the signed-in student. The
   *  state is minted by GithubService (D-203: HMAC-signed, 10-minute
   *  expiry, one-time nonce row) — the controller holds no state logic. */
  @GetMapping("/github/authorize")
  public Map<String,String> authorize(Authentication a){
    var p = policy.requireStudent(a.getPrincipal());
    return Map.of("url", github.authorizeUrl(p.userId(), github.newState(p.userId())));
  }
  /** OAuth callback (browser redirect from GitHub). Exchanges the code
   *  server-side, stores the encrypted token, and returns the browser
   *  to the Workspace page. The state is HMAC-signed; the session is not
   *  required here because GitHub's redirect may not carry it in
   *  cross-site form posts — the signed state binds the user id.
   *  D-203: every failure path redirects to ?github=error — cancelling
   *  on GitHub (?error=access_denied, no code) used to hit a raw Spring
   *  400 because code/state were required parameters; they are optional
   *  now and a missing or denied authorisation is a redirect, never a
   *  stack trace page. */
  @GetMapping("/github/callback")
  public void callback(@RequestParam(required=false) String code, @RequestParam(required=false) String state,
                       @RequestParam(required=false) String error, HttpServletResponse res) throws IOException {
    String frontend = props.github()!=null && props.github().frontendUrl()!=null ? props.github().frontendUrl() : "http://localhost:5173";
    try {
      if (error!=null || code==null || state==null) throw new Exceptions.BadRequest("GITHUB_AUTH_FAILED","GitHub authorization did not complete");
      Long userId = github.consumeState(state);
      github.exchangeCode(userId, code);
      res.sendRedirect(frontend + "/workspace?github=connected");
    } catch (Exception e){
      res.sendRedirect(frontend + "/workspace?github=error");
    }
  }
  @DeleteMapping("/github/connection")
  public Map<String,String> disconnect(Authentication a){
    var p = policy.requireStudent(a.getPrincipal());
    github.disconnect(p.userId());
    return Map.of("status","ok");
  }
  @GetMapping("/github/repos")
  public List<GithubService.RepoDto> myRepos(Authentication a){
    var p = policy.requireStudent(a.getPrincipal());
    return github.myRepos(p.userId());
  }
  @GetMapping("/github/repos/{owner}/{repo}")
  public Map<String,Object> repo(Authentication a, @PathVariable String owner, @PathVariable String repo){
    var p = policy.requireStudent(a.getPrincipal());
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("repo", github.repo(p.userId(), owner, repo));
    out.put("readme", github.readme(p.userId(), owner, repo));
    out.put("myUsername", github.myUsername(p.userId()));
    return out;
  }
  @GetMapping("/github/repos/{owner}/{repo}/contents")
  public List<GithubService.ContentEntry> contents(Authentication a, @PathVariable String owner, @PathVariable String repo,
      @RequestParam(defaultValue="") String path){
    var p = policy.requireStudent(a.getPrincipal());
    return github.contents(p.userId(), owner, repo, path);
  }
  @GetMapping("/github/repos/{owner}/{repo}/file")
  public GithubService.FileContent file(Authentication a, @PathVariable String owner, @PathVariable String repo,
      @RequestParam String path){
    var p = policy.requireStudent(a.getPrincipal());
    return github.file(p.userId(), owner, repo, path);
  }
  @GetMapping("/github/repos/{owner}/{repo}/commits")
  public List<GithubService.CommitDto> commits(Authentication a, @PathVariable String owner, @PathVariable String repo){
    var p = policy.requireStudent(a.getPrincipal());
    return github.commits(p.userId(), owner, repo);
  }

  // ---- UniLink-native project discussions (persisted; NOT GitHub Issues) ----
  @GetMapping("/discussions")
  public List<DiscussionService.DiscussionDto> listDiscussions(Authentication a, @RequestParam String repo){
    policy.requireStudent(a.getPrincipal());
    return discussionService.list(repo);
  }
  @PostMapping("/discussions")
  public DiscussionService.DiscussionDto postDiscussion(Authentication a, @RequestBody DiscussionService.DiscussionBody b){
    var p = policy.requireStudent(a.getPrincipal());
    return discussionService.post(p, b);
  }
}
