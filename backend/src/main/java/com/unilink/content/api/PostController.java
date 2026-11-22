package com.unilink.content.api;
import com.unilink.common.PageResponse; import com.unilink.community.FeedService;
import com.unilink.content.ContentDtos; import com.unilink.content.PostService;
import com.unilink.security.AccessPolicy;
import com.unilink.storage.StorageGateway;
import com.unilink.team.TeamDtos; import com.unilink.team.TeamService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDateTime; import java.util.*;
@RestController @RequestMapping("/api")
public class PostController {
  private final PostService posts; private final AccessPolicy policy; private final JdbcTemplate jdbc;
  private final TeamService teams; private final StorageGateway storage;
  public PostController(PostService p, AccessPolicy a, JdbcTemplate j, TeamService t, StorageGateway s){ posts=p; policy=a; jdbc=j; teams=t; storage=s; }
  public record CreatePost(String type, String visibility, String title, String body, List<Long> sectorIds) {}
  public record CreateEvent(String visibility, String title, String body, LocalDateTime deadline, String regLink, String location, List<Long> sectorIds) {}
  public record UpdatePost(String title, String body, String visibility, List<Long> sectorIds) {}
  public record UpdateEvent(String title, String body, LocalDateTime deadline, String regLink, String location, String visibility) {}
  public record TeamRequestBody(String name, Integer maxSize) {}
  public record CommentBody(String body, List<Long> mentionUserIds, Long parentId) {}
  public record ReactionBody(String reaction) {}
  public record TargetBody(String targetType, Long targetId) {}
  @PostMapping("/posts") public FeedService.PostDto create(Authentication a, @RequestBody CreatePost r){
    var p = policy.requireStudent(a.getPrincipal());
    return posts.createPost(p, r.type()==null?"POST":r.type(), r.visibility(), r.title(), r.body(), r.sectorIds());
  }
  @GetMapping("/posts/{id}") public FeedService.PostDto get(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); return posts.getPost(id, p);
  }
  /** Post edit/delete (FR-CNT-02): owner only, non-event posts. */
  @PatchMapping("/posts/{id}") public FeedService.PostDto update(Authentication a, @PathVariable Long id, @RequestBody UpdatePost r){
    var p = policy.requireStudent(a.getPrincipal());
    return posts.updatePost(p, id, r.title(), r.body(), r.visibility(), r.sectorIds());
  }
  @DeleteMapping("/posts/{id}") public Map<String,String> delete(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); posts.deletePost(p, id); return Map.of("status","deleted");
  }
  /** Post attachments (FR-CNT-03): one image/PDF per call, ≤10 MB, max 5 per post (D-104). */
  @PostMapping(value="/posts/{id}/attachments", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public ContentDtos.AttachmentDto addAttachment(Authentication a, @PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
    var p = policy.requireStudent(a.getPrincipal());
    return posts.addPostAttachment(p, id, file.getOriginalFilename(), file.getContentType(), file.getBytes());
  }
  /** Owner-only attachment delete (D-107): removes the row and the stored object. */
  @DeleteMapping("/posts/{id}/attachments/{attachmentId}")
  public Map<String,String> deleteAttachment(Authentication a, @PathVariable Long id, @PathVariable Long attachmentId){
    var p = policy.requireStudent(a.getPrincipal());
    posts.deletePostAttachment(p, id, attachmentId);
    return Map.of("status","deleted");
  }
  @GetMapping("/posts/{id}/attachments") public List<ContentDtos.AttachmentDto> attachments(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); return posts.listPostAttachments(p, id);
  }
  @GetMapping("/posts/{id}/attachments/{attachmentId}")
  public ResponseEntity<InputStreamResource> downloadAttachment(Authentication a, @PathVariable Long id, @PathVariable Long attachmentId){
    var p = policy.requireStudent(a.getPrincipal());
    var f = posts.openPostAttachment(p, id, attachmentId);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType()))
      // D-106: the stored file name is sanitised (no quotes/CR/LF), so it cannot
      // break out of the quoted Content-Disposition value (header injection).
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+com.unilink.storage.UploadValidator.sanitizeFileName(f.fileName())+"\"")
      .body(new InputStreamResource(storage.open(f.storageKey())));
  }
  @PostMapping("/posts/{id}/comments") public ContentDtos.CommentDto comment(Authentication a, @PathVariable Long id, @RequestBody CommentBody r){
    var p = policy.requireStudent(a.getPrincipal()); return posts.addComment(p, id, r.body(), r.mentionUserIds(), r.parentId());
  }
  @GetMapping("/posts/{id}/comments") public List<ContentDtos.CommentDto> comments(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); return posts.listComments(id, p);
  }
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
  @GetMapping("/bookmarks") public List<ContentDtos.BookmarkDto> bookmarks(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return posts.listBookmarks(p.userId());
  }
  /** Events are created by administrators only (product rule, 2026-11-22).
   *  The student event-creation route stays mapped so clients get a clear
   *  403 instead of a 404, but it never creates anything. Admin creation
   *  lives at POST /api/admin/events. */
  @PostMapping("/events") public FeedService.PostDto createEvent(Authentication a, @RequestBody CreateEvent r){
    policy.requireStudent(a.getPrincipal());
    throw new com.unilink.common.Exceptions.Forbidden("Events are created by UniLink administrators only");
  }
  /** Event detail (FR-EVT-03). */
  @GetMapping("/events/{id}") public FeedService.PostDto getEvent(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); return posts.getEvent(id, p);
  }
  /** Upcoming events (FR-EVT-07) and the past-event archive (FR-EVT-05). */
  @GetMapping("/events/upcoming") public List<FeedService.PostDto> upcomingEvents(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return posts.listEvents(p, false);
  }
  @GetMapping("/events") public List<FeedService.PostDto> listEvents(Authentication a, @RequestParam(defaultValue="false") boolean past){
    var p = policy.requireStudent(a.getPrincipal()); return posts.listEvents(p, past);
  }
  /** Event edit/delete (FR-EVT-08): owner only. */
  @PatchMapping("/events/{id}") public FeedService.PostDto updateEvent(Authentication a, @PathVariable Long id, @RequestBody UpdateEvent r){
    var p = policy.requireStudent(a.getPrincipal());
    return posts.updateEvent(p, id, r.title(), r.body(), r.deadline(), r.regLink(), r.location(), r.visibility());
  }
  @DeleteMapping("/events/{id}") public Map<String,String> deleteEvent(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); posts.deleteEvent(p, id); return Map.of("status","deleted");
  }
  /** "Looking for Teammates" (FR-EVT-06 / FR-TEAM-01): creates the team for
   *  this event; the caller becomes its creator and team-chat admin. */
  @PostMapping("/events/{id}/team-request") public TeamDtos.TeamDto teamRequest(Authentication a, @PathVariable Long id,
      @RequestBody(required=false) TeamRequestBody r){
    var p = policy.requireStudent(a.getPrincipal());
    String name = (r==null || r.name()==null || r.name().isBlank()) ? "Team for event " + id : r.name();
    int maxSize = (r==null || r.maxSize()==null) ? 4 : r.maxSize();
    return teams.createTeam(p, id, name, maxSize);
  }
  @PostMapping("/events/{id}/respond") public Map<String,String> respond(Authentication a, @PathVariable Long id, @RequestParam String response){
    var p = policy.requireStudent(a.getPrincipal()); posts.respondToEvent(p, id, response); return Map.of("status","ok");
  }
  /** Calendar event (D-141): camelCase like every other DTO — the old
   *  raw {@code queryForList} map exposed the snake_case {@code reg_link}
   *  column name, the one non-camelCase key in the student API. */
  public record CalendarEventDto(Long id, String title, LocalDateTime deadline, String location, String regLink) {}
  /** Calendar: in-scope events with deadline in range (D-024 deadline>now filter applies). */
  @GetMapping("/calendar")
  public List<CalendarEventDto> calendar(Authentication a, @RequestParam String from, @RequestParam String to){
    var p = policy.requireStudent(a.getPrincipal());
    // D-141: from/to were passed straight to the SQL comparison, so
    // garbage dates were silently coerced (H2/MySQL) and the endpoint
    // answered 200 with wrong results instead of 400. Accepts a date
    // (2026-11-01, as the frontend sends) or an ISO local date-time.
    LocalDateTime fromDt = parseCalendarBound(from, "from");
    LocalDateTime toDt = parseCalendarBound(to, "to");
    List<Map<String,Object>> rows = jdbc.queryForList("""
      SELECT p.id, p.title, e.deadline, e.location, e.reg_link FROM posts p
      JOIN event_details e ON e.post_id=p.id
      WHERE p.type='EVENT' AND p.status='ACTIVE' AND e.deadline>? AND e.deadline<=? AND e.archived_at IS NULL
      AND (p.visibility='NATIONAL' OR p.scope_university_id=?)
      ORDER BY e.deadline""", fromDt, toDt, p.universityId());
    return rows.stream().map(r -> new CalendarEventDto(
      r.get("id")==null ? null : ((Number) r.get("id")).longValue(),
      (String) r.get("title"),
      r.get("deadline") instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) r.get("deadline"),
      (String) r.get("location"), (String) r.get("reg_link"))).toList();
  }
  private static LocalDateTime parseCalendarBound(String value, String name){
    if (value==null) throw new com.unilink.common.Exceptions.BadRequest("INVALID_REQUEST","Calendar bound '"+name+"' is required");
    try { return LocalDateTime.parse(value); }
    catch (java.time.format.DateTimeParseException ignored) { /* fall through to date-only */ }
    try { return java.time.LocalDate.parse(value).atStartOfDay(); }
    catch (java.time.format.DateTimeParseException e){
      throw new com.unilink.common.Exceptions.BadRequest("INVALID_REQUEST","Calendar bound '"+name+"' must be a date (YYYY-MM-DD) or date-time");
    }
  }
}
