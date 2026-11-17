package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="attachments") @Getter @Setter @NoArgsConstructor
public class Attachment {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="owner_type") private String ownerType;
  @Column(name="owner_id") private Long ownerId;
  @Column(name="storage_key") private String storageKey;
  @Column(name="file_name") private String fileName;
  @Column(name="content_type") private String contentType;
  @Column(name="size_bytes") private Long sizeBytes;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
