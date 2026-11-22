package com.unilink.community.api;
import com.unilink.admin.AdminService; import com.unilink.common.PageResponse;
import com.unilink.community.FeedService; import com.unilink.identity.dto.CatalogDtos.AnnouncementDto;
import com.unilink.security.AccessPolicy;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
public class FeedController {
  private final FeedService feed; private final AdminService admin; private final AccessPolicy policy;
  public FeedController(FeedService f, AdminService a, AccessPolicy p){ feed=f; admin=a; policy=p; }
  @GetMapping("/api/feed")
  public PageResponse<FeedService.PostDto> feed(Authentication a,
      @RequestParam(defaultValue="DEPARTMENT") String level,
      @RequestParam(required=false) String cursor,
      @RequestParam(defaultValue="20") int limit,
      @RequestParam(required=false) Long sector,
      @RequestParam(defaultValue="recent") String sort){
    var p = policy.requireStudent(a.getPrincipal());
    if (!List.of("DEPARTMENT","UNIVERSITY","HUB","NATIONAL").contains(level))
      throw new com.unilink.common.Exceptions.BadRequest("INVALID_LEVEL","Unknown feed level");
    if (!List.of("recent","top").contains(sort))
      throw new com.unilink.common.Exceptions.BadRequest("INVALID_SORT","Unknown sort");
    return feed.feed(p, level, cursor, limit, sector, sort);
  }
  @GetMapping("/api/announcements")
  public List<AnnouncementDto> announcements(Authentication a){
    var p = policy.requireStudent(a.getPrincipal());
    return admin.activeAnnouncements(p);
  }
}
