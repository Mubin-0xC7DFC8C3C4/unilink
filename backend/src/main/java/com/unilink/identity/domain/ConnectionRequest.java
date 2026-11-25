package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

/** Mutual connection request (D-200, V018). A connection is an ACCEPTED
 *  row, readable in either direction; removal transitions it to REMOVED
 *  (the audit row is kept, and a fresh request is allowed afterwards).
 *  The UNIQUE (requester_id, addressee_id, status) key blocks duplicate
 *  PENDING rows while allowing a fresh request after REJECTED/CANCELLED/
 *  REMOVED. Self-request is enforced in service. */
@Entity @Table(name="connection_requests") @Getter @Setter @NoArgsConstructor
public class ConnectionRequest {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="requester_id") private Long requesterId;
  @Column(name="addressee_id") private Long addresseeId;
  @Column(name="status") private String status;
  @Column(name="message") private String message;
  @Column(name="decided_at") private LocalDateTime decidedAt;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
