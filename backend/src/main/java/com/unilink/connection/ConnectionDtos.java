package com.unilink.connection;
import com.unilink.identity.domain.*;
import java.time.LocalDateTime;
/** Connection DTOs (ARCH: entities never leave the service layer).
 *  No phone, email or student ID is ever carried (same privacy rule as
 *  search user hits). */
public final class ConnectionDtos {
  private ConnectionDtos(){}
  public record ConnectionRequestDto(Long id, Long requesterId, String requesterName, Long addresseeId,
                                     String addresseeName, String status, String message,
                                     LocalDateTime decidedAt, LocalDateTime createdAt){
    public static ConnectionRequestDto from(ConnectionRequest r, String requesterName, String addresseeName){
      return new ConnectionRequestDto(r.getId(), r.getRequesterId(), requesterName, r.getAddresseeId(),
        addresseeName, r.getStatus(), r.getMessage(), r.getDecidedAt(), r.getCreatedAt());
    }
  }
  /** One side of an ACCEPTED connection, from the caller's point of view. */
  public record ConnectionDto(Long requestId, Long userId, String fullName, String universityName,
                              String departmentName, LocalDateTime connectedAt){}
  /** NONE | PENDING_OUTGOING | PENDING_INCOMING | CONNECTED | SELF.
   *  requestId is the live PENDING request id when the status is
   *  PENDING_* (so the UI can accept/cancel it), null otherwise. */
  public record ConnectionStatusDto(String status, Long requestId){}
}
