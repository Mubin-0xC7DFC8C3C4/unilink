package com.unilink.content.api;
import com.unilink.common.PageResponse; import com.unilink.community.FeedService;
import com.unilink.content.PostService; import com.unilink.identity.domain.*;
import com.unilink.security.AccessPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime; import java.util.*;
@RestController @RequestMapping("/api")
public class PostController {
  private final PostService posts; private final AccessPolicy policy; private final JdbcTemplate jdbc;
  public PostController(PostService p, AccessPolicy a, JdbcTemplate j){ posts=p; policy=a; jdbc=j; }
  public record CreatePost(String type, String visibility, String title, String body, List<Long> sectorIds) {}
  public record CreateEvent(String visibility, String title, String body, LocalDateTime deadline, String regLink, String location, List<Long> sectorIds) {}
  public record CommentBody(String body, List<Long> mentionUserIds) {}
  public record ReactionBody(String reaction) {}
  public record TargetBody(String targetType, Long targetId) {}
  @PostMapping("/posts") public FeedService.PostDto create(Authentication a, @RequestBody CreatePost r){
    var p = policy.requireStudent(a.getPrincipal());
    return posts.createPost(p, r.type()==null?"POST":r.type(), r.visibility(), r.title(), r.body(), r.sectorIds());
  }
  @GetMapping("/posts/{id}") public FeedService.PostDto get(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); return posts.getPost(id, p);
  }
  @PostMapping("/posts/{id}/comments") public Comment comment(Authentication a, @PathVariable Long id, @RequestBody CommentBody r){
    var p = policy.requireStudent(a.getPrincipal()); return posts.addComment(p, id, r.body(), r.mentionUserIds());
  }
  @GetMapping("/posts/{id}/comments") public List<Comment> comments(@PathVariable Long id){ return posts.listComments(id); }
  @PostMapping("/reactions") public Map<String,String> react(Authentication a, @RequestBody TargetBody t, @RequestParam(defaultValue="LIKE") String reaction){
    var p = policy.requireStudent(a.getPrincipal()); posts.react(p, t.targetType(), t.targetId(), reaction); return Map.of("status","ok");
  }
  @DeleteMapping("/reactions") public Map<String,String> unreact(Authentication a, @RequestBody TargetBody t){
    var p = policy.requireStudent(a.getPrincipal()); posts.unreact(p, t.targetType(), t.targetId()); return Map.of("status","ok");
  }
  @PostMapping("/bookmarks") public Map<String,String> bookmark(Authentication a, @RequestBody TargetBody t){
    var p = policy.requireStudent(a.getPrincipal()); posts.bookmark(p, t.targetType(), t.targetId()); return Map.of("status","ok");
  }
  @DeleteMapping("/bookmarks") public Map<String,String> unbookmark(Authentication a, @RequestBody TargetBody t){
    var p = policy.requireStudent(a.getPrincipal()); posts.unbookmark(p, t.targetType(), t.targetId()); return Map.of("status","ok");
  }
  @GetMapping("/bookmarks") public List<Bookmark> bookmarks(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return posts.listBookmarks(p.userId());
  }
  @PostMapping("/events") public FeedService.PostDto createEvent(Authentication a, @RequestBody CreateEvent r){
    var p = policy.requireStudent(a.getPrincipal());
    return posts.createEvent(p, r.visibility(), r.title(), r.body(), r.deadline(), r.regLink(), r.location(), r.sectorIds());
  }
  @PostMapping("/events/{id}/respond") public Map<String,String> respond(Authentication a, @PathVariable Long id, @RequestParam String response){
    var p = policy.requireStudent(a.getPrincipal()); posts.respondToEvent(p, id, response); return Map.of("status","ok");
  }
  /** Calendar: in-scope events with deadline in range (D-024 deadline>now filter applies). */
  @GetMapping("/calendar")
  public List<Map<String,Object>> calendar(Authentication a, @RequestParam String from, @RequestParam String to){
    var p = policy.requireStudent(a.getPrincipal());
    return jdbc.queryForList("""
      SELECT p.id, p.title, e.deadline, e.location, e.reg_link FROM posts p
      JOIN event_details e ON e.post_id=p.id
      WHERE p.type='EVENT' AND p.status='ACTIVE' AND e.deadline>? AND e.deadline<=? AND e.archived_at IS NULL
      AND (p.visibility='NATIONAL' OR p.scope_university_id=?)
      ORDER BY e.deadline""", from, to, p.universityId());
  }
}
