package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
  Optional<ChatRoom> findByPublicId(String publicId);
  List<ChatRoom> findByTypeAndUniversityIdAndDepartmentId(String type, Long universityId, Long departmentId);
  Optional<ChatRoom> findByTypeAndTeamId(String type, Long teamId);
}
