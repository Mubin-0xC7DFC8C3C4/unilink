package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface AdminRecoveryCodeRepository extends JpaRepository<AdminRecoveryCode, Long> {
  List<AdminRecoveryCode> findByAdminIdAndUsedFalse(Long adminId);
  void deleteByAdminId(Long adminId);
}
