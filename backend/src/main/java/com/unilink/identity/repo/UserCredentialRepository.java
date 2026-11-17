package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {
  // PK is the user id itself (UserCredential.id maps to user_id), so the
  // inherited findById(id) is the lookup; a derived findByUserId would
  // reference a property that does not exist and fail context startup.
}
