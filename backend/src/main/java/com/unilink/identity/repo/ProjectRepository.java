package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface ProjectRepository extends JpaRepository<Project, Long> {
  List<Project> findByUserId(Long userId);
  Optional<Project> findByIdAndUserId(Long id, Long userId);
}
