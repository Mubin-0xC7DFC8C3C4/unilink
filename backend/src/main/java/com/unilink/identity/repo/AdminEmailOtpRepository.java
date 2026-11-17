package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDateTime;

public interface AdminEmailOtpRepository extends JpaRepository<AdminEmailOtp, Long> {
  Optional<AdminEmailOtp> findTopByAdminIdAndUsedFalseOrderByCreatedAtDesc(Long adminId);
  /** Atomic failed-guess counter (D-117; same D-103 bug class as the phone
   *  and password-reset OTPs). A plain `setAttempts(n+1)` + save inside the
   *  @Transactional verify method is rolled back together with the
   *  OTP_INVALID exception, so the 5-attempt cap could never engage. This
   *  UPDATE runs in its own transaction and increments atomically in the
   *  database, so a wrong guess is counted exactly once even when the
   *  caller's transaction rolls back. */
  @Modifying
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Query("UPDATE AdminEmailOtp o SET o.attempts = COALESCE(o.attempts, 0) + 1 WHERE o.id = :id")
  int incrementAttempts(@Param("id") Long id);
}
