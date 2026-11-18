package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface MessageRepository extends JpaRepository<Message, Long> {
  List<Message> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);
  /** Audit Worker 2 (D-144): the messages preceding a reported one, newest
   *  first, for the D-033 moderation snapshot (target + up to 10 preceding). */
  List<Message> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long id, Pageable pageable);
  Optional<Message> findByRoomIdAndIdempotencyKey(Long roomId, String idempotencyKey);
  long countByRoomId(Long roomId);
}
