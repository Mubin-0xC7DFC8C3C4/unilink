package com.unilink.notification;
import com.unilink.identity.event.DomainEvents;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component
public class NotificationEventListener {
  private final NotificationService notifications;
  public NotificationEventListener(NotificationService n){ notifications=n; }
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onVerified(DomainEvents.StudentVerified e){
    notifications.notify(e.userId(),"VERIFICATION_DECISION","USER",e.userId(),"Verification approved","Your UniLink account is now verified.");
  }
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRejected(DomainEvents.StudentRejected e){
    notifications.notify(e.userId(),"VERIFICATION_DECISION","USER",e.userId(),"Verification rejected","Your verification was rejected: "+e.reason());
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
