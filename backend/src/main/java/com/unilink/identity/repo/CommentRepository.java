package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface CommentRepository extends JpaRepository<Comment, Long> {
  List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);
  /** Audit Worker 2 (D-152): count without loading every comment row.
   *  FeedService.toDto used findByPostId…().size(), i.e. a full comment
   *  load per post in every feed page. */
  long countByPostId(Long postId);
  Optional<Comment> findByIdAndAuthorId(Long id, Long authorId);
}
