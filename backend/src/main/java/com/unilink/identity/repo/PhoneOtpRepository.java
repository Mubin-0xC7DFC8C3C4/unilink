package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDateTime;

public interface PhoneOtpRepository extends JpaRepository<PhoneOtp, Long> {
  Optional<PhoneOtp> findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(Long userId);
  List<PhoneOtp> findByUserId(Long userId);
  /** Atomic failed-guess counter (D-103). A plain `setAttempts(n+1)` +
   *  save inside the verify transaction is rolled back together with the
   *  OTP_INVALID exception, so the 5-attempt cap could never engage.
   *  This UPDATE runs in its own transaction and increments atomically
   *  in the database, so a wrong guess is counted exactly once even when
   *  the caller's transaction rolls back, and concurrent wrong guesses
   *  cannot lose updates. */
  @Modifying
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Query("UPDATE PhoneOtp p SET p.attempts = COALESCE(p.attempts, 0) + 1 WHERE p.id = :id")
  int incrementAttempts(@Param("id") Long id);
}
