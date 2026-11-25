package com.unilink.identity.repo;
import com.unilink.identity.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ConnectionRequestRepository extends JpaRepository<ConnectionRequest, Long> {
  List<ConnectionRequest> findByAddresseeIdAndStatusOrderByCreatedAtDesc(Long addresseeId, String status);
  List<ConnectionRequest> findByRequesterIdAndStatusOrderByCreatedAtDesc(Long requesterId, String status);
  boolean existsByRequesterIdAndAddresseeIdAndStatus(Long requesterId, Long addresseeId, String status);
  Optional<ConnectionRequest> findByRequesterIdAndAddresseeIdAndStatus(Long requesterId, Long addresseeId, String status);
  /** The other side of every ACCEPTED row involving this user — the
   *  connection graph, read live so removal takes effect immediately. */
  @Query("SELECT CASE WHEN r.requesterId = :userId THEN r.addresseeId ELSE r.requesterId END "
      + "FROM ConnectionRequest r WHERE r.status = 'ACCEPTED' AND (r.requesterId = :userId OR r.addresseeId = :userId)")
  List<Long> findAcceptedPartnerIds(@Param("userId") Long userId);
  @Query("SELECT r FROM ConnectionRequest r WHERE r.status = 'ACCEPTED' AND (r.requesterId = :userId OR r.addresseeId = :userId) ORDER BY r.createdAt DESC")
  List<ConnectionRequest> findAcceptedForUser(@Param("userId") Long userId);
}
