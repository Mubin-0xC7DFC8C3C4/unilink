package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface AdminAlertRepository extends JpaRepository<AdminAlert, Long> {
  List<AdminAlert> findByAdminIdOrderByCreatedAtDesc(Long adminId);
  boolean existsByTypeAndTargetTypeAndTargetIdAndAdminId(String type, String targetType, Long targetId, Long adminId);
}
