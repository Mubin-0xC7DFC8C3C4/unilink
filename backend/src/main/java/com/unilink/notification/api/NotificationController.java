package com.unilink.notification.api;
import com.unilink.notification.NotificationDtos; import com.unilink.notification.NotificationService;
import com.unilink.security.AccessPolicy;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService svc; private final AccessPolicy policy;
  public NotificationController(NotificationService s, AccessPolicy p){ svc=s; policy=p; }
  @GetMapping public List<NotificationDtos.NotificationDto> list(Authentication a, @RequestParam(defaultValue="20") int limit){
    var p = policy.requireStudent(a.getPrincipal()); return svc.list(p.userId(), limit);
  }
  @GetMapping("/unread-count") public Map<String,Long> unread(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return Map.of("unread", svc.unreadCount(p.userId()));
  }
  @PostMapping("/{id}/read") public Map<String,String> read(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); svc.markRead(p.userId(), id); return Map.of("status","ok");
  }
  @PostMapping("/read-all") public Map<String,String> readAll(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); svc.markAllRead(p.userId()); return Map.of("status","ok");
  }
  public record PrefBody(String type, boolean emailEnabled, boolean inAppEnabled) {}
  @PostMapping("/preferences") public Map<String,String> pref(Authentication a, @RequestBody PrefBody r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    svc.setPreference(p.userId(), r.type(), r.emailEnabled(), r.inAppEnabled()); return Map.of("status","saved");
  }
}
