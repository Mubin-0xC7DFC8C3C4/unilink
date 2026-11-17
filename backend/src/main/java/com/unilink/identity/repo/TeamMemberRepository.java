package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
  List<TeamMember> findByTeamId(Long teamId);
  List<TeamMember> findByUserId(Long userId);
  boolean existsByEventPostIdAndUserId(Long eventPostId, Long userId);
  long countByTeamId(Long teamId);
  Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);
}
