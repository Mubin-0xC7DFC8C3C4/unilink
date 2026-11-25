package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, Long> {
  Optional<VerificationRequest> findTopByUserIdOrderBySubmittedAtDesc(Long userId);
  /** I-18: account deletion collects storage keys from ALL of a user's
   *  verification requests, not only the latest. */
  List<VerificationRequest> findByUserIdOrderBySubmittedAtDesc(Long userId);
  List<VerificationRequest> findByStatusOrderBySubmittedAtAsc(String status);
  List<VerificationRequest> findAllByOrderBySubmittedAtAsc();
  long countByStatus(String status);
  /** Registration-token lookup (D-175): the newest request carrying this
   *  token hash. Callers must also check the token expiry themselves. */
  Optional<VerificationRequest> findTopByVerificationTokenHashOrderBySubmittedAtDesc(String verificationTokenHash);
}
