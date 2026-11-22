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
  @Column(name="organizer_name") private String organizerName;
  @Column(name="organizing_unit") private String organizingUnit;
  @Column(name="contact_info") private String contactInfo;
  @Column(name="short_description") private String shortDescription;
  @Column(name="end_at") private LocalDateTime endAt;
  @Column(name="timezone") private String timezone;
  @Column(name="event_type") private String eventType;
  @Column(name="venue_name") private String venueName;
  @Column(name="full_address") private String fullAddress;
  @Column(name="meeting_link") private String meetingLink;
  @Column(name="registration_required") private Boolean registrationRequired;
  @Column(name="registration_deadline") private LocalDateTime registrationDeadline;
  @Column(name="max_participants") private Integer maxParticipants;
  @Column(name="event_status") private String eventStatus;
  @Column(name="created_by_admin_id") private Long createdByAdminId;
  @Column(name="cover_key") private String coverKey;
  @PrePersist void onCreate(){ if(eventStatus==null) eventStatus="PUBLISHED"; if(registrationRequired==null) registrationRequired=true; if(timezone==null) timezone="Asia/Dhaka"; }
}
