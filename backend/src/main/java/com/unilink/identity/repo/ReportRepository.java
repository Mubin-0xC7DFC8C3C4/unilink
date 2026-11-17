package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface ReportRepository extends JpaRepository<Report, Long> {
  boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(Long reporterId, String targetType, Long targetId, String status);
  @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT r.reporterId) FROM Report r WHERE r.targetType=:targetType AND r.targetId=:targetId AND r.status=:status")
  long countDistinctReporters(@org.springframework.data.repository.query.Param("targetType") String targetType, @org.springframework.data.repository.query.Param("targetId") Long targetId, @org.springframework.data.repository.query.Param("status") String status);
  List<Report> findByStatusOrderByCreatedAtAsc(String status);
  List<Report> findByTargetTypeAndTargetId(String targetType, Long targetId);
}
