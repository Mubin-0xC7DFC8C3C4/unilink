package com.unilink.identity.domain;
import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;

/** A student's GitHub OAuth connection. The access token is stored
 *  AES-GCM encrypted (see AesGcm); plaintext tokens never persist. */
@Entity @Table(name="github_connections") @Getter @Setter @NoArgsConstructor
public class GithubConnection {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="github_user_id") private Long githubUserId;
  @Column(name="username") private String username;
  @Column(name="access_token_enc") private String accessTokenEnc;
  @Column(name="scopes") private String scopes;
  @Column(name="connected_at") private LocalDateTime connectedAt;
  @Column(name="updated_at") private LocalDateTime updatedAt;
  @PrePersist void onCreate(){ if(connectedAt==null) connectedAt=LocalDateTime.now(); if(updatedAt==null) updatedAt=connectedAt; }
  @PreUpdate void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
