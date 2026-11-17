package com.unilink.search;
import com.unilink.common.Exceptions; import com.unilink.security.Principals;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
import java.util.*;
/** SearchStrategy over MySQL FULLTEXT (D-028). Sort fields are allow-listed; parameters are bound. */
@Service
public class SearchService {
  private final JdbcTemplate jdbc;
  public SearchService(JdbcTemplate jdbc){ this.jdbc=jdbc; }
  public record PostHit(Long id, String title, String body, String visibility) {}
  public record UserHit(Long id, String fullName, Long universityId, Long departmentId) {}
  /** FR-SRC-02: filter posts by sector tag id (the `sector` param was
   *  previously accepted by the controller and ignored). */
  public List<PostHit> searchPosts(Principals.UnilinkUserPrincipal p, String q, Long sectorId){
    if (q==null || q.isBlank()) return List.of();
    String sql = """
      SELECT id, title, body, visibility FROM posts
      WHERE status='ACTIVE' AND MATCH(title, body) AGAINST (? IN NATURAL LANGUAGE MODE)
      AND (visibility='NATIONAL'
        OR (scope_university_id=? AND visibility IN ('UNIVERSITY','DEPARTMENT'))
        OR (visibility='DEPARTMENT' AND scope_department_id=?)
        OR (visibility='DEPARTMENT_HUB' AND scope_catalog_id=?))
      """ + (sectorId==null ? "" : "AND EXISTS (SELECT 1 FROM post_sector_tags pst WHERE pst.post_id=posts.id AND pst.sector_tag_id=?) ") + """
      ORDER BY created_at DESC LIMIT 50""";
    Object[] args = sectorId==null
      ? new Object[]{ q, p.universityId(), p.departmentId(), p.catalogId() }
      : new Object[]{ q, p.universityId(), p.departmentId(), p.catalogId(), sectorId };
    return jdbc.query(sql, (rs,i)-> new PostHit(rs.getLong("id"), rs.getString("title"), rs.getString("body"), rs.getString("visibility")),
      args);
  }
  public List<UserHit> searchUsers(String q){
    if (q==null || q.isBlank()) return List.of();
    return jdbc.query("SELECT id, full_name, university_id, department_id FROM users WHERE state='VERIFIED' AND MATCH(full_name) AGAINST (? IN NATURAL LANGUAGE MODE) LIMIT 50",
      (rs,i)-> new UserHit(rs.getLong("id"), rs.getString("full_name"), rs.getLong("university_id"), rs.getLong("department_id")), q);
  }
}
