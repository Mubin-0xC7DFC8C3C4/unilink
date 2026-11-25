package com.unilink.connection;
import com.unilink.common.Exceptions; import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*; import com.unilink.notification.NotificationService;
import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
/** Mutual connections (D-200). Modelled on TeamService.decideRequest():
 *  every check that can fail runs before the row is transitioned, and a
 *  decided request can never be re-decided (ALREADY_DECIDED).
 *  Follow stays as-is (one-sided, instant) and the two graphs are
 *  distinct — accepting a connection does not auto-follow.
 *  Notification keys include the request id: NotificationService keys on
 *  type:targetType:targetId:recipientId and never deletes the row, so a
 *  bare user id as target would swallow a re-request notification after
 *  a decision (the same trap as re-follow after unfollow). */
@Service
public class ConnectionService {
  private final ConnectionRequestRepository requests; private final UserRepository users;
  private final UniversityRepository universities; private final DepartmentRepository departments;
  private final AccessPolicy policy; private final NotificationService notifications;
  public ConnectionService(ConnectionRequestRepository r, UserRepository u, UniversityRepository unis,
      DepartmentRepository depts, AccessPolicy p, NotificationService n){
    requests=r; users=u; universities=unis; departments=depts; policy=p; notifications=n;
  }
  private String nameOf(Long userId){
    return users.findById(userId).map(User::getFullName).orElse("A student");
  }
  private Optional<ConnectionRequest> pendingEitherWay(Long a, Long b){
    return requests.findByRequesterIdAndAddresseeIdAndStatus(a, b, "PENDING")
      .or(() -> requests.findByRequesterIdAndAddresseeIdAndStatus(b, a, "PENDING"));
  }
  private boolean connectedEitherWay(Long a, Long b){
    return requests.existsByRequesterIdAndAddresseeIdAndStatus(a, b, "ACCEPTED")
      || requests.existsByRequesterIdAndAddresseeIdAndStatus(b, a, "ACCEPTED");
  }
  @Transactional
  public ConnectionDtos.ConnectionRequestDto requestConnection(Principals.UnilinkUserPrincipal p, Long addresseeId, String message){
    policy.requireVerified(p);
    if (addresseeId==null) throw new Exceptions.NotFound("User");
    if (p.userId().equals(addresseeId)) throw new Exceptions.BadRequest("CANNOT_CONNECT_SELF","You cannot connect with yourself");
    users.findById(addresseeId).orElseThrow(() -> new Exceptions.NotFound("User"));
    if (connectedEitherWay(p.userId(), addresseeId))
      throw new Exceptions.Conflict("ALREADY_CONNECTED","You are already connected");
    if (pendingEitherWay(p.userId(), addresseeId).isPresent())
      throw new Exceptions.Conflict("REQUEST_PENDING","A connection request is already pending");
    ConnectionRequest r = new ConnectionRequest();
    r.setRequesterId(p.userId()); r.setAddresseeId(addresseeId); r.setStatus("PENDING"); r.setMessage(message);
    r = requests.save(r);
    notifications.notify(addresseeId, "CONNECTION_REQUEST", "CONNECTION_REQUEST", r.getId(),
      "New connection request", nameOf(p.userId()) + " wants to connect with you");
    return ConnectionDtos.ConnectionRequestDto.from(r, nameOf(p.userId()), nameOf(addresseeId));
  }
  @Transactional(readOnly=true)
  public List<ConnectionDtos.ConnectionRequestDto> incoming(Principals.UnilinkUserPrincipal p){
    policy.requireVerified(p);
    return requests.findByAddresseeIdAndStatusOrderByCreatedAtDesc(p.userId(), "PENDING").stream()
      .map(r -> ConnectionDtos.ConnectionRequestDto.from(r, nameOf(r.getRequesterId()), nameOf(r.getAddresseeId()))).toList();
  }
  @Transactional(readOnly=true)
  public List<ConnectionDtos.ConnectionRequestDto> outgoing(Principals.UnilinkUserPrincipal p){
    policy.requireVerified(p);
    return requests.findByRequesterIdAndStatusOrderByCreatedAtDesc(p.userId(), "PENDING").stream()
      .map(r -> ConnectionDtos.ConnectionRequestDto.from(r, nameOf(r.getRequesterId()), nameOf(r.getAddresseeId()))).toList();
  }
  @Transactional(readOnly=true)
  public List<ConnectionDtos.ConnectionDto> connections(Principals.UnilinkUserPrincipal p){
    policy.requireVerified(p);
    return requests.findAcceptedForUser(p.userId()).stream().map(r -> {
      Long otherId = r.getRequesterId().equals(p.userId()) ? r.getAddresseeId() : r.getRequesterId();
      User other = users.findById(otherId).orElse(null);
      String uni = other==null || other.getUniversityId()==null ? null
        : universities.findById(other.getUniversityId()).map(University::getName).orElse(null);
      String dept = other==null ? null
        : (other.getDepartmentLabel()!=null ? other.getDepartmentLabel()
          : (other.getDepartmentId()==null ? null : departments.findById(other.getDepartmentId()).map(Department::getName).orElse(null)));
      return new ConnectionDtos.ConnectionDto(r.getId(), otherId, other==null ? "Student" : other.getFullName(),
        uni, dept, r.getDecidedAt());
    }).toList();
  }
  /** Live partner ids, for the CONNECTIONS feed level and Top affinity. */
  @Transactional(readOnly=true)
  public Set<Long> connectedUserIds(Long userId){
    return new HashSet<>(requests.findAcceptedPartnerIds(userId));
  }
  @Transactional(readOnly=true)
  public ConnectionDtos.ConnectionStatusDto status(Principals.UnilinkUserPrincipal p, Long userId){
    policy.requireVerified(p);
    if (userId==null) throw new Exceptions.NotFound("User");
    if (p.userId().equals(userId)) return new ConnectionDtos.ConnectionStatusDto("SELF", null);
    users.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    if (connectedEitherWay(p.userId(), userId)) return new ConnectionDtos.ConnectionStatusDto("CONNECTED", null);
    Optional<ConnectionRequest> out = requests.findByRequesterIdAndAddresseeIdAndStatus(p.userId(), userId, "PENDING");
    if (out.isPresent()) return new ConnectionDtos.ConnectionStatusDto("PENDING_OUTGOING", out.get().getId());
    Optional<ConnectionRequest> in = requests.findByRequesterIdAndAddresseeIdAndStatus(userId, p.userId(), "PENDING");
    if (in.isPresent()) return new ConnectionDtos.ConnectionStatusDto("PENDING_INCOMING", in.get().getId());
    return new ConnectionDtos.ConnectionStatusDto("NONE", null);
  }
  @Transactional
  public ConnectionDtos.ConnectionRequestDto decide(Principals.UnilinkUserPrincipal p, Long requestId, boolean accept){
    policy.requireVerified(p);
    ConnectionRequest r = requests.findById(requestId).orElseThrow(() -> new Exceptions.NotFound("Connection request"));
    if (!r.getAddresseeId().equals(p.userId())) throw new Exceptions.NotFound("Connection request");
    if (!"PENDING".equals(r.getStatus())) throw new Exceptions.Conflict("ALREADY_DECIDED","Request already decided");
    r.setStatus(accept ? "ACCEPTED" : "REJECTED"); r.setDecidedAt(LocalDateTime.now()); requests.save(r);
    if (accept)
      notifications.notify(r.getRequesterId(), "CONNECTION_ACCEPTED", "CONNECTION_REQUEST", r.getId(),
        "Connection accepted", nameOf(p.userId()) + " accepted your connection request");
    else
      notifications.notify(r.getRequesterId(), "CONNECTION_REJECTED", "CONNECTION_REQUEST", r.getId(),
        "Connection request declined", nameOf(p.userId()) + " declined your connection request");
    return ConnectionDtos.ConnectionRequestDto.from(r, nameOf(r.getRequesterId()), nameOf(r.getAddresseeId()));
  }
  @Transactional
  public ConnectionDtos.ConnectionRequestDto cancel(Principals.UnilinkUserPrincipal p, Long requestId){
    policy.requireVerified(p);
    ConnectionRequest r = requests.findById(requestId).orElseThrow(() -> new Exceptions.NotFound("Connection request"));
    if (!r.getRequesterId().equals(p.userId())) throw new Exceptions.NotFound("Connection request");
    if (!"PENDING".equals(r.getStatus())) throw new Exceptions.Conflict("ALREADY_DECIDED","Request already decided");
    r.setStatus("CANCELLED"); r.setDecidedAt(LocalDateTime.now()); requests.save(r);
    return ConnectionDtos.ConnectionRequestDto.from(r, nameOf(r.getRequesterId()), nameOf(r.getAddresseeId()));
  }
  /** Removal transitions the ACCEPTED row to REMOVED (audit row kept);
   *  the next feed fetch no longer returns their posts. Re-request is
   *  allowed afterwards. No notification is sent for a removal (D-200). */
  @Transactional
  public void remove(Principals.UnilinkUserPrincipal p, Long userId){
    policy.requireVerified(p);
    if (userId==null) throw new Exceptions.NotFound("User");
    ConnectionRequest r = requests.findByRequesterIdAndAddresseeIdAndStatus(p.userId(), userId, "ACCEPTED")
      .or(() -> requests.findByRequesterIdAndAddresseeIdAndStatus(userId, p.userId(), "ACCEPTED"))
      .orElseThrow(() -> new Exceptions.NotFound("Connection"));
    r.setStatus("REMOVED"); r.setDecidedAt(LocalDateTime.now()); requests.save(r);
  }
}
