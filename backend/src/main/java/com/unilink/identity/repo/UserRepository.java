package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);
  boolean existsByEmail(String email);
  boolean existsByUniversityIdAndStudentId(Long universityId, String studentId);
  List<User> findByState(String state);
  List<User> findByUniversityIdAndState(Long universityId, String state);
  long countByState(String state);
  long countByUniversityIdAndState(Long universityId, String state);
  List<User> findByUniversityId(Long universityId);
}
