package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDateTime;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
  Optional<PasswordResetToken> findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(Long userId);
  /** Atomic failed-guess counter — same rollback problem as
   *  PhoneOtpRepository.incrementAttempts (D-103): the wrong-code
   *  exception rolls back the caller's transaction, so a plain
   *  setAttempts+save could never make the 5-attempt cap engage. */
  @Modifying
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Query("UPDATE PasswordResetToken p SET p.attempts = COALESCE(p.attempts, 0) + 1 WHERE p.id = :id")
  int incrementAttempts(@Param("id") Long id);
}
