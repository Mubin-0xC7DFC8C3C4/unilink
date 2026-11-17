package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface VerificationAttemptRepository extends JpaRepository<VerificationAttempt, Long> {
  long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
  long countByUserId(Long userId);
  List<VerificationAttempt> findByUserIdOrderByCreatedAtDesc(Long userId);
}
