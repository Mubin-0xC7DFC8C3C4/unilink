package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

/** One-time GitHub OAuth state nonce (D-203). The HMAC-signed state
 *  parameter (userId.nonce.issuedAt) is verified against this row at
 *  callback time: the row must exist, belong to the state user, be unused
 *  and unexpired. Consuming a state sets used_at, so a replayed callback
 *  finds no usable row. Expired rows are deleted lazily on consume. */
@Entity @Table(name="github_oauth_states") @Getter @Setter @NoArgsConstructor
public class GithubOAuthState {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="nonce", nullable=false, unique=true) private String nonce;
  @Column(name="user_id", nullable=false) private Long userId;
  @Column(name="expires_at", nullable=false) private LocalDateTime expiresAt;
  @Column(name="used_at") private LocalDateTime usedAt;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
