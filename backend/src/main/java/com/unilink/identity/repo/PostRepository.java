package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
  Optional<Post> findByIdAndAuthorId(Long id, Long authorId);
  List<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId, Pageable pageable);
  /** Audit Worker 2 (D-155): all posts by an author, for the account-deletion
   *  attachment sweep (their attachment rows/files must not orphan). */
  List<Post> findByAuthorId(Long authorId);
  long countByStatus(String status);
}
