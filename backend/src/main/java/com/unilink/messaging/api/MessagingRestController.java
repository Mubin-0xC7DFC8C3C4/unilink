package com.unilink.messaging.api;
import com.unilink.messaging.MessagingService;
import com.unilink.security.AccessPolicy;
import com.unilink.storage.StorageGateway;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
@RestController @RequestMapping("/api")
public class MessagingRestController {
  private final MessagingService svc; private final AccessPolicy policy; private final StorageGateway storage;
  MessagingRestController(MessagingService s, AccessPolicy p, StorageGateway storage){ svc=s; policy=p; this.storage=storage; }
  public record SendBody(String body, String idempotencyKey) {}
  public record TempRoomBody(String name, List<Long> memberIds) {}
  public record InviteBody(Long userId) {}
  /** Community context (FR-COM-01): profile-derived spaces; ensures the
   *  department and university rooms exist for the verified student. */
  @GetMapping("/community/context") public MessagingService.CommunityContextDto context(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return svc.communityContext(p);
  }
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
  @PostMapping("/rooms/temp") public MessagingService.RoomDto tempRoom(Authentication a, @RequestBody TempRoomBody b){
    var p = policy.requireStudent(a.getPrincipal());
    return svc.createTempRoom(p, b.name(), b.memberIds());
  }
  @PostMapping("/rooms/{publicId}/invite") public MessagingService.RoomDto invite(Authentication a, @PathVariable String publicId, @RequestBody InviteBody b){
    var p = policy.requireStudent(a.getPrincipal()); return svc.inviteToTempRoom(p, publicId, b.userId());
  }
  @DeleteMapping("/rooms/{publicId}/members/{userId}") public MessagingService.RoomDto removeMember(Authentication a, @PathVariable String publicId, @PathVariable Long userId){
    var p = policy.requireStudent(a.getPrincipal()); return svc.removeRoomMember(p, publicId, userId);
  }
  @PostMapping("/rooms/{publicId}/close") public MessagingService.RoomDto close(Authentication a, @PathVariable String publicId){
    var p = policy.requireStudent(a.getPrincipal()); return svc.closeRoom(p, publicId);
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
  /** Send a message with one file attached (FR-MSG-07): multipart `file` plus
   *  optional `body` text. Returns the created message (hasAttachment=true). */
  @PostMapping(value="/rooms/{publicId}/attachments", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public MessagingService.MessageDto sendWithAttachment(Authentication a, @PathVariable String publicId,
      @RequestParam(value="body", required=false) String body, @RequestParam("file") MultipartFile file) throws IOException {
    var p = policy.requireStudent(a.getPrincipal());
    return svc.sendMessageWithAttachment(p, publicId, body, file.getOriginalFilename(), file.getContentType(), file.getBytes());
  }
  @GetMapping("/rooms/{publicId}/messages/{messageId}/attachments")
  public List<MessagingService.AttachmentDto> messageAttachments(Authentication a, @PathVariable String publicId, @PathVariable Long messageId){
    var p = policy.requireStudent(a.getPrincipal()); return svc.listMessageAttachments(p, publicId, messageId);
  }
  @GetMapping("/rooms/{publicId}/messages/{messageId}/attachments/{attachmentId}")
  public ResponseEntity<InputStreamResource> downloadMessageAttachment(Authentication a, @PathVariable String publicId,
      @PathVariable Long messageId, @PathVariable Long attachmentId){
    var p = policy.requireStudent(a.getPrincipal());
    var f = svc.openMessageAttachment(p, publicId, messageId, attachmentId);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType()))
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+f.fileName().replace("\"","")+"\"")
      .body(new InputStreamResource(storage.open(f.storageKey())));
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
