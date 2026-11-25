package com.unilink.search.api;
import com.unilink.common.Exceptions;
import com.unilink.search.SearchService; import com.unilink.security.AccessPolicy;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/search")
public class SearchController {
  private final SearchService search; private final AccessPolicy policy;
  public SearchController(SearchService s, AccessPolicy p){ search=s; policy=p; }
  /** D-201: unknown type is a 400 (INVALID_TYPE), not a silent {}.
   *  Keyset pagination: `cursor` pages posts, `usersCursor` pages
   *  people; `limit` is clamped to 1..50 server-side. `total` is the
   *  count of matches across the sections included by `type`. */
  @GetMapping public Map<String,Object> search(Authentication a, @RequestParam String q, @RequestParam(defaultValue="all") String type,
      @RequestParam(required=false) Long sector,
      @RequestParam(required=false) String cursor, @RequestParam(required=false) String usersCursor,
      @RequestParam(defaultValue="20") int limit){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    if (!List.of("all","posts","users").contains(type))
      throw new Exceptions.BadRequest("INVALID_TYPE","Unknown search type");
    Map<String,Object> m = new LinkedHashMap<>();
    long total = 0;
    if ("all".equals(type) || "posts".equals(type)){
      var posts = search.searchPosts(p, q, sector, cursor, limit);
      m.put("posts", posts.items()); m.put("postsNextCursor", posts.nextCursor()); m.put("postsTotal", posts.total());
      total += posts.total();
    }
    if ("all".equals(type) || "users".equals(type)){
      var users = search.searchUsers(q, usersCursor, limit);
      m.put("users", users.items()); m.put("usersNextCursor", users.nextCursor()); m.put("usersTotal", users.total());
      total += users.total();
    }
    if ("all".equals(type)){
      var unis = search.searchUniversities(q);
      var depts = search.searchDepartments(q);
      m.put("universities", unis); m.put("departments", depts);
      total += unis.size() + depts.size();
    }
    m.put("total", total);
    return m;
  }
}
