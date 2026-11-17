package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface TeamJoinRequestRepository extends JpaRepository<TeamJoinRequest, Long> {
  List<TeamJoinRequest> findByTeamIdAndStatusOrderByCreatedAtAsc(Long teamId, String status);
  Optional<TeamJoinRequest> findByTeamIdAndUserIdAndStatus(Long teamId, Long userId, String status);
  boolean existsByTeamIdAndUserIdAndStatus(Long teamId, Long userId, String status);
}
