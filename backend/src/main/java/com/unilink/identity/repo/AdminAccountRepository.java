package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
  Optional<AdminAccount> findByEmail(String email);
  List<AdminAccount> findByRoleAndUniversityId(String role, Long universityId);
  List<AdminAccount> findByRole(String role);
  long countByRole(String role);
}
