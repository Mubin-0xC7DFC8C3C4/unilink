package com.unilink.messaging;
import com.unilink.common.Exceptions; import com.unilink.identity.domain.*;
import com.unilink.identity.event.DomainEvents; import com.unilink.identity.repo.*;
import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.*;
import java.util.*;
/** Persist-then-broadcast after commit (ARCH §7.3). */
@Service
public class MessagingService {
  public record RoomDto(Long id, String publicId, String type, String name, List<Long> memberIds) {}
  public record MessageDto(Long id, String publicId, Long roomId, Long senderId, String body, java.time.LocalDateTime createdAt) {}
  private final ChatRoomRepository rooms; private final RoomMemberRepository roomMembers; private final MessageRepository messages;
  private final UserBlockRepository blocks; private final UserRepository users; private final AccessPolicy policy;
  private final ApplicationEventPublisher events; private final SimpMessagingTemplate broker;
  public MessagingService(ChatRoomRepository rooms, RoomMemberRepository roomMembers, MessageRepository messages,
      UserBlockRepository blocks, UserRepository users, AccessPolicy policy,
      ApplicationEventPublisher events, SimpMessagingTemplate broker){
    this.rooms=rooms; this.roomMembers=roomMembers; this.messages=messages; this.blocks=blocks; this.users=users;
    this.policy=policy; this.events=events; this.broker=broker;
  }
  private RoomDto toDto(ChatRoom r){
    return new RoomDto(r.getId(), r.getPublicId(), r.getType(), r.getName(),
      roomMembers.findByRoomId(r.getId()).stream().map(RoomMember::getUserId).toList());
  }
  @Transactional
  public ChatRoom getOrCreateDepartmentRoom(Principals.UnilinkUserPrincipal p){
    policy.requireVerified(p);
    return rooms.findByTypeAndUniversityIdAndDepartmentId("DEPARTMENT", p.universityId(), p.departmentId()).stream().findFirst()
      .orElseGet(() -> {
        ChatRoom r = new ChatRoom(); r.setPublicId(UUID.randomUUID().toString()); r.setType("DEPARTMENT");
        r.setName("Department"); r.setUniversityId(p.universityId()); r.setDepartmentId(p.departmentId()); r.setCreatedBy(p.userId());
        r = rooms.save(r);
        RoomMember m = new RoomMember(); m.setRoomId(r.getId()); m.setUserId(p.userId()); m.setRole("MEMBER"); roomMembers.save(m);
        return r;
      });
  }
  @Transactional
  public ChatRoom getOrCreateUniversityRoom(Principals.UnilinkUserPrincipal p){
    policy.requireVerified(p);
    return rooms.findByTypeAndUniversityIdAndDepartmentId("UNIVERSITY", p.universityId(), null).stream().findFirst()
      .orElseGet(() -> {
        ChatRoom r = new ChatRoom(); r.setPublicId(UUID.randomUUID().toString()); r.setType("UNIVERSITY");
        r.setName("University"); r.setUniversityId(p.universityId()); r.setCreatedBy(p.userId());
        r = rooms.save(r);
        RoomMember m = new RoomMember(); m.setRoomId(r.getId()); m.setUserId(p.userId()); m.setRole("MEMBER"); roomMembers.save(m);
        return r;
      });
  }
  @Transactional
  public RoomDto createPrivateRoom(Principals.UnilinkUserPrincipal p, Long otherUserId){
    policy.requireVerified(p);
    if (p.userId().equals(otherUserId)) throw new Exceptions.BadRequest("CANNOT_MESSAGE_SELF","Cannot message yourself");
    User other = users.findById(otherUserId).orElseThrow(() -> new Exceptions.NotFound("User"));
    if (!"VERIFIED".equals(other.getState())) throw new Exceptions.BadRequest("USER_NOT_VERIFIED","User is not verified");
    if (blocks.existsByBlockerIdAndBlockedId(otherUserId, p.userId()) || blocks.existsByBlockerIdAndBlockedId(p.userId(), otherUserId))
      throw new Exceptions.Forbidden("Messaging is blocked between these users");
    ChatRoom r = new ChatRoom(); r.setPublicId(UUID.randomUUID().toString()); r.setType("PRIVATE");
    r.setName(other.getFullName()); r.setCreatedBy(p.userId()); r = rooms.save(r);
    for (Long uid : List.of(p.userId(), otherUserId)){
      RoomMember m = new RoomMember(); m.setRoomId(r.getId()); m.setUserId(uid); m.setRole("MEMBER"); roomMembers.save(m);
    }
    return toDto(r);
  }
  @Transactional(readOnly=true)
  public List<RoomDto> myRooms(Principals.UnilinkUserPrincipal p){
    return roomMembers.findByUserId(p.userId()).stream()
      .map(rm -> rooms.findById(rm.getRoomId())).flatMap(Optional::stream).map(this::toDto).toList();
  }
  @Transactional(readOnly=true)
  public List<RoomDto> searchRooms(Principals.UnilinkUserPrincipal p, String q){
    // D-029: search conversation names and participants only, never message bodies.
    String needle = q==null?"":q.toLowerCase();
    return myRooms(p).stream().filter(r -> r.name()!=null && r.name().toLowerCase().contains(needle)).toList();
  }
  @Transactional
  public MessageDto sendMessage(Principals.UnilinkUserPrincipal p, String roomPublicId, String body, String idempotencyKey){
    policy.requireVerified(p);
    if (body==null || body.isBlank() || body.length()>5000) throw new Exceptions.BadRequest("INVALID_MESSAGE","Message must be 1–5000 characters");
    ChatRoom room = rooms.findByPublicId(roomPublicId).orElseThrow(() -> new Exceptions.NotFound("Room"));
    RoomMember me = roomMembers.findByRoomIdAndUserId(room.getId(), p.userId()).orElseThrow(() -> new Exceptions.NotFound("Room"));
    if (idempotencyKey!=null && !idempotencyKey.isBlank()){
      var existing = messages.findByRoomIdAndIdempotencyKey(room.getId(), idempotencyKey);
      if (existing.isPresent()) return toMsgDto(existing.get());
    }
    Message m = new Message(); m.setPublicId(UUID.randomUUID().toString()); m.setRoomId(room.getId());
    m.setSenderId(p.userId()); m.setBody(body); m.setIdempotencyKey(idempotencyKey);
    m = messages.save(m);
    MessageDto dto = toMsgDto(m);
    events.publishEvent(new DomainEvents.MessageSent(m.getId(), room.getId(), p.userId()));
    return dto;
  }
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void broadcastAfterCommit(DomainEvents.MessageSent e){
    messages.findById(e.messageId()).ifPresent(m ->
      rooms.findById(m.getRoomId()).ifPresent(room ->
        broker.convertAndSend("/topic/room."+room.getPublicId(), toMsgDto(m))));
  }
  private MessageDto toMsgDto(Message m){ return new MessageDto(m.getId(), m.getPublicId(), m.getRoomId(), m.getSenderId(), m.getBody(), m.getCreatedAt()); }
  @Transactional(readOnly=true)
  public List<MessageDto> listMessages(Principals.UnilinkUserPrincipal p, String roomPublicId, int limit){
    ChatRoom room = rooms.findByPublicId(roomPublicId).orElseThrow(() -> new Exceptions.NotFound("Room"));
    roomMembers.findByRoomIdAndUserId(room.getId(), p.userId()).orElseThrow(() -> new Exceptions.NotFound("Room"));
    return messages.findByRoomIdOrderByIdDesc(room.getId(), PageRequest.of(0, Math.min(Math.max(limit,1),50)))
      .stream().map(this::toMsgDto).toList();
  }
  @Transactional
  public void markRead(Principals.UnilinkUserPrincipal p, String roomPublicId, Long messageId){
    ChatRoom room = rooms.findByPublicId(roomPublicId).orElseThrow(() -> new Exceptions.NotFound("Room"));
    RoomMember me = roomMembers.findByRoomIdAndUserId(room.getId(), p.userId()).orElseThrow(() -> new Exceptions.NotFound("Room"));
    me.setLastReadMessageId(messageId); roomMembers.save(me);
  }
  @Transactional
  public void block(Long blockerId, Long blockedId){
    if (blockerId.equals(blockedId)) throw new Exceptions.BadRequest("CANNOT_BLOCK_SELF","Cannot block yourself");
    if (!blocks.existsByBlockerIdAndBlockedId(blockerId, blockedId)){
      UserBlock b=new UserBlock(); b.setBlockerId(blockerId); b.setBlockedId(blockedId); blocks.save(b);
    }
  }
  @Transactional public void unblock(Long blockerId, Long blockedId){ blocks.deleteByBlockerIdAndBlockedId(blockerId, blockedId); }
  @Transactional
  public void mute(Long userId, String roomPublicId, int minutes){
    ChatRoom room = rooms.findByPublicId(roomPublicId).orElseThrow(() -> new Exceptions.NotFound("Room"));
    RoomMember me = roomMembers.findByRoomIdAndUserId(room.getId(), userId).orElseThrow(() -> new Exceptions.NotFound("Room"));
    me.setMutedUntil(java.time.LocalDateTime.now().plusMinutes(minutes)); roomMembers.save(me);
  }
}
