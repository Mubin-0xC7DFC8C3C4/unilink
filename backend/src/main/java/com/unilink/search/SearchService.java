package com.unilink.search;
import com.unilink.common.Exceptions; import com.unilink.security.Principals;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
import java.util.*;
/** Search over posts and people. Portability (D-167, supersedes the
 *  D-028 FULLTEXT plan): matching is LIKE-based so the same queries run on
 *  MySQL 8 and TiDB (TiDB has no FULLTEXT). The user term is escaped so
 *  % and _ match literally. Sort fields are allow-listed; parameters are bound. */
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
    String like = "%" + escapeLike(q.trim()) + "%";
    String sql = """
      SELECT id, title, body, visibility FROM posts
      WHERE status='ACTIVE' AND (title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\')
      AND (visibility='NATIONAL'
        OR (scope_university_id=? AND visibility IN ('UNIVERSITY','DEPARTMENT'))
        OR (visibility='DEPARTMENT' AND scope_department_id=?)
        OR (visibility='DEPARTMENT_HUB' AND scope_catalog_id=?))
      """ + (sectorId==null ? "" : "AND EXISTS (SELECT 1 FROM post_sector_tags pst WHERE pst.post_id=posts.id AND pst.sector_tag_id=?) ") + """
      ORDER BY created_at DESC LIMIT 50""";
    Object[] args = sectorId==null
      ? new Object[]{ like, like, p.universityId(), p.departmentId(), p.catalogId() }
      : new Object[]{ like, like, p.universityId(), p.departmentId(), p.catalogId(), sectorId };
    return jdbc.query(sql, (rs,i)-> new PostHit(rs.getLong("id"), rs.getString("title"), rs.getString("body"), rs.getString("visibility")),
      args);
  }
  private static String escapeLike(String term){
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
  public List<UserHit> searchUsers(String q){
    if (q==null || q.isBlank()) return List.of();
    String like = "%" + escapeLike(q.trim()) + "%";
    return jdbc.query("SELECT id, full_name, university_id, department_id FROM users WHERE state='VERIFIED' AND full_name LIKE ? ESCAPE '\\' LIMIT 50",
      (rs,i)-> new UserHit(rs.getLong("id"), rs.getString("full_name"), rs.getLong("university_id"), rs.getLong("department_id")), like);
  }
}
