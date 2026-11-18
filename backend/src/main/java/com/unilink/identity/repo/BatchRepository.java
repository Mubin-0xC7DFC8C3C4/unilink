package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface BatchRepository extends JpaRepository<Batch, Long> {
  List<Batch> findByUniversityIdAndDepartmentIdAndActiveTrue(Long universityId, Long departmentId);
  /** Audit Worker 2 (D-153): batches for a university without a department
   *  filter. PublicCatalogService used to load the whole batches table
   *  (findAll) and filter in memory. */
  List<Batch> findByUniversityIdAndActiveTrue(Long universityId);
}
