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
}
