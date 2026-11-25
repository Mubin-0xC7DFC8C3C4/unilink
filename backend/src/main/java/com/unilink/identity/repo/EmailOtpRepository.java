package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {
  Optional<EmailOtp> findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(Long userId);
  List<EmailOtp> findByUserIdAndUsedFalse(Long userId);
  /** Atomic failed-guess counter (D-103 pattern, as PhoneOtpRepository):
   *  a plain setAttempts + save inside the verify transaction is rolled
   *  back with the OTP_INVALID exception, so the 5-attempt cap could
   *  never engage. This UPDATE runs in its own transaction. */
  @Modifying
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Query("UPDATE EmailOtp e SET e.attempts = COALESCE(e.attempts, 0) + 1 WHERE e.id = :id")
  int incrementAttempts(@Param("id") Long id);
}
