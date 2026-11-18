package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.LocalDateTime;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
  List<Attachment> findByOwnerTypeAndOwnerId(String ownerType, Long ownerId);
  long countByOwnerTypeAndOwnerId(String ownerType, Long ownerId);
  /** Audit Worker 2 (D-155): deleting a user's posts must also remove their
   *  attachment rows (the table has no FK to posts — owner is polymorphic),
   *  otherwise they orphan when the post cascade-deletes. */
  void deleteByOwnerTypeAndOwnerId(String ownerType, Long ownerId);
}
