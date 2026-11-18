package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);
  long countByRecipientIdAndReadFlagFalse(Long recipientId);
  boolean existsByIdempotencyKey(String key);
  /** Audit Worker 2 (D-147): mark-all-read must mark ALL unread rows.
   *  The service used to page the first 50 and save those, so unread rows
   *  past 50 stayed unread after "mark all read". */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query("UPDATE Notification n SET n.readFlag = true WHERE n.recipientId = :userId AND n.readFlag = false")
  int markAllRead(@org.springframework.data.repository.query.Param("userId") Long userId);
}
