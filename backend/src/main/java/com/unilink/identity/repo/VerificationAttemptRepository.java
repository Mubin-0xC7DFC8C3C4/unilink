package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface VerificationAttemptRepository extends JpaRepository<VerificationAttempt, Long> {
  long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
  /** I-18: resubmit counting counts RESUBMIT outcomes only — APPROVED /
   *  REJECTED decision rows must not consume the 3-per-24h resubmit cap. */
  long countByUserIdAndOutcomeAndCreatedAtAfter(Long userId, String outcome, LocalDateTime since);
  long countByUserId(Long userId);
  /** I-18: previousAttempts counts attempts on THIS request, not all
   *  attempts the user ever made. */
  long countByVerificationRequestId(Long verificationRequestId);
  List<VerificationAttempt> findByUserIdOrderByCreatedAtDesc(Long userId);
}
