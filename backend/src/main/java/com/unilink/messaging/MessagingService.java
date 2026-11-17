package com.unilink.messaging;
import com.unilink.common.Exceptions; import com.unilink.identity.domain.*;
import com.unilink.identity.event.DomainEvents; import com.unilink.identity.repo.*;
import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import com.unilink.storage.StorageGateway; import com.unilink.storage.UploadValidator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.*;
import java.util.*;
/** Persist-then-broadcast after commit (ARCH §7.3). */
@Service
public class MessagingService {
  public record RoomDto(Long id, String publicId, String type, String name, List<Long> memberIds, Boolean closed) {}
  /** I-9: `read` is true only when every OTHER member has read this message
   *  (their room_members.last_read_message_id >= this id). It is computed
   *  server-side from last_read_message_id; the frontend never invents it.
   *  `hasAttachment` is true when the message has a stored attachment. */
  public record MessageDto(Long id, String publicId, Long roomId, Long senderId, String body,
                           java.time.LocalDateTime createdAt, boolean read, boolean hasAttachment) {}
  public record AttachmentDto(Long id, String fileName, String contentType, Long sizeBytes,
                              java.time.LocalDateTime createdAt) {}
  public record AttachmentFile(String storageKey, String fileName, String contentType) {}
  public record CommunityContextDto(Long universityId, String universityName, Long departmentId,
                                    String departmentName, Long catalogId,
                                    String departmentRoomPublicId, String universityRoomPublicId) {}
  private final ChatRoomRepository rooms; private final RoomMemberRepository roomMembers; private final MessageRepository messages;
  private final MessageAttachmentRepository messageAttachments;
  private final UserBlockRepository blocks; private final UserRepository users;
  private final UniversityRepository universities; private final DepartmentRepository departments;
  private final AccessPolicy policy;
  private final ApplicationEventPublisher events; private final SimpMessagingTemplate broker;
  private final StorageGateway storage; private final UploadValidator uploads;
  public MessagingService(ChatRoomRepository rooms, RoomMemberRepository roomMembers, MessageRepository messages,
      MessageAttachmentRepository messageAttachments,
      UserBlockRepository blocks, UserRepository users,
      UniversityRepository universities, DepartmentRepository departments,
      AccessPolicy policy,
      ApplicationEventPublisher events, SimpMessagingTemplate broker,
      StorageGateway storage, UploadValidator uploads){
    this.rooms=rooms; this.roomMembers=roomMembers; this.messages=messages;
    this.messageAttachments=messageAttachments;
    this.blocks=blocks; this.users=users;
    this.universities=universities; this.departments=departments;
    this.policy=policy; this.events=events; this.broker=broker;
    this.storage=storage; this.uploads=uploads;
  }
  private RoomDto toDto(ChatRoom r){
    return new RoomDto(r.getId(), r.getPublicId(), r.getType(), r.getName(),
      roomMembers.findByRoomId(r.getId()).stream().map(RoomMember::getUserId).toList(),
      r.getClosedAt()!=null);
  }
  @Transactional
  public RoomDto getOrCreateDepartmentRoom(Principals.UnilinkUserPrincipal p){
    policy.requireVerified(p);
    ChatRoom room = rooms.findByTypeAndUniversityIdAndDepartmentId("DEPARTMENT", p.universityId(), p.departmentId()).stream().findFirst()
      .orElseGet(() -> {
        ChatRoom r = new ChatRoom(); r.setPublicId(UUID.randomUUID().toString()); r.setType("DEPARTMENT");
        r.setName("Department"); r.setUniversityId(p.universityId()); r.setDepartmentId(p.departmentId()); r.setCreatedBy(p.userId());
        r = rooms.save(r);
        RoomMember m = new RoomMember(); m.setRoomId(r.getId()); m.setUserId(p.userId()); m.setRole("MEMBER"); roomMembers.save(m);
        return r;
      });
    return toDto(room);
  }
  @Transactional
  public RoomDto getOrCreateUniversityRoom(Principals.UnilinkUserPrincipal p){
    policy.requireVerified(p);
    ChatRoom room = rooms.findByTypeAndUniversityIdAndDepartmentId("UNIVERSITY", p.universityId(), null).stream().findFirst()
      .orElseGet(() -> {
        ChatRoom r = new ChatRoom(); r.setPublicId(UUID.randomUUID().toString()); r.setType("UNIVERSITY");
        r.setName("University"); r.setUniversityId(p.universityId()); r.setCreatedBy(p.userId());
        r = rooms.save(r);
        RoomMember m = new RoomMember(); m.setRoomId(r.getId()); m.setUserId(p.userId()); m.setRole("MEMBER"); roomMembers.save(m);
        return r;
      });
    return toDto(room);
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
  /** Temporary group chat for team formation (FR-MSG-04). The creator is the
   *  room ADMIN; invited members must be verified students. */
  @Transactional
  public RoomDto createTempRoom(Principals.UnilinkUserPrincipal p, String name, List<Long> memberIds){
    policy.requireVerified(p);
    if (name==null || name.isBlank()) throw new Exceptions.BadRequest("NAME_REQUIRED","A temporary room needs a name");
    ChatRoom r = new ChatRoom(); r.setPublicId(UUID.randomUUID().toString()); r.setType("TEMP");
    r.setName(name.trim()); r.setUniversityId(p.universityId()); r.setCreatedBy(p.userId());
    r = rooms.save(r);
    RoomMember admin = new RoomMember(); admin.setRoomId(r.getId()); admin.setUserId(p.userId()); admin.setRole("ADMIN"); roomMembers.save(admin);
    if (memberIds!=null) for (Long uid : new LinkedHashSet<>(memberIds)){
      if (uid.equals(p.userId())) continue;
      User u = users.findById(uid).orElseThrow(() -> new Exceptions.NotFound("User"));
      if (!"VERIFIED".equals(u.getState())) throw new Exceptions.BadRequest("USER_NOT_VERIFIED","Invited user is not verified");
      RoomMember m = new RoomMember(); m.setRoomId(r.getId()); m.setUserId(uid); m.setRole("MEMBER"); roomMembers.save(m);
    }
    return toDto(r);
  }
  /** Invite one verified student to a temporary room (FR-MSG-04). */
  @Transactional
  public RoomDto inviteToTempRoom(Principals.UnilinkUserPrincipal p, String roomPublicId, Long userId){
    ChatRoom room = requireOpenTempRoom(p, roomPublicId);
    requireRoomAdmin(p, room);
    User u = users.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    if (!"VERIFIED".equals(u.getState())) throw new Exceptions.BadRequest("USER_NOT_VERIFIED","Invited user is not verified");
    if (!roomMembers.existsByRoomIdAndUserId(room.getId(), userId)){
      RoomMember m = new RoomMember(); m.setRoomId(room.getId()); m.setUserId(userId); m.setRole("MEMBER"); roomMembers.save(m);
    }
    return toDto(room);
  }
  /** Group admin removes a member from a temporary room (FR-MSG-08). */
  @Transactional
  public RoomDto removeRoomMember(Principals.UnilinkUserPrincipal p, String roomPublicId, Long userId){
    ChatRoom room = requireOpenTempRoom(p, roomPublicId);
    requireRoomAdmin(p, room);
    if (room.getCreatedBy()!=null && room.getCreatedBy().equals(userId))
      throw new Exceptions.BadRequest("CANNOT_REMOVE_CREATOR","The room creator cannot be removed; close the room instead");
    roomMembers.findByRoomIdAndUserId(room.getId(), userId)
      .orElseThrow(() -> new Exceptions.NotFound("Member"));
    roomMembers.deleteByRoomIdAndUserId(room.getId(), userId);
    return toDto(room);
  }
  /** Group admin closes a temporary room (FR-MSG-08). History stays readable;
   *  new messages are rejected (see sendMessage). */
  @Transactional
  public RoomDto closeRoom(Principals.UnilinkUserPrincipal p, String roomPublicId){
    ChatRoom room = rooms.findByPublicId(roomPublicId).orElseThrow(() -> new Exceptions.NotFound("Room"));
    roomMembers.findByRoomIdAndUserId(room.getId(), p.userId()).orElseThrow(() -> new Exceptions.NotFound("Room"));
    if (!"TEMP".equals(room.getType())) throw new Exceptions.BadRequest("NOT_TEMPORARY","Only temporary rooms can be closed");
    requireRoomAdmin(p, room);
    if (room.getClosedAt()==null){ room.setClosedAt(java.time.LocalDateTime.now()); rooms.save(room); }
    return toDto(room);
  }
  private ChatRoom requireOpenTempRoom(Principals.UnilinkUserPrincipal p, String roomPublicId){
    ChatRoom room = rooms.findByPublicId(roomPublicId).orElseThrow(() -> new Exceptions.NotFound("Room"));
    roomMembers.findByRoomIdAndUserId(room.getId(), p.userId()).orElseThrow(() -> new Exceptions.NotFound("Room"));
    if (!"TEMP".equals(room.getType())) throw new Exceptions.BadRequest("NOT_TEMPORARY","Only temporary rooms support this");
    if (room.getClosedAt()!=null) throw new Exceptions.BadRequest("ROOM_CLOSED","This room is closed");
    return room;
  }
  private void requireRoomAdmin(Principals.UnilinkUserPrincipal p, ChatRoom room){
    RoomMember me = roomMembers.findByRoomIdAndUserId(room.getId(), p.userId())
      .orElseThrow(() -> new Exceptions.NotFound("Room"));
    boolean creator = room.getCreatedBy()!=null && room.getCreatedBy().equals(p.userId());
    if (!creator && !"ADMIN".equals(me.getRole())) throw new Exceptions.Forbidden("Only the room admin can do this");
  }
  /** Community context (FR-COM-01): the student's profile-derived spaces.
   *  Calling it ensures the department and university rooms exist, i.e. the
   *  verified student is placed in them on first use. */
  @Transactional
  public CommunityContextDto communityContext(Principals.UnilinkUserPrincipal p){
    policy.requireVerified(p);
    RoomDto dept = getOrCreateDepartmentRoom(p);
    RoomDto uni = getOrCreateUniversityRoom(p);
    String uniName = p.universityId()==null ? null
      : universities.findById(p.universityId()).map(University::getName).orElse(null);
    String deptName = p.departmentId()==null ? null
      : departments.findById(p.departmentId()).map(Department::getName).orElse(null);
    return new CommunityContextDto(p.universityId(), uniName, p.departmentId(), deptName, p.catalogId(),
      dept.publicId(), uni.publicId());
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
    roomMembers.findByRoomIdAndUserId(room.getId(), p.userId()).orElseThrow(() -> new Exceptions.NotFound("Room"));
    if (room.getClosedAt()!=null) throw new Exceptions.BadRequest("ROOM_CLOSED","This room is closed");
    if (idempotencyKey!=null && !idempotencyKey.isBlank()){
      var existing = messages.findByRoomIdAndIdempotencyKey(room.getId(), idempotencyKey);
      if (existing.isPresent()) return toMsgDto(existing.get(), room);
    }
    Message m = new Message(); m.setPublicId(UUID.randomUUID().toString()); m.setRoomId(room.getId());
    m.setSenderId(p.userId()); m.setBody(body); m.setIdempotencyKey(idempotencyKey);
    m = messages.save(m);
    MessageDto dto = toMsgDto(m, room);
    events.publishEvent(new DomainEvents.MessageSent(m.getId(), room.getId(), p.userId()));
    return dto;
  }
  /** Send a message with one file attached (FR-MSG-07, ≤10 MB image/PDF). */
  @Transactional
  public MessageDto sendMessageWithAttachment(Principals.UnilinkUserPrincipal p, String roomPublicId,
      String body, String fileName, String contentType, byte[] bytes){
    if (bytes==null || bytes.length==0) throw new Exceptions.BadRequest("INVALID_FILE","File is empty or unreadable");
    String sniffed = uploads.sniffContentType(bytes, contentType);
    uploads.validateAttachment(bytes, UploadValidator.ATTACHMENT_MAX);
    String text = (body==null || body.isBlank()) ? fileName : body;
    MessageDto dto = sendMessage(p, roomPublicId, text, UUID.randomUUID().toString());
    MessageAttachment a = new MessageAttachment();
    a.setMessageId(dto.id());
    a.setStorageKey(storage.store("message-attachments", fileName, sniffed, bytes));
    a.setFileName(fileName); a.setContentType(sniffed); a.setSizeBytes((long) bytes.length);
    messageAttachments.save(a);
    return new MessageDto(dto.id(), dto.publicId(), dto.roomId(), dto.senderId(), dto.body(), dto.createdAt(), dto.read(), true);
  }
  @Transactional(readOnly=true)
  public List<AttachmentDto> listMessageAttachments(Principals.UnilinkUserPrincipal p, String roomPublicId, Long messageId){
    ChatRoom room = rooms.findByPublicId(roomPublicId).orElseThrow(() -> new Exceptions.NotFound("Room"));
    roomMembers.findByRoomIdAndUserId(room.getId(), p.userId()).orElseThrow(() -> new Exceptions.NotFound("Room"));
    Message m = messages.findById(messageId).orElseThrow(() -> new Exceptions.NotFound("Message"));
    if (!room.getId().equals(m.getRoomId())) throw new Exceptions.NotFound("Message");
    return messageAttachments.findByMessageId(messageId).stream()
      .map(a -> new AttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), null)).toList();
  }
  @Transactional(readOnly=true)
  public AttachmentFile openMessageAttachment(Principals.UnilinkUserPrincipal p, String roomPublicId, Long messageId, Long attachmentId){
    ChatRoom room = rooms.findByPublicId(roomPublicId).orElseThrow(() -> new Exceptions.NotFound("Room"));
    roomMembers.findByRoomIdAndUserId(room.getId(), p.userId()).orElseThrow(() -> new Exceptions.NotFound("Room"));
    MessageAttachment a = messageAttachments.findById(attachmentId).orElseThrow(() -> new Exceptions.NotFound("Attachment"));
    if (!messageId.equals(a.getMessageId())) throw new Exceptions.NotFound("Attachment");
    Message m = messages.findById(messageId).orElseThrow(() -> new Exceptions.NotFound("Message"));
    if (!room.getId().equals(m.getRoomId())) throw new Exceptions.NotFound("Attachment");
    if (!storage.exists(a.getStorageKey())) throw new Exceptions.NotFound("File");
    return new AttachmentFile(a.getStorageKey(), a.getFileName(), a.getContentType());
  }
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void broadcastAfterCommit(DomainEvents.MessageSent e){
    messages.findById(e.messageId()).ifPresent(m ->
      rooms.findById(m.getRoomId()).ifPresent(room ->
        broker.convertAndSend("/topic/room."+room.getPublicId(), toMsgDto(m, room))));
  }
  /** I-9: `read` = every other member's last_read_message_id >= this message's
   *  id. A room with no other members (or where nobody else has read) is false. */
  private MessageDto toMsgDto(Message m, ChatRoom room){
    List<RoomMember> members = roomMembers.findByRoomId(room.getId());
    boolean read = isReadByOthers(m, members);
    boolean hasAttachment = !messageAttachments.findByMessageId(m.getId()).isEmpty();
    return new MessageDto(m.getId(), m.getPublicId(), m.getRoomId(), m.getSenderId(), m.getBody(), m.getCreatedAt(), read, hasAttachment);
  }
  private boolean isReadByOthers(Message m, List<RoomMember> members){
    List<RoomMember> others = members.stream().filter(rm -> !rm.getUserId().equals(m.getSenderId())).toList();
    return !others.isEmpty() && others.stream()
      .allMatch(rm -> rm.getLastReadMessageId()!=null && rm.getLastReadMessageId() >= m.getId());
  }
  @Transactional(readOnly=true)
  public List<MessageDto> listMessages(Principals.UnilinkUserPrincipal p, String roomPublicId, int limit){
    ChatRoom room = rooms.findByPublicId(roomPublicId).orElseThrow(() -> new Exceptions.NotFound("Room"));
    roomMembers.findByRoomIdAndUserId(room.getId(), p.userId()).orElseThrow(() -> new Exceptions.NotFound("Room"));
    List<Message> found = messages.findByRoomIdOrderByIdDesc(room.getId(), PageRequest.of(0, Math.min(Math.max(limit,1),50)));
    List<RoomMember> members = roomMembers.findByRoomId(room.getId());
    Set<Long> withAttachments = found.isEmpty() ? Set.of()
      : messageAttachments.findByMessageIdIn(found.stream().map(Message::getId).toList()).stream()
          .map(MessageAttachment::getMessageId).collect(java.util.stream.Collectors.toSet());
    return found.stream().map(m -> new MessageDto(m.getId(), m.getPublicId(), m.getRoomId(), m.getSenderId(),
      m.getBody(), m.getCreatedAt(), isReadByOthers(m, members), withAttachments.contains(m.getId()))).toList();
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
