package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface MessageRepository extends JpaRepository<Message, Long> {
  List<Message> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);
  Optional<Message> findByRoomIdAndIdempotencyKey(Long roomId, String idempotencyKey);
  long countByRoomId(Long roomId);
}
