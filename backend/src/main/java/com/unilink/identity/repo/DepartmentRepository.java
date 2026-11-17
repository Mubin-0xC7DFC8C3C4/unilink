package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
  List<Department> findByUniversityIdAndActiveTrue(Long universityId);
  Optional<Department> findByUniversityIdAndCatalogId(Long universityId, Long catalogId);
}
