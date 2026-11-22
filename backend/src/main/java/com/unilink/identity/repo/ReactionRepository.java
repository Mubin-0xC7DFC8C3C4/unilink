package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
  Optional<Reaction> findByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);
  long countByTargetTypeAndTargetId(String targetType, Long targetId);
  void deleteByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);
  List<Reaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
