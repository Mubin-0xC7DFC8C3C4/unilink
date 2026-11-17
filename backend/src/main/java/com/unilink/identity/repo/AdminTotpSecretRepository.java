package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface AdminTotpSecretRepository extends JpaRepository<AdminTotpSecret, Long> {
  // PK is the admin id itself (AdminTotpSecret.id maps to admin_id), so the
  // inherited findById(id) is the lookup; a derived findByAdminId would
  // reference a property that does not exist and fail context startup.
}
