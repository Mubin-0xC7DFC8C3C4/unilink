package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
  boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);
  void deleteByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);
  List<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId);
  long countByTargetTypeAndTargetId(String targetType, Long targetId);
}
