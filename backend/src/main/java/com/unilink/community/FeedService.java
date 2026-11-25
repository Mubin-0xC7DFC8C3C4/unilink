package com.unilink.community;
import com.unilink.common.*; import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import com.unilink.security.Principals;
import org.springframework.data.domain.*; import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime; import java.util.*;
@Service
public class FeedService {
  public record PostDto(Long id, Long authorId, String authorName, String type, String visibility, String title,
                        String body, String status, LocalDateTime createdAt, LocalDateTime eventDeadline, String regLink,
                        String location, long commentCount, List<String> sectors,
                        long reactionCount, String viewerReaction, boolean savedByViewer,
                        String authorDepartment, String authorUniversity, String authorBatch,
                        String eventStatus, String organizerName) {}
  private final PostRepository posts; private final UserRepository users; private final EventDetailRepository events;
  private final CommentRepository comments; private final PostSectorTagRepository postSectors; private final SectorTagRepository sectors;
  private final ReactionRepository reactions; private final BookmarkRepository bookmarks; private final FollowRepository follows;
  private final UniversityRepository universities; private final DepartmentRepository departments; private final BatchRepository batches;
  public FeedService(PostRepository posts, UserRepository users, EventDetailRepository events,
                     CommentRepository comments, PostSectorTagRepository postSectors, SectorTagRepository sectors,
                     ReactionRepository reactions, BookmarkRepository bookmarks, FollowRepository follows,
                     UniversityRepository universities, DepartmentRepository departments, BatchRepository batches){
    this.posts=posts; this.users=users; this.events=events;
    this.comments=comments; this.postSectors=postSectors; this.sectors=sectors;
    this.reactions=reactions; this.bookmarks=bookmarks; this.follows=follows;
    this.universities=universities; this.departments=departments; this.batches=batches; }
  @Transactional(readOnly=true)
  public PageResponse<PostDto> feed(Principals.UnilinkUserPrincipal p, String level, String cursor, int limit, Long sectorId){
    return feed(p, level, cursor, limit, sectorId, "recent");
  }
  @Transactional(readOnly=true)
  public PageResponse<PostDto> feed(Principals.UnilinkUserPrincipal p, String level, String cursor, int limit, Long sectorId, String sort){
    limit = Math.min(Math.max(limit,1),50);
    Specification<Post> spec;
    if ("VERIFIED".equals(p.state())){
      spec = Specification.where(FeedSpecifications.statusActive())
        .and(FeedSpecifications.level(level, p.universityId(), p.departmentId(), p.catalogId()))
        .and(FeedSpecifications.sector(sectorId)).and(FeedSpecifications.liveEventsOnly());
    } else {
      // D-016: unverified read-only National feed
      spec = Specification.where(FeedSpecifications.statusActive())
        .and(FeedSpecifications.nationalOnly()).and(FeedSpecifications.sector(sectorId));
    }
    if ("top".equals(sort)){
      // Top ranking (2026-11-22 spec): score = (affinity + engagement) /
      // (hoursSinceCreated + 2)^1.5 over a bounded recent candidate set.
      // Affinity: followed author +15, same department +10, same
      // university +6. Engagement: comments*3 + saves*2 + reactions*1.
      // Cursor pagination does not apply to a ranked list; the first
      // `limit` items are returned with no next cursor.
      List<Post> candidates = new java.util.ArrayList<>(posts.findAll(spec, PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC,"createdAt"))).getContent());
      LocalDateTime now = LocalDateTime.now();
      candidates.sort((a, b) -> Double.compare(topScore(b, p, now), topScore(a, p, now)));
      List<Post> result = candidates.size()>limit ? candidates.subList(0,limit) : candidates;
      return PageResponse.of(result.stream().map(post -> toDto(post, p.userId())).toList(), null);
    }
    spec = spec.and(FeedSpecifications.beforeCursor(cursor));
    Pageable page = PageRequest.of(0, limit+1, Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by(Sort.Direction.DESC,"id")));
    List<Post> rows = posts.findAll(spec, page).getContent();
    List<Post> result = rows.size()>limit ? rows.subList(0,limit) : rows;
    String next = null;
    if (rows.size()>limit){ Post last = result.get(result.size()-1);
      next = CursorCodec.encode(last.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), last.getId()); }
    return PageResponse.of(result.stream().map(post -> toDto(post, p.userId())).toList(), next);
  }
  private double topScore(Post post, Principals.UnilinkUserPrincipal p, LocalDateTime now){
    double affinity = 0;
    if (post.getAuthorId() != null){
      if (follows.existsByFollowerIdAndFollowedId(p.userId(), post.getAuthorId())) affinity += 15;
      if (Objects.equals(post.getScopeDepartmentId(), p.departmentId())) affinity += 10;
      if (Objects.equals(post.getScopeUniversityId(), p.universityId())) affinity += 6;
    }
    double engagement = comments.countByPostId(post.getId())*3.0
      + bookmarks.countByTargetTypeAndTargetId("POST", post.getId())*2.0
      + reactions.countByTargetTypeAndTargetId("POST", post.getId());
    double hours = Math.max(0, java.time.Duration.between(post.getCreatedAt(), now).toMinutes()/60.0);
    return (affinity + engagement) / Math.pow(hours + 2, 1.5);
  }
  public PostDto toDto(Post post){ return toDto(post, null); }
  public PostDto toDto(Post post, Long viewerId){
    User author = post.getAuthorId()==null ? null : users.findById(post.getAuthorId()).orElse(null);
    String name = author!=null ? author.getFullName() : "UniLink Administration";
    EventDetail ev = "EVENT".equals(post.getType()) ? events.findById(post.getId()).orElse(null) : null;
    // SHAPE: commentCount counts the post's comments (same set listComments returns);
    // sectors are the post's sector tag names, dangling tags dropped, never fabricated.
    // D-152: count via a COUNT query, not by loading every comment row.
    long commentCount = comments.countByPostId(post.getId());
    List<String> sectorNames = postSectors.findByPostId(post.getId()).stream()
      .flatMap(t -> sectors.findById(t.getSectorTagId()).map(SectorTag::getName).stream()).toList();
    long reactionCount = reactions.countByTargetTypeAndTargetId("POST", post.getId());
    String viewerReaction = viewerId==null ? null : reactions.findByUserIdAndTargetTypeAndTargetId(viewerId, "POST", post.getId()).map(Reaction::getReaction).orElse(null);
    boolean savedByViewer = viewerId!=null && bookmarks.existsByUserIdAndTargetTypeAndTargetId(viewerId, "POST", post.getId());
    String authorDepartment = null, authorUniversity = null, authorBatch = null;
    if (author != null){
      // D-193/D-194: the free-text label wins; the legacy row is the fallback.
      if (author.getDepartmentLabel()!=null) authorDepartment = author.getDepartmentLabel();
      else if (author.getDepartmentId()!=null) authorDepartment = departments.findById(author.getDepartmentId()).map(Department::getName).orElse(null);
      if (author.getUniversityId()!=null) authorUniversity = universities.findById(author.getUniversityId()).map(University::getName).orElse(null);
      if (author.getBatchLabel()!=null) authorBatch = author.getBatchLabel();
      else if (author.getBatchId()!=null) authorBatch = batches.findById(author.getBatchId()).map(Batch::getLabel).orElse(null);
    }
    return new PostDto(post.getId(), post.getAuthorId(), name, post.getType(), post.getVisibility(),
      post.getTitle(), post.getBody(), post.getStatus(), post.getCreatedAt(),
      ev==null?null:ev.getDeadline(), ev==null?null:ev.getRegLink(), ev==null?null:ev.getLocation(),
      commentCount, sectorNames, reactionCount, viewerReaction, savedByViewer,
      authorDepartment, authorUniversity, authorBatch,
      ev==null?null:(ev.getEventStatus()==null?"PUBLISHED":ev.getEventStatus()),
      ev==null?null:ev.getOrganizerName());
  }
}
