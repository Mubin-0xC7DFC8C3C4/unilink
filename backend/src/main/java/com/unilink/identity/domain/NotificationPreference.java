package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="notification_preferences") @Getter @Setter @NoArgsConstructor
public class NotificationPreference {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="user_id") private Long userId;
  @Column(name="type") private String type;
  @Column(name="email_enabled") private Boolean emailEnabled;
  @Column(name="in_app_enabled") private Boolean inAppEnabled;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
