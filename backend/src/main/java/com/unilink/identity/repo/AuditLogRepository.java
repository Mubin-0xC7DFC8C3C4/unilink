package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
  Optional<AuditLog> findTopByOrderByIdDesc();
  List<AuditLog> findByActorAdminIdOrderByCreatedAtDesc(Long actorAdminId, Pageable pageable);
  long countByActorAdminId(Long actorAdminId);
  /** D-204 (D): audit rows targeting one user, newest first, for the per-user activity view. */
  List<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId, Pageable pageable);
}
