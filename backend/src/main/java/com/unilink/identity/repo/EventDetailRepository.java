package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface EventDetailRepository extends JpaRepository<EventDetail, Long> {
  // PK is the post id itself (EventDetail.id maps to post_id), so the
  // inherited findById(id) is the lookup; a derived findByPostId would
  // reference a property that does not exist and fail context startup.
  List<EventDetail> findByArchivedAtIsNullAndDeadlineBefore(LocalDateTime now);
}
