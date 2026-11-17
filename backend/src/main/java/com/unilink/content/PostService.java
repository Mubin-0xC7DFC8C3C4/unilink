package com.unilink.content;
import com.unilink.common.Exceptions; import com.unilink.community.FeedService;
import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import com.unilink.storage.ExifStripper;
import com.unilink.storage.StorageGateway; import com.unilink.storage.UploadValidator;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime; import java.util.*;
@Service
public class PostService {
  public record AttachmentFile(String storageKey, String fileName, String contentType) {}
  private final PostRepository posts; private final CommentRepository comments; private final CommentMentionRepository mentions;
  private final ReactionRepository reactions; private final BookmarkRepository bookmarks; private final PostSectorTagRepository postSectors;
  private final EventDetailRepository events; private final EventResponseRepository responses; private final UserRepository users;
  private final AttachmentRepository attachments;
  private final AccessPolicy policy; private final ContentValidator validator; private final FeedService feedService;
  private final StorageGateway storage; private final UploadValidator uploads;
  public PostService(PostRepository posts, CommentRepository comments, CommentMentionRepository mentions,
      ReactionRepository reactions, BookmarkRepository bookmarks, PostSectorTagRepository postSectors,
      EventDetailRepository events, EventResponseRepository responses, UserRepository users,
      AttachmentRepository attachments,
      AccessPolicy policy, ContentValidator validator, FeedService feedService,
      StorageGateway storage, UploadValidator uploads){
    this.posts=posts; this.comments=comments; this.mentions=mentions; this.reactions=reactions; this.bookmarks=bookmarks;
    this.postSectors=postSectors; this.events=events; this.responses=responses; this.users=users;
    this.attachments=attachments;
    this.policy=policy; this.validator=validator; this.feedService=feedService;
    this.storage=storage; this.uploads=uploads;
  }
  @Transactional
  public FeedService.PostDto createPost(Principals.UnilinkUserPrincipal p, String type, String visibility, String title, String body, List<Long> sectorIds){
    policy.requireVerified(p);
    validator.validate(body, title);
    if (!List.of("DEPARTMENT","UNIVERSITY","DEPARTMENT_HUB","NATIONAL").contains(visibility))
      throw new Exceptions.BadRequest("INVALID_VISIBILITY","Unknown visibility");
    User u = users.findById(p.userId()).orElseThrow(() -> new Exceptions.NotFound("User"));
    Post post = new Post();
    post.setAuthorId(p.userId()); post.setType(type); post.setVisibility(visibility);
    post.setTitle(title); post.setBody(body); post.setStatus("ACTIVE");
    post.setScopeUniversityId(p.universityId()); post.setScopeDepartmentId(p.departmentId()); post.setScopeCatalogId(p.catalogId());
    post = posts.save(post);
    if (sectorIds!=null) for (Long s : sectorIds){ PostSectorTag t=new PostSectorTag(); t.setPostId(post.getId()); t.setSectorTagId(s); postSectors.save(t); }
    return feedService.toDto(post);
  }
  @Transactional(readOnly=true)
  public FeedService.PostDto getPost(Long id, Principals.UnilinkUserPrincipal p){
    Post post = posts.findById(id).orElseThrow(() -> new Exceptions.NotFound("Post"));
    if ("VERIFIED".equals(p.state())) return feedService.toDto(post);
    if (!"NATIONAL".equals(post.getVisibility())) throw new Exceptions.NotFound("Post"); // D-016
    return feedService.toDto(post);
  }
  @Transactional
  public ContentDtos.CommentDto addComment(Principals.UnilinkUserPrincipal p, Long postId, String body, List<Long> mentionUserIds){
    policy.requireVerified(p);
    validator.validate(body, null);
    Post post = posts.findById(postId).orElseThrow(() -> new Exceptions.NotFound("Post"));
    if (!"ACTIVE".equals(post.getStatus())) throw new Exceptions.BadRequest("POST_NOT_ACTIVE","Post is not active");
    Comment c = new Comment(); c.setPostId(postId); c.setAuthorId(p.userId()); c.setBody(body); c.setStatus("ACTIVE");
    c = comments.save(c);
    if (mentionUserIds!=null) for (Long m : mentionUserIds){ // D-025: mentions reference user id
      if (users.existsById(m)){ CommentMention cm=new CommentMention(); cm.setCommentId(c.getId()); cm.setMentionedUserId(m); mentions.save(cm); }
    }
    return ContentDtos.CommentDto.from(c, authorName(c.getAuthorId()));
  }
  /** SHAPE: comment author's display name; null when the user is unknown — never fabricated. */
  private String authorName(Long authorId){
    return authorId==null ? null : users.findById(authorId).map(User::getFullName).orElse(null);
  }
  @Transactional(readOnly=true)
  public List<ContentDtos.CommentDto> listComments(Long postId, Principals.UnilinkUserPrincipal p){
    // I-15: comments now require an authenticated caller and apply the same
    // D-016 visibility rule as getPost — previously the controller took no
    // Authentication at all, so any authenticated student (verified or not)
    // could read comments on any post.
    getPost(postId, p);
    return comments.findByPostIdOrderByCreatedAtAsc(postId).stream().map(c -> ContentDtos.CommentDto.from(c, authorName(c.getAuthorId()))).toList();
  }
  @Transactional
  public void react(Principals.UnilinkUserPrincipal p, String targetType, Long targetId, String reaction){
    policy.requireVerified(p);
    reactions.findByUserIdAndTargetTypeAndTargetId(p.userId(), targetType, targetId).ifPresentOrElse(
      r -> { r.setReaction(reaction); reactions.save(r); },
      () -> { Reaction r=new Reaction(); r.setUserId(p.userId()); r.setTargetType(targetType); r.setTargetId(targetId); r.setReaction(reaction); reactions.save(r); });
  }
  @Transactional
  public void unreact(Principals.UnilinkUserPrincipal p, String targetType, Long targetId){
    policy.requireVerified(p);
    reactions.deleteByUserIdAndTargetTypeAndTargetId(p.userId(), targetType, targetId);
  }
  @Transactional
  public void bookmark(Principals.UnilinkUserPrincipal p, String targetType, Long targetId){
    policy.requireVerified(p);
    if (!bookmarks.existsByUserIdAndTargetTypeAndTargetId(p.userId(), targetType, targetId)){
      Bookmark b=new Bookmark(); b.setUserId(p.userId()); b.setTargetType(targetType); b.setTargetId(targetId); bookmarks.save(b);
    }
  }
  @Transactional
  public void unbookmark(Principals.UnilinkUserPrincipal p, String targetType, Long targetId){
    policy.requireVerified(p);
    bookmarks.deleteByUserIdAndTargetTypeAndTargetId(p.userId(), targetType, targetId);
  }
  @Transactional(readOnly=true)
  public List<ContentDtos.BookmarkDto> listBookmarks(Long userId){ return bookmarks.findByUserIdOrderByCreatedAtDesc(userId).stream()
    // SHAPE: title is the bookmarked post's title when targetType is POST, null otherwise.
    .map(b -> ContentDtos.BookmarkDto.from(b, "POST".equals(b.getTargetType())
      ? posts.findById(b.getTargetId()).map(Post::getTitle).orElse(null) : null)).toList(); }
  // ---- events (D-023, D-024) ----
  @Transactional
  public FeedService.PostDto createEvent(Principals.UnilinkUserPrincipal p, String visibility, String title, String body,
                                         LocalDateTime deadline, String regLink, String location, List<Long> sectorIds){
    if (deadline==null || !deadline.isAfter(LocalDateTime.now())) throw new Exceptions.BadRequest("PAST_DEADLINE","Event deadline must be in the future");
    FeedService.PostDto dto = createPost(p, "EVENT", visibility, title, body, sectorIds);
    EventDetail ev = new EventDetail(); ev.setId(dto.id()); ev.setDeadline(deadline); ev.setRegLink(regLink);
    ev.setLocation(location); ev.setRegistrationClosed(false); events.save(ev);
    return dto;
  }
  @Transactional
  public void respondToEvent(Principals.UnilinkUserPrincipal p, Long postId, String response){
    policy.requireVerified(p);
    if (!List.of("INTERESTED","GOING").contains(response)) throw new Exceptions.BadRequest("INVALID_RESPONSE","Response must be INTERESTED or GOING (D-023)");
    Post post = posts.findById(postId).orElseThrow(() -> new Exceptions.NotFound("Post"));
    if (!"EVENT".equals(post.getType())) throw new Exceptions.BadRequest("NOT_AN_EVENT","Post is not an event");
    EventDetail ev = events.findById(postId).orElseThrow(() -> new Exceptions.NotFound("Event"));
    if (ev.getDeadline().isBefore(LocalDateTime.now()) || ev.getArchivedAt()!=null)
      throw new Exceptions.BadRequest("EVENT_PAST","This event has ended");
    responses.findByUserIdAndPostId(p.userId(), postId).ifPresentOrElse(
      r -> { r.setResponse(response); responses.save(r); },
      () -> { EventResponse r=new EventResponse(); r.setUserId(p.userId()); r.setPostId(postId); r.setResponse(response); responses.save(r); });
  }
  // ---- post edit/delete (FR-CNT-02): owner only, non-event posts ----
  @Transactional
  public FeedService.PostDto updatePost(Principals.UnilinkUserPrincipal p, Long id, String title, String body,
                                        String visibility, List<Long> sectorIds){
    policy.requireVerified(p);
    Post post = posts.findByIdAndAuthorId(id, p.userId()).orElseThrow(() -> new Exceptions.NotFound("Post"));
    if ("EVENT".equals(post.getType())) throw new Exceptions.BadRequest("IS_EVENT","Events are edited via /api/events/{id}");
    if (!"ACTIVE".equals(post.getStatus())) throw new Exceptions.BadRequest("POST_NOT_ACTIVE","Post is not active");
    if (title!=null && !title.isBlank()) post.setTitle(title);
    if (body!=null && !body.isBlank()){ validator.validate(body, post.getTitle()); post.setBody(body); }
    if (visibility!=null){
      if (!List.of("DEPARTMENT","UNIVERSITY","DEPARTMENT_HUB","NATIONAL").contains(visibility))
        throw new Exceptions.BadRequest("INVALID_VISIBILITY","Unknown visibility");
      post.setVisibility(visibility);
    }
    if (sectorIds!=null){
      postSectors.deleteByPostId(id);
      for (Long s : sectorIds){ PostSectorTag t=new PostSectorTag(); t.setPostId(id); t.setSectorTagId(s); postSectors.save(t); }
    }
    return feedService.toDto(posts.save(post));
  }
  @Transactional
  public void deletePost(Principals.UnilinkUserPrincipal p, Long id){
    policy.requireVerified(p);
    Post post = posts.findByIdAndAuthorId(id, p.userId()).orElseThrow(() -> new Exceptions.NotFound("Post"));
    if ("EVENT".equals(post.getType())) throw new Exceptions.BadRequest("IS_EVENT","Events are deleted via /api/events/{id}");
    post.setStatus("REMOVED"); posts.save(post);
  }
  // ---- event detail / lists / edit / delete (FR-EVT-03/07/08) ----
  @Transactional(readOnly=true)
  public FeedService.PostDto getEvent(Long id, Principals.UnilinkUserPrincipal p){
    FeedService.PostDto dto = getPost(id, p);
    if (!"EVENT".equals(dto.type())) throw new Exceptions.NotFound("Event");
    return dto;
  }
  private Specification<Post> visibleEvents(Principals.UnilinkUserPrincipal p){
    return (r,q,cb) -> cb.and(cb.equal(r.get("type"),"EVENT"),
      cb.or(cb.equal(r.get("visibility"),"NATIONAL"),
        cb.equal(r.get("scopeUniversityId"), p.universityId()),
        cb.and(cb.equal(r.get("visibility"),"DEPARTMENT_HUB"), cb.equal(r.get("scopeCatalogId"), p.catalogId()))));
  }
  private Specification<Post> eventTime(boolean past){
    return (r,q,cb) -> {
      var sq = q.subquery(Long.class);
      var e = sq.from(EventDetail.class);
      // B1: correlate the subquery to the outer post. EventDetail shares the
      // post PK, so the old `id IN (subquery)` happened to agree — but only via
      // that implicit invariant; the correlation makes the list decided by
      // THIS post's own detail, not by any matching event.
      sq.select(e.get("id")).where(cb.equal(e.get("id"), r.get("id")), past
        ? cb.or(cb.lessThanOrEqualTo(e.get("deadline"), LocalDateTime.now()), cb.isNotNull(e.get("archivedAt")))
        : cb.and(cb.greaterThan(e.get("deadline"), LocalDateTime.now()), cb.isNull(e.get("archivedAt"))));
      return r.get("id").in(sq);
    };
  }
  /** Upcoming (FR-EVT-07) and past (archive) event lists, in the caller's scope. */
  @Transactional(readOnly=true)
  public List<FeedService.PostDto> listEvents(Principals.UnilinkUserPrincipal p, boolean past){
    Specification<Post> spec = Specification.where(visibleEvents(p))
      .and((r,q,cb) -> cb.equal(r.get("status"),"ACTIVE"))
      .and(eventTime(past));
    return posts.findAll(spec, Sort.by(Sort.Direction.DESC,"createdAt")).stream().map(feedService::toDto).toList();
  }
  @Transactional
  public FeedService.PostDto updateEvent(Principals.UnilinkUserPrincipal p, Long id, String title, String body,
                                         LocalDateTime deadline, String regLink, String location, String visibility){
    policy.requireVerified(p);
    Post post = posts.findByIdAndAuthorId(id, p.userId()).orElseThrow(() -> new Exceptions.NotFound("Event"));
    if (!"EVENT".equals(post.getType())) throw new Exceptions.BadRequest("NOT_AN_EVENT","Post is not an event");
    if (!"ACTIVE".equals(post.getStatus())) throw new Exceptions.BadRequest("POST_NOT_ACTIVE","Event is not active");
    EventDetail ev = events.findById(id).orElseThrow(() -> new Exceptions.NotFound("Event"));
    if (ev.getArchivedAt()!=null) throw new Exceptions.BadRequest("EVENT_PAST","This event has ended");
    if (title!=null && !title.isBlank()) post.setTitle(title);
    if (body!=null && !body.isBlank()){ validator.validate(body, post.getTitle()); post.setBody(body); }
    if (visibility!=null){
      if (!List.of("DEPARTMENT","UNIVERSITY","DEPARTMENT_HUB","NATIONAL").contains(visibility))
        throw new Exceptions.BadRequest("INVALID_VISIBILITY","Unknown visibility");
      post.setVisibility(visibility);
    }
    if (deadline!=null){
      if (!deadline.isAfter(LocalDateTime.now())) throw new Exceptions.BadRequest("PAST_DEADLINE","Event deadline must be in the future");
      ev.setDeadline(deadline);
    }
    if (regLink!=null) ev.setRegLink(regLink);
    if (location!=null) ev.setLocation(location);
    events.save(ev);
    return feedService.toDto(posts.save(post));
  }
  @Transactional
  public void deleteEvent(Principals.UnilinkUserPrincipal p, Long id){
    policy.requireVerified(p);
    Post post = posts.findByIdAndAuthorId(id, p.userId()).orElseThrow(() -> new Exceptions.NotFound("Event"));
    if (!"EVENT".equals(post.getType())) throw new Exceptions.BadRequest("NOT_AN_EVENT","Post is not an event");
    post.setStatus("REMOVED"); posts.save(post);
    events.findById(id).ifPresent(ev -> { ev.setArchivedAt(LocalDateTime.now()); events.save(ev); });
  }
  // ---- post attachments (FR-CNT-03): ≤10 MB image/PDF, owner attaches ----
  // D-104: at most 5 attachments per post, enforced server-side (400 over the
  // limit, checked BEFORE storing so a rejected file leaves no orphan).
  // D-106: images are EXIF-stripped before storage; PDFs pass through unchanged.
  @Transactional
  public ContentDtos.AttachmentDto addPostAttachment(Principals.UnilinkUserPrincipal p, Long postId,
      String fileName, String contentType, byte[] bytes){
    policy.requireVerified(p);
    Post post = posts.findByIdAndAuthorId(postId, p.userId()).orElseThrow(() -> new Exceptions.NotFound("Post"));
    if (!"ACTIVE".equals(post.getStatus())) throw new Exceptions.BadRequest("POST_NOT_ACTIVE","Post is not active");
    if (bytes==null || bytes.length==0) throw new Exceptions.BadRequest("INVALID_FILE","File is empty or unreadable");
    String sniffed = uploads.validateAttachment(bytes, UploadValidator.ATTACHMENT_MAX);
    if (attachments.countByOwnerTypeAndOwnerId("POST", postId) >= UploadValidator.MAX_ATTACHMENTS_PER_POST)
      throw new Exceptions.BadRequest("ATTACHMENT_LIMIT","A post can have at most 5 attachments");
    byte[] stored = ExifStripper.strip(bytes);
    String safeName = UploadValidator.sanitizeFileName(fileName);
    Attachment a = new Attachment();
    a.setOwnerType("POST"); a.setOwnerId(postId);
    a.setStorageKey(storage.store("post-attachments", safeName, sniffed, stored));
    a.setFileName(safeName); a.setContentType(sniffed); a.setSizeBytes((long) stored.length);
    return ContentDtos.AttachmentDto.from(attachments.save(a));
  }
  /** Owner-only delete (D-107): the row and its stored object are both removed,
   *  so a deleted attachment leaves no orphaned file. A non-owner (or a wrong
   *  post id) gets 404, never a hint the attachment exists. */
  @Transactional
  public void deletePostAttachment(Principals.UnilinkUserPrincipal p, Long postId, Long attachmentId){
    policy.requireVerified(p);
    posts.findByIdAndAuthorId(postId, p.userId()).orElseThrow(() -> new Exceptions.NotFound("Post"));
    Attachment a = attachments.findById(attachmentId).orElseThrow(() -> new Exceptions.NotFound("Attachment"));
    if (!"POST".equals(a.getOwnerType()) || !postId.equals(a.getOwnerId())) throw new Exceptions.NotFound("Attachment");
    storage.delete(a.getStorageKey());
    attachments.delete(a);
  }
  @Transactional(readOnly=true)
  public List<ContentDtos.AttachmentDto> listPostAttachments(Principals.UnilinkUserPrincipal p, Long postId){
    getPost(postId, p); // visibility check (D-016)
    requireActivePost(postId);
    return attachments.findByOwnerTypeAndOwnerId("POST", postId).stream().map(ContentDtos.AttachmentDto::from).toList();
  }
  @Transactional(readOnly=true)
  public AttachmentFile openPostAttachment(Principals.UnilinkUserPrincipal p, Long postId, Long attachmentId){
    getPost(postId, p); // visibility check (D-016)
    requireActivePost(postId);
    Attachment a = attachments.findById(attachmentId).orElseThrow(() -> new Exceptions.NotFound("Attachment"));
    if (!"POST".equals(a.getOwnerType()) || !postId.equals(a.getOwnerId())) throw new Exceptions.NotFound("Attachment");
    if (!storage.exists(a.getStorageKey())) throw new Exceptions.NotFound("File");
    return new AttachmentFile(a.getStorageKey(), a.getFileName(), a.getContentType());
  }
  /** D-107: deletePost/deleteEvent only soft-delete (status REMOVED); the files
   *  stay so a moderation RESTORE can bring the post back, but while the post
   *  is not ACTIVE its attachments are not listable or downloadable. */
  private void requireActivePost(Long postId){
    Post post = posts.findById(postId).orElseThrow(() -> new Exceptions.NotFound("Post"));
    if (!"ACTIVE".equals(post.getStatus())) throw new Exceptions.NotFound("Post");
  }
}
