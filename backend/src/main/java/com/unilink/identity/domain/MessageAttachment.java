package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="message_attachments") @Getter @Setter @NoArgsConstructor
public class MessageAttachment {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(name="message_id") private Long messageId;
  @Column(name="storage_key") private String storageKey;
  @Column(name="file_name") private String fileName;
  @Column(name="content_type") private String contentType;
  @Column(name="size_bytes") private Long sizeBytes;
  @PrePersist void onCreate(){}
}
