package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
  long countByEmailAndSuccessFalseAndCreatedAtAfter(String email, LocalDateTime since);
  /** D-204 (B): newest-first pages for the Login Activity admin page. */
  org.springframework.data.domain.Page<LoginAttempt> findAllByOrderByCreatedAtDesc(Pageable pageable);
  /** D-204 (B): moderator view — only attempts for their university's accounts. */
  org.springframework.data.domain.Page<LoginAttempt> findByEmailInOrderByCreatedAtDesc(java.util.List<String> emails, Pageable pageable);
  /** D-204 (D): one user's attempts, newest first, for the per-user activity view. */
  java.util.List<LoginAttempt> findByEmailOrderByCreatedAtDesc(String email, Pageable pageable);
}
