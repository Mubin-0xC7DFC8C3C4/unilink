package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
  Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);
  List<RoomMember> findByUserId(Long userId);
  List<RoomMember> findByRoomId(Long roomId);
  boolean existsByRoomIdAndUserId(Long roomId, Long userId);
  void deleteByRoomIdAndUserId(Long roomId, Long userId);
}
