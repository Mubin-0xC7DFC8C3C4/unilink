package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="event_details") @Getter @Setter @NoArgsConstructor
public class EventDetail {
  @Id @Column(name="post_id") private Long id;
  @Column(name="deadline") private LocalDateTime deadline;
  @Column(name="reg_link") private String regLink;
  @Column(name="location") private String location;
  @Column(name="registration_closed") private Boolean registrationClosed;
  @Column(name="archived_at") private LocalDateTime archivedAt;
  @PrePersist void onCreate(){}
}
