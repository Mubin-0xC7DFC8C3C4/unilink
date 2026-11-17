package com.unilink.messaging.api;
import com.unilink.messaging.MessagingService;
import com.unilink.security.AccessPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class MessagingRestController {
  private final MessagingService svc; private final AccessPolicy policy;
  MessagingRestController(MessagingService s, AccessPolicy p){ svc=s; policy=p; }
  public record SendBody(String body, String idempotencyKey) {}
  @GetMapping("/rooms") public List<MessagingService.RoomDto> rooms(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return svc.myRooms(p);
  }
  @GetMapping("/rooms/search") public List<MessagingService.RoomDto> search(Authentication a, @RequestParam String q){
    var p = policy.requireStudent(a.getPrincipal()); return svc.searchRooms(p, q);
  }
  @PostMapping("/rooms/department") public MessagingService.RoomDto deptRoom(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return svc.getOrCreateDepartmentRoom(p);
  }
  @PostMapping("/rooms/university") public MessagingService.RoomDto uniRoom(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return svc.getOrCreateUniversityRoom(p);
  }
  @PostMapping("/rooms/private/{userId}") public MessagingService.RoomDto privateRoom(Authentication a, @PathVariable Long userId){
    var p = policy.requireStudent(a.getPrincipal()); return svc.createPrivateRoom(p, userId);
  }
  @GetMapping("/rooms/{publicId}/messages") public List<MessagingService.MessageDto> messages(Authentication a, @PathVariable String publicId, @RequestParam(defaultValue="50") int limit){
    var p = policy.requireStudent(a.getPrincipal()); return svc.listMessages(p, publicId, limit);
  }
  @PostMapping("/rooms/{publicId}/messages") public MessagingService.MessageDto send(Authentication a, @PathVariable String publicId, @RequestBody SendBody b){
    var p = policy.requireStudent(a.getPrincipal()); return svc.sendMessage(p, publicId, b.body(), b.idempotencyKey());
  }
  @PostMapping("/rooms/{publicId}/read") public Map<String,String> read(Authentication a, @PathVariable String publicId, @RequestParam Long messageId){
    var p = policy.requireStudent(a.getPrincipal()); svc.markRead(p, publicId, messageId); return Map.of("status","ok");
  }
  @PostMapping("/blocks/{userId}") public Map<String,String> block(Authentication a, @PathVariable Long userId){
    var p = policy.requireStudent(a.getPrincipal()); svc.block(p.userId(), userId); return Map.of("status","blocked");
  }
  @DeleteMapping("/blocks/{userId}") public Map<String,String> unblock(Authentication a, @PathVariable Long userId){
    var p = policy.requireStudent(a.getPrincipal()); svc.unblock(p.userId(), userId); return Map.of("status","unblocked");
  }
  @PostMapping("/rooms/{publicId}/mute") public Map<String,String> mute(Authentication a, @PathVariable String publicId, @RequestParam(defaultValue="60") int minutes){
    var p = policy.requireStudent(a.getPrincipal()); svc.mute(p.userId(), publicId, minutes); return Map.of("status","muted");
  }
}
