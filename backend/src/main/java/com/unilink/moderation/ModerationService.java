package com.unilink.moderation;
import com.unilink.common.Exceptions; import com.unilink.identity.domain.*;
import com.unilink.identity.event.DomainEvents; import com.unilink.identity.repo.*;
import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime; import java.util.*;
/** Reporting + auto-hide (D-034/D-069) + appeals (FR-REP-03) + strikes (FR-REP-04). */
@Service
public class ModerationService {
  private final ReportRepository reports; private final PostRepository posts; private final CommentRepository comments;
  private final MessageRepository messages; private final ModerationSnapshotRepository snapshots;
  private final ModerationEventRepository modEvents; private final AppealRepository appeals;
  private final SystemSettingRepository settings; private final UserRepository users; private final AdminAlertRepository alerts;
  private final AdminAccountRepository admins; private final AccessPolicy policy; private final ApplicationEventPublisher events;
  public ModerationService(ReportRepository reports, PostRepository posts, CommentRepository comments,
      MessageRepository messages, ModerationSnapshotRepository snapshots, ModerationEventRepository modEvents,
      AppealRepository appeals, SystemSettingRepository settings, UserRepository users, AdminAlertRepository alerts,
      AdminAccountRepository admins,
      AccessPolicy policy, ApplicationEventPublisher events){
    this.reports=reports; this.posts=posts; this.comments=comments; this.messages=messages; this.snapshots=snapshots;
    this.modEvents=modEvents; this.appeals=appeals; this.settings=settings; this.users=users; this.alerts=alerts;
    this.admins=admins;
    this.policy=policy; this.events=events;
  }
  int threshold(){ return settings.findById("report.auto_hide_threshold").map(s->{ try{return Integer.parseInt(s.getSettingValue());}catch(Exception e){return 5;} }).orElse(5); }
  /** Chain of Responsibility: report eligibility (verified, not owner, one active report). */
  @Transactional
  public ModerationDtos.ReportDto report(Principals.UnilinkUserPrincipal p, String targetType, Long targetId, String reason){
    policy.requireVerified(p);
    if (reason==null || reason.isBlank()) throw new Exceptions.BadRequest("REASON_REQUIRED","A reason is required");
    Long ownerId = resolveOwner(targetType, targetId);
    if (ownerId!=null && ownerId.equals(p.userId())) throw new Exceptions.BadRequest("CANNOT_REPORT_OWN","You cannot report your own content");
    if (reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(p.userId(), targetType, targetId, "ACTIVE"))
      throw new Exceptions.Conflict("ALREADY_REPORTED","You have already reported this item");
    Report r = new Report(); r.setReporterId(p.userId()); r.setTargetType(targetType); r.setTargetId(targetId);
    r.setReason(reason); r.setStatus("ACTIVE"); r = reports.save(r);
    final Long reportId = r.getId();
    if ("MESSAGE".equals(targetType)){
      // D-033: snapshot this message + 10 preceding at report time; no general conversation access.
      messages.findById(targetId).ifPresent(msg -> {
        ModerationSnapshot s = new ModerationSnapshot(); s.setReportId(reportId); s.setMessageId(msg.getId());
        s.setSenderId(msg.getSenderId()); s.setBody(msg.getBody()); snapshots.save(s);
        // D-144: the 10 preceding messages in the same room were never
        // snapshotted — only the reported one — so a moderator reviewing
        // the report had no context. Newest first, at most 10.
        for (Message prev : messages.findByRoomIdAndIdLessThanOrderByIdDesc(msg.getRoomId(), msg.getId(), PageRequest.of(0, 10))){
          ModerationSnapshot ps = new ModerationSnapshot(); ps.setReportId(reportId); ps.setMessageId(prev.getId());
          ps.setSenderId(prev.getSenderId()); ps.setBody(prev.getBody()); snapshots.save(ps);
        }
      });
    }
    maybeAutoHide(r);
    return ModerationDtos.ReportDto.from(r);
  }
  private Long resolveOwner(String targetType, Long targetId){
    // D-140: a null targetType used to NPE the switch below (500) and a
    // null targetId used to NPE findById (500). Both are client errors.
    if (targetType==null || targetId==null)
      throw new Exceptions.BadRequest("INVALID_TARGET","Unknown report target");
    return switch (targetType){
      case "POST", "EVENT" -> posts.findById(targetId).map(Post::getAuthorId).orElse(null);
      case "COMMENT" -> comments.findById(targetId).map(Comment::getAuthorId).orElse(null);
      case "MESSAGE" -> messages.findById(targetId).map(Message::getSenderId).orElse(null);
      case "USER" -> targetId;
      default -> throw new Exceptions.BadRequest("INVALID_TARGET","Unknown report target");
    };
  }
  /** Auto-hide at N distinct verified reporters, exactly once (D-069). Profiles/messages are flagged, not hidden. */
  private void maybeAutoHide(Report r){
    if (!List.of("POST","EVENT","COMMENT").contains(r.getTargetType())) return;
    long distinct = reports.countDistinctReporters(r.getTargetType(), r.getTargetId(), "ACTIVE");
    if (distinct < threshold()) return;
    if ("POST".equals(r.getTargetType()) || "EVENT".equals(r.getTargetType())){
      posts.findById(r.getTargetId()).ifPresent(post -> {
        if ("ACTIVE".equals(post.getStatus())){
          post.setStatus("HIDDEN_AUTO");
          // D-142: stamp the hide time in the entity, not only in the DB.
          // updated_at is maintained by MySQL ON UPDATE, so the
          // persistence context still held null here and checkStrikes —
          // which runs in this same transaction — counted 0 hides for the
          // post just hidden; the alert then fired one hide late (on the
          // 4th auto-hide, not the 3rd). Setting it explicitly makes the
          // count correct in-transaction on any database.
          post.setUpdatedAt(LocalDateTime.now());
          posts.save(post); // @Version guards concurrent 5th/6th reports
          recordAutoHide(r.getTargetType(), r.getTargetId(), post.getAuthorId());
        }
      });
    } else if ("COMMENT".equals(r.getTargetType())){
      comments.findById(r.getTargetId()).ifPresent(c -> {
        if ("ACTIVE".equals(c.getStatus())){
          c.setStatus("HIDDEN_AUTO"); comments.save(c);
          recordAutoHide("COMMENT", r.getTargetId(), c.getAuthorId());
        }
      });
    }
  }
  private void recordAutoHide(String targetType, Long targetId, Long authorId){
    ModerationEvent ev = new ModerationEvent(); ev.setTargetType(targetType); ev.setTargetId(targetId);
    ev.setEventType("AUTO_HIDE"); modEvents.save(ev);
    events.publishEvent(new DomainEvents.PostAutoHidden(targetId, authorId, targetType));
    checkStrikes(authorId);
  }
  /** 3 auto-hides in 30 days flags the user for admin review (FR-REP-04).
   *  Counted per author: their posts currently HIDDEN_AUTO and updated inside the window. */
  private void checkStrikes(Long authorId){
    int window = settings.findById("report.strike_window_days").map(s->{ try{return Integer.parseInt(s.getSettingValue());}catch(Exception e){return 30;} }).orElse(30);
    LocalDateTime since = LocalDateTime.now().minusDays(window);
    long authorHides = posts.findByAuthorIdOrderByCreatedAtDesc(authorId, org.springframework.data.domain.PageRequest.of(0, 100)).stream()
      .filter(post -> "HIDDEN_AUTO".equals(post.getStatus()) && post.getUpdatedAt()!=null && post.getUpdatedAt().isAfter(since))
      .count();
    if (authorHides >= 3){
      // D-142: the alert used to be saved with adminId null, so
      // AdminService.adminAlerts (findByAdminId) never returned it to
      // anyone, and the uk_alert_once key (with a NULL admin_id) never
      // deduped it in MySQL — every strike check wrote another invisible
      // row. Address it like the overdue-verification alerts: the author's
      // university moderators, falling back to Platform Admins; the
      // exists-check keeps it to one alert per admin (the unique key is
      // the backstop, not the mechanism).
      User author = users.findById(authorId).orElse(null);
      List<AdminAccount> mods = (author==null || author.getUniversityId()==null) ? List.of()
        : admins.findByRoleAndUniversityId("UNIVERSITY_MODERATOR", author.getUniversityId());
      List<AdminAccount> targets = mods.isEmpty() ? admins.findByRole("PLATFORM_ADMIN") : mods;
      for (AdminAccount a : targets){
        if (alerts.existsByTypeAndTargetTypeAndTargetIdAndAdminId("STRIKE_FLAG", "USER", authorId, a.getId())) continue;
        AdminAlert al = new AdminAlert(); al.setAdminId(a.getId());
        al.setUniversityId(author==null ? null : author.getUniversityId());
        al.setType("STRIKE_FLAG"); al.setTargetType("USER"); al.setTargetId(authorId);
        al.setMessage("User has 3+ auto-hides in "+window+" days and needs review");
        alerts.save(al);
      }
    }
  }
  @Transactional
  public ModerationDtos.AppealDto appeal(Principals.UnilinkUserPrincipal p, String targetType, Long targetId, String reason){
    policy.requireVerified(p);
    if (reason==null || reason.isBlank()) throw new Exceptions.BadRequest("REASON_REQUIRED","Appeal reason is required");
    // I-15: ownership check — only the owner of a hidden item may appeal it.
    // Without this, any verified student could appeal (and restore) anyone
    // else's content. Unknown targets are 404; someone else's item is 403.
    Long ownerId = resolveOwner(targetType, targetId);
    if (ownerId==null) throw new Exceptions.NotFound("Appeal target");
    if (!ownerId.equals(p.userId())) throw new Exceptions.Forbidden("Only the owner can appeal this item");
    Appeal a = new Appeal(); a.setAppellantId(p.userId()); a.setTargetType(targetType); a.setTargetId(targetId);
    a.setReason(reason); a.setStatus("PENDING"); return ModerationDtos.AppealDto.from(appeals.save(a));
  }
  /** Public appeal via the single-use link sent on suspension (D-019). */
  @Transactional
  public ModerationDtos.AppealDto appealByToken(String token, String targetType, Long targetId, String reason){
    String hash = com.unilink.common.Hashing.sha256(token);
    Appeal a = appeals.findByTokenHash(hash).orElseThrow(() -> new Exceptions.NotFound("Appeal link"));
    // D-143: the link is single-use. Without the status check a submitted
    // appeal (PENDING) could be re-submitted with the same token until it
    // expired, overwriting the appellant's reason.
    if (!"AWAITING_SUBMISSION".equals(a.getStatus()))
      throw new Exceptions.BadRequest("TOKEN_USED","This appeal link has already been used");
    if (a.getTokenExpiresAt()!=null && a.getTokenExpiresAt().isBefore(LocalDateTime.now()))
      throw new Exceptions.BadRequest("TOKEN_EXPIRED","Appeal link has expired");
    a.setReason(reason); a.setStatus("PENDING"); return ModerationDtos.AppealDto.from(appeals.save(a));
  }
  /** Creates a single-use appeal link token for a suspended/banned user (D-019).
   *  Only the SHA-256 hash is stored; the plaintext token is returned once to the caller
   *  (emailed to the user) and never persisted. */
  public record AppealToken(Appeal appeal, String token) {}
  @Transactional
  public AppealToken createSuspensionAppealToken(Long userId){
    String token = com.unilink.identity.service.OtpService.newToken();
    Appeal a = new Appeal(); a.setAppellantId(userId); a.setTargetType("USER"); a.setTargetId(userId);
    a.setReason("Suspension appeal (submitted via emailed link)"); a.setStatus("AWAITING_SUBMISSION");
    a.setTokenHash(com.unilink.common.Hashing.sha256(token));
    a.setTokenExpiresAt(LocalDateTime.now().plusDays(7));
    return new AppealToken(appeals.save(a), token);
  }
}
