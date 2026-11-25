package com.unilink.search;
import com.unilink.common.Exceptions; import com.unilink.security.Principals;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
/** Search over posts and people. Portability (D-167, supersedes the
 *  D-028 FULLTEXT plan): matching is LIKE-based so the same queries run on
 *  MySQL 8 and TiDB (TiDB has no FULLTEXT). The user term is escaped so
 *  % and _ match literally. Sort fields are allow-listed; parameters are bound.
 *  D-201: the query is tokenised on whitespace — every token must match
 *  (AND of per-token LIKEs), so "Sadia Islam" also matches in reversed
 *  order. Universities / departments (active catalogue rows) and skills
 *  (join user_skills -> skills, matched names on the user hit) are
 *  searchable; EVENT posts match on location / organiser too, and expired
 *  events are excluded (liveEventsOnly). Keyset pagination: cursor is the
 *  last returned id, limit is clamped to 1..50. */
@Service
public class SearchService {
  private final JdbcTemplate jdbc;
  public SearchService(JdbcTemplate jdbc){ this.jdbc=jdbc; }
  public record PostHit(Long id, String title, String body, String visibility, String type,
                        String location, String organizerName) {}
  public record UserHit(Long id, String fullName, Long universityId, Long departmentId, List<String> matchedSkills) {}
  public record UniversityHit(Long id, String name, String shortName, String category) {}
  public record DepartmentHit(Long id, String name, Long universityId) {}
  public record Page<T>(List<T> items, String nextCursor, long total) {}
  private static List<String> tokens(String q){
    if (q==null || q.isBlank()) return List.of();
    return List.of(q.trim().split("\\s+"));
  }
  private static String escapeLike(String term){
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
  private static String like(String token){ return "%" + escapeLike(token) + "%"; }
  private static int clampLimit(int limit){ return Math.min(Math.max(limit, 1), 50); }
  private static final String ESC = " ESCAPE '\\'";

  /** FR-SRC-02: filter posts by sector tag id (the `sector` param was
   *  previously accepted by the controller and ignored). */
  public Page<PostHit> searchPosts(Principals.UnilinkUserPrincipal p, String q, Long sectorId, String cursor, int limit){
    List<String> toks = tokens(q);
    if (toks.isEmpty()) return new Page<>(List.of(), null, 0);
    limit = clampLimit(limit);
    // The visibility predicate is the same one feed uses: NATIONAL is
    // open; UNIVERSITY/DEPARTMENT need the caller's university (and
    // department); DEPARTMENT_HUB needs the caller's catalog.
    StringBuilder where = new StringBuilder("WHERE p.status='ACTIVE' AND (p.visibility='NATIONAL'")
      .append(" OR (p.scope_university_id=? AND p.visibility IN ('UNIVERSITY','DEPARTMENT'))")
      .append(" OR (p.visibility='DEPARTMENT' AND p.scope_department_id=?)")
      .append(" OR (p.visibility='DEPARTMENT_HUB' AND p.scope_catalog_id=?))");
    List<Object> args = new ArrayList<>(Arrays.asList(p.universityId(), p.departmentId(), p.catalogId()));
    for (String t : toks){
      String l = like(t);
      where.append(" AND (p.title LIKE ?").append(ESC).append(" OR p.body LIKE ?").append(ESC)
        .append(" OR ed.location LIKE ?").append(ESC).append(" OR ed.organizer_name LIKE ?").append(ESC).append(")");
      args.addAll(List.of(l, l, l, l));
    }
    if (sectorId!=null){
      where.append(" AND EXISTS (SELECT 1 FROM post_sector_tags pst WHERE pst.post_id=p.id AND pst.sector_tag_id=?)");
      args.add(sectorId);
    }
    // D-201: expired / archived events never surface (liveEventsOnly).
    where.append(" AND NOT EXISTS (SELECT 1 FROM event_details e2 WHERE e2.post_id=p.id")
      .append(" AND (e2.deadline <= ? OR e2.archived_at IS NOT NULL))");
    args.add(LocalDateTime.now());
    String from = "FROM posts p LEFT JOIN event_details ed ON ed.post_id = p.id ";
    long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    String pageWhere = where.toString();
    if (cursor!=null && !cursor.isBlank()){
      try { pageWhere += " AND p.id < ?"; pageArgs.add(Long.parseLong(cursor.trim())); }
      catch (NumberFormatException e){ throw new Exceptions.BadRequest("INVALID_CURSOR","Cursor must be a post id"); }
    }
    List<PostHit> rows = jdbc.query(
      "SELECT p.id, p.title, p.body, p.visibility, p.type, ed.location, ed.organizer_name " + from + pageWhere
        + " ORDER BY p.id DESC LIMIT " + (limit + 1),
      (rs,i)-> new PostHit(rs.getLong("id"), rs.getString("title"), rs.getString("body"), rs.getString("visibility"),
        rs.getString("type"), rs.getString("location"), rs.getString("organizer_name")),
      pageArgs.toArray());
    String next = null;
    if (rows.size() > limit){ rows = rows.subList(0, limit); next = String.valueOf(rows.get(rows.size()-1).id()); }
    return new Page<>(rows, next, total);
  }

  public Page<UserHit> searchUsers(String q, String cursor, int limit){
    List<String> toks = tokens(q);
    if (toks.isEmpty()) return new Page<>(List.of(), null, 0);
    limit = clampLimit(limit);
    StringBuilder where = new StringBuilder("WHERE state='VERIFIED'");
    List<Object> args = new ArrayList<>();
    for (String t : toks){
      String l = like(t);
      where.append(" AND (full_name LIKE ?").append(ESC)
        .append(" OR EXISTS (SELECT 1 FROM user_skills us JOIN skills s ON s.id=us.skill_id")
        .append(" WHERE us.user_id=users.id AND s.name LIKE ?").append(ESC).append("))");
      args.addAll(List.of(l, l));
    }
    long total = jdbc.queryForObject("SELECT COUNT(*) FROM users " + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    String pageWhere = where.toString();
    if (cursor!=null && !cursor.isBlank()){
      try { pageWhere += " AND id < ?"; pageArgs.add(Long.parseLong(cursor.trim())); }
      catch (NumberFormatException e){ throw new Exceptions.BadRequest("INVALID_CURSOR","Cursor must be a user id"); }
    }
    List<UserHit> rows = jdbc.query(
      "SELECT id, full_name, university_id, department_id FROM users " + pageWhere
        + " ORDER BY id DESC LIMIT " + (limit + 1),
      (rs,i)-> new UserHit(rs.getLong("id"), rs.getString("full_name"),
        rs.getObject("university_id")==null ? null : rs.getLong("university_id"),
        rs.getObject("department_id")==null ? null : rs.getLong("department_id"), List.of()),
      pageArgs.toArray());
    String next = null;
    if (rows.size() > limit){ rows = rows.subList(0, limit); next = String.valueOf(rows.get(rows.size()-1).id()); }
    if (!rows.isEmpty()){
      Map<Long, List<String>> matched = matchedSkills(rows.stream().map(UserHit::id).toList(), toks);
      rows = rows.stream().map(h -> new UserHit(h.id(), h.fullName(), h.universityId(), h.departmentId(),
        matched.getOrDefault(h.id(), List.of()))).toList();
    }
    return new Page<>(rows, next, total);
  }
  /** Skill names on each hit that matched any query token (D-201). */
  private Map<Long, List<String>> matchedSkills(List<Long> userIds, List<String> toks){
    String in = String.join(",", Collections.nCopies(userIds.size(), "?"));
    StringBuilder sql = new StringBuilder(
      "SELECT us.user_id, s.name FROM user_skills us JOIN skills s ON s.id=us.skill_id WHERE us.user_id IN (" + in + ") AND (");
    List<Object> args = new ArrayList<>(userIds);
    for (int i=0; i<toks.size(); i++){ if (i>0) sql.append(" OR "); sql.append("s.name LIKE ?").append(ESC); args.add(like(toks.get(i))); }
    sql.append(") ORDER BY s.name");
    Map<Long, List<String>> out = new LinkedHashMap<>();
    jdbc.query(sql.toString(), (rs,i)->{
      out.computeIfAbsent(rs.getLong("user_id"), k -> new ArrayList<>()).add(rs.getString("name"));
      return null;
    }, args.toArray());
    out.replaceAll((k,v) -> v.stream().distinct().toList());
    return out;
  }
  /** Active catalogue universities whose name or short name matches every token. */
  public List<UniversityHit> searchUniversities(String q){
    List<String> toks = tokens(q);
    if (toks.isEmpty()) return List.of();
    StringBuilder sql = new StringBuilder("SELECT id, name, short_name, category FROM universities WHERE active = TRUE");
    List<Object> args = new ArrayList<>();
    for (String t : toks){
      String l = like(t);
      sql.append(" AND (name LIKE ?").append(ESC).append(" OR short_name LIKE ?").append(ESC).append(")");
      args.addAll(List.of(l, l));
    }
    sql.append(" ORDER BY id DESC LIMIT 50");
    return jdbc.query(sql.toString(),
      (rs,i)-> new UniversityHit(rs.getLong("id"), rs.getString("name"), rs.getString("short_name"), rs.getString("category")),
      args.toArray());
  }
  /** Active catalogue departments whose name matches every token. */
  public List<DepartmentHit> searchDepartments(String q){
    List<String> toks = tokens(q);
    if (toks.isEmpty()) return List.of();
    StringBuilder sql = new StringBuilder("SELECT id, name, university_id FROM departments WHERE active = TRUE");
    List<Object> args = new ArrayList<>();
    for (String t : toks){
      sql.append(" AND name LIKE ?").append(ESC);
      args.add(like(t));
    }
    sql.append(" ORDER BY id DESC LIMIT 50");
    return jdbc.query(sql.toString(),
      (rs,i)-> new DepartmentHit(rs.getLong("id"), rs.getString("name"), rs.getLong("university_id")),
      args.toArray());
  }
}
