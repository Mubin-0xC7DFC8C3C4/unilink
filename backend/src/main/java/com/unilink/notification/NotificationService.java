package com.unilink.notification;
import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import org.springframework.data.domain.PageRequest; import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** In-app notifications with idempotency key (type,target,recipient) — ARCH §9. */
@Service
public class NotificationService {
  private final NotificationRepository notifications; private final NotificationPreferenceRepository prefs;
  public NotificationService(NotificationRepository n, NotificationPreferenceRepository p){ notifications=n; prefs=p; }
  @Transactional
  public void notify(Long recipientId, String type, String targetType, Long targetId, String title, String body){
    String key = type+":"+targetType+":"+targetId+":"+recipientId;
    if (notifications.existsByIdempotencyKey(key)) return;
    Optional<NotificationPreference> pref = prefs.findByUserIdAndType(recipientId, type);
    if (pref.isPresent() && Boolean.FALSE.equals(pref.get().getInAppEnabled())) return;
    Notification n = new Notification();
    n.setRecipientId(recipientId); n.setType(type); n.setTargetType(targetType); n.setTargetId(targetId);
    n.setTitle(title); n.setBody(body); n.setIdempotencyKey(key); n.setReadFlag(false);
    notifications.save(n);
  }
  @Transactional(readOnly=true)
  public List<Notification> list(Long userId, int limit){ return notifications.findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit,50))); }
  @Transactional(readOnly=true)
  public long unreadCount(Long userId){ return notifications.countByRecipientIdAndReadFlagFalse(userId); }
  @Transactional
  public void markRead(Long userId, Long notificationId){
    notifications.findById(notificationId).filter(n->n.getRecipientId().equals(userId)).ifPresent(n->{ n.setReadFlag(true); notifications.save(n); });
  }
  @Transactional
  public void markAllRead(Long userId){ list(userId,50).forEach(n->{ n.setReadFlag(true); notifications.save(n); }); }
  @Transactional
  public void setPreference(Long userId, String type, boolean email, boolean inApp){
    NotificationPreference p = prefs.findByUserIdAndType(userId,type).orElseGet(NotificationPreference::new);
    p.setUserId(userId); p.setType(type); p.setEmailEnabled(email); p.setInAppEnabled(inApp); prefs.save(p);
  }
}
