package com.unilink.identity.service;
import com.unilink.admin.AdminDtos.VerificationRequestDto;
import com.unilink.audit.AuditService; import com.unilink.common.Exceptions;
import com.unilink.identity.domain.*; import com.unilink.identity.event.DomainEvents;
import com.unilink.identity.repo.*; import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import org.springframework.context.ApplicationEventPublisher; import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime; import java.util.Map;
/** Facade (ARCH §8): one call, one transaction — decision + audit + notification event. */
@Service
public class VerificationFacade {
  private final VerificationRequestRepository requests; private final UserRepository users;
  private final VerificationAttemptRepository attempts; private final AuditService audit;
  private final AccessPolicy policy; private final ApplicationEventPublisher events;
  private final SessionService sessions;
  public VerificationFacade(VerificationRequestRepository requests, UserRepository users,
      VerificationAttemptRepository attempts, AuditService audit, AccessPolicy policy,
      ApplicationEventPublisher events, SessionService sessions){
    this.requests=requests; this.users=users; this.attempts=attempts; this.audit=audit;
    this.policy=policy; this.events=events; this.sessions=sessions;
  }
  @Transactional
  public VerificationRequestDto approve(Long requestId, Principals.AdminPrincipal admin, String ip){
    VerificationRequest req = requests.findById(requestId).orElseThrow(() -> new Exceptions.NotFound("Verification request"));
    if (!"PENDING_REVIEW".equals(req.getStatus())) throw new Exceptions.Conflict("ALREADY_DECIDED","This request was already decided");
    User u = users.findById(req.getUserId()).orElseThrow(() -> new Exceptions.NotFound("User"));
    policy.requireModeratorScopeOr404(admin, u.getUniversityId());
    req.setStatus("APPROVED"); req.setDecidedAt(LocalDateTime.now()); req.setDecidedBy(admin.adminId());
    requests.save(req);
    u.setState(AccountState.PENDING_REVIEW.transitionTo(AccountState.VERIFIED).name());
    users.save(u);
    VerificationAttempt att = new VerificationAttempt();
    att.setUserId(u.getId()); att.setVerificationRequestId(req.getId()); att.setOutcome("APPROVED"); attempts.save(att);
    audit.write(admin.adminId(), "VERIFICATION_APPROVE", "USER", u.getId(), null, Map.of("requestId", req.getId()), ip);
    events.publishEvent(new DomainEvents.StudentVerified(u.getId()));
    return VerificationRequestDto.from(req);
  }
  @Transactional
  public VerificationRequestDto reject(Long requestId, Principals.AdminPrincipal admin, String reason, String ip){
    if (reason==null || reason.isBlank()) throw new Exceptions.BadRequest("REASON_REQUIRED","A rejection reason is mandatory (FR-ADM-03)");
    VerificationRequest req = requests.findById(requestId).orElseThrow(() -> new Exceptions.NotFound("Verification request"));
    if (!"PENDING_REVIEW".equals(req.getStatus())) throw new Exceptions.Conflict("ALREADY_DECIDED","This request was already decided");
    User u = users.findById(req.getUserId()).orElseThrow(() -> new Exceptions.NotFound("User"));
    policy.requireModeratorScopeOr404(admin, u.getUniversityId());
    req.setStatus("REJECTED"); req.setReason(reason); req.setDecidedAt(LocalDateTime.now()); req.setDecidedBy(admin.adminId());
    requests.save(req);
    u.setState(AccountState.PENDING_REVIEW.transitionTo(AccountState.REJECTED).name());
    users.save(u);
    VerificationAttempt att = new VerificationAttempt();
    att.setUserId(u.getId()); att.setVerificationRequestId(req.getId()); att.setOutcome("REJECTED"); att.setReason(reason); attempts.save(att);
    audit.write(admin.adminId(), "VERIFICATION_REJECT", "USER", u.getId(), reason, Map.of("requestId", req.getId()), ip);
    events.publishEvent(new DomainEvents.StudentRejected(u.getId(), reason));
    return VerificationRequestDto.from(req);
  }
}
