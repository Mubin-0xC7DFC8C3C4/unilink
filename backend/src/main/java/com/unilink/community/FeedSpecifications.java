package com.unilink.community;
import com.unilink.identity.domain.Post;
import jakarta.persistence.criteria.*; import org.springframework.data.jpa.domain.Specification;
/** Specification pattern (ARCH §8): the visibility matrix of D-021 as composable specs.
 *  Post has no JPA associations (flat foreign-key columns), so sector/event filters use subqueries. */
public final class FeedSpecifications {
  private FeedSpecifications(){}
  public static Specification<Post> statusActive(){ return (r,q,cb)-> cb.equal(r.get("status"),"ACTIVE"); }
  public static Specification<Post> level(String level, Long universityId, Long departmentId, Long catalogId){
    return (r,q,cb)-> switch (level) {
      case "DEPARTMENT" -> cb.and(cb.equal(r.get("visibility"),"DEPARTMENT"),
        cb.equal(r.get("scopeUniversityId"),universityId), cb.equal(r.get("scopeDepartmentId"),departmentId));
      case "UNIVERSITY" -> cb.and(cb.equal(r.get("scopeUniversityId"),universityId),
        cb.or(cb.equal(r.get("visibility"),"UNIVERSITY"), cb.equal(r.get("visibility"),"DEPARTMENT")));
      case "HUB" -> cb.and(cb.equal(r.get("visibility"),"DEPARTMENT_HUB"), cb.equal(r.get("scopeCatalogId"),catalogId));
      case "NATIONAL" -> cb.equal(r.get("visibility"),"NATIONAL");
      default -> cb.disjunction();
    };
  }
  public static Specification<Post> sector(Long sectorId){
    if (sectorId==null) return (r,q,cb)-> cb.conjunction();
    return (r,q,cb)->{
      Subquery<Long> sq = q.subquery(Long.class);
      Root<com.unilink.identity.domain.PostSectorTag> t = sq.from(com.unilink.identity.domain.PostSectorTag.class);
      sq.select(t.get("postId")).where(cb.equal(t.get("sectorTagId"), sectorId));
      return r.get("id").in(sq);
    };
  }
  /** Keyset pagination: strictly before (createdAt, id) of the cursor. */
  public static Specification<Post> beforeCursor(String cursor){
    if (cursor==null || cursor.isBlank()) return (r,q,cb)-> cb.conjunction();
    long[] c = com.unilink.common.CursorCodec.decode(cursor);
    java.time.LocalDateTime at = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(c[0]), java.time.ZoneOffset.UTC);
    return (r,q,cb)-> cb.or(cb.lessThan(r.get("createdAt"), at),
      cb.and(cb.equal(r.get("createdAt"), at), cb.lessThan(r.get("id"), c[1])));
  }
  /** Unverified students see only NATIONAL items, read-only (D-016). */
  public static Specification<Post> nationalOnly(){ return (r,q,cb)-> cb.equal(r.get("visibility"),"NATIONAL"); }
  /** Events whose deadline passed are excluded even before the archive job runs (D-024). */
  public static Specification<Post> liveEventsOnly(){
    return (r,q,cb)->{
      Subquery<Long> sq = q.subquery(Long.class);
      Root<com.unilink.identity.domain.EventDetail> e = sq.from(com.unilink.identity.domain.EventDetail.class);
      sq.select(e.get("id")).where(cb.or(cb.lessThanOrEqualTo(e.get("deadline"), java.time.LocalDateTime.now()),
        cb.isNotNull(e.get("archivedAt"))));
      return cb.not(r.get("id").in(sq));
    };
  }
}
