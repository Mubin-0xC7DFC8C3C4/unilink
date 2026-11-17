package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
  boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
  void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
  List<UserBlock> findByBlockerId(Long blockerId);
}
