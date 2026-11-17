package com.unilink.search.api;
import com.unilink.search.SearchService; import com.unilink.security.AccessPolicy;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/search")
public class SearchController {
  private final SearchService search; private final AccessPolicy policy;
  public SearchController(SearchService s, AccessPolicy p){ search=s; policy=p; }
  @GetMapping public Map<String,Object> search(Authentication a, @RequestParam String q, @RequestParam(defaultValue="all") String type,
      @RequestParam(required=false) Long sector){
    var p = policy.requireStudent(a.getPrincipal());
    Map<String,Object> m = new LinkedHashMap<>();
    if ("all".equals(type) || "posts".equals(type)) m.put("posts", search.searchPosts(p, q, sector));
    if ("all".equals(type) || "users".equals(type)) m.put("users", search.searchUsers(q));
    return m;
  }
}
