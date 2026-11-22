package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface FollowRepository extends JpaRepository<Follow, Long> {
  boolean existsByFollowerIdAndFollowedId(Long followerId, Long followedId);
  void deleteByFollowerIdAndFollowedId(Long followerId, Long followedId);
  long countByFollowedId(Long followedId);
  List<Follow> findByFollowedIdOrderByCreatedAtDesc(Long followedId, Pageable pageable);
  List<Follow> findByFollowerIdOrderByCreatedAtDesc(Long followerId, Pageable pageable);
  List<Follow> findByFollowerId(Long followerId);
  long countByFollowerId(Long followerId);
}
