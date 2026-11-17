package com.unilink.notification;
import com.unilink.identity.event.DomainEvents;
import com.unilink.identity.repo.UserRepository;
import com.unilink.integration.email.EmailProvider;
import org.slf4j.*; import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component
public class NotificationEventListener {
  private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
  private final NotificationService notifications; private final UserRepository users; private final EmailProvider email;
  public NotificationEventListener(NotificationService n, UserRepository users, EmailProvider email){
    notifications=n; this.users=users; this.email=email;
  }
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onVerified(DomainEvents.StudentVerified e){
    notifications.notify(e.userId(),"VERIFICATION_DECISION","USER",e.userId(),"Verification approved","Your UniLink account is now verified.");
    // I-11 (FR-AUTH-09): the decision email, not just the in-app notice.
    // Security emails cannot be disabled (D-029c), so no preference check.
    sendDecisionEmail(e.userId(), "Your UniLink verification was approved",
      "Good news — your UniLink account is now verified. You can sign in and use the full student network.");
  }
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRejected(DomainEvents.StudentRejected e){
    notifications.notify(e.userId(),"VERIFICATION_DECISION","USER",e.userId(),"Verification rejected","Your verification was rejected: "+e.reason());
    sendDecisionEmail(e.userId(), "Your UniLink verification was rejected",
      "Your UniLink verification was rejected: "+e.reason()+". You can resubmit your application from the Pending Review page.");
  }
  /** Email delivery must never fail the decision: a provider failure is
   *  logged, and the in-app notification above already reached the user. */
  private void sendDecisionEmail(Long userId, String subject, String body){
    users.findById(userId).ifPresent(u -> {
      if (u.getEmail()==null || u.getEmail().isBlank()) return;
      try { email.sendSimple(u.getEmail(), subject, body); }
      catch (Exception ex){ log.warn("Verification-decision email to {} failed", u.getEmail(), ex); }
    });
  }
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAutoHidden(DomainEvents.PostAutoHidden e){
    notifications.notify(e.authorId(),"REPORT_AUTO_HIDE",e.targetType(),e.postId(),"Your content was hidden","Your content received multiple reports and was automatically hidden pending review.");
  }
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTeamAccepted(DomainEvents.TeamJoinAccepted e){
    notifications.notify(e.userId(),"TEAM_JOIN_ACCEPTED","TEAM",e.teamId(),"Team request accepted","You have joined the team.");
  }
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMessage(DomainEvents.MessageSent e){
    // Offline digest is handled by the messaging service; this hook keeps the event observed.
  }
}
