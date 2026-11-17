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
                        String location, long commentCount, List<String> sectors) {}
  private final PostRepository posts; private final UserRepository users; private final EventDetailRepository events;
  private final CommentRepository comments; private final PostSectorTagRepository postSectors; private final SectorTagRepository sectors;
  public FeedService(PostRepository posts, UserRepository users, EventDetailRepository events,
                     CommentRepository comments, PostSectorTagRepository postSectors, SectorTagRepository sectors){
    this.posts=posts; this.users=users; this.events=events;
    this.comments=comments; this.postSectors=postSectors; this.sectors=sectors; }
  @Transactional(readOnly=true)
  public PageResponse<PostDto> feed(Principals.UnilinkUserPrincipal p, String level, String cursor, int limit, Long sectorId){
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
    spec = spec.and(FeedSpecifications.beforeCursor(cursor));
    Pageable page = PageRequest.of(0, limit+1, Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by(Sort.Direction.DESC,"id")));
    List<Post> rows = posts.findAll(spec, page).getContent();
    List<Post> result = rows.size()>limit ? rows.subList(0,limit) : rows;
    String next = null;
    if (rows.size()>limit){ Post last = result.get(result.size()-1);
      next = CursorCodec.encode(last.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), last.getId()); }
    return PageResponse.of(result.stream().map(this::toDto).toList(), next);
  }
  public PostDto toDto(Post post){
    String name = users.findById(post.getAuthorId()).map(User::getFullName).orElse("Deleted user");
    EventDetail ev = "EVENT".equals(post.getType()) ? events.findById(post.getId()).orElse(null) : null;
    // SHAPE: commentCount counts the post's comments (same set listComments returns);
    // sectors are the post's sector tag names, dangling tags dropped, never fabricated.
    long commentCount = comments.findByPostIdOrderByCreatedAtAsc(post.getId()).size();
    List<String> sectorNames = postSectors.findByPostId(post.getId()).stream()
      .flatMap(t -> sectors.findById(t.getSectorTagId()).map(SectorTag::getName).stream()).toList();
    return new PostDto(post.getId(), post.getAuthorId(), name, post.getType(), post.getVisibility(),
      post.getTitle(), post.getBody(), post.getStatus(), post.getCreatedAt(),
      ev==null?null:ev.getDeadline(), ev==null?null:ev.getRegLink(), ev==null?null:ev.getLocation(),
      commentCount, sectorNames);
  }
}
