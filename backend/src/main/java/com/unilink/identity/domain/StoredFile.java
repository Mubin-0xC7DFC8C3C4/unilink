package com.unilink.identity.domain;
import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;

/** A file stored in the database (D-168). Only used when
 *  app.storage.provider=db — for hosts whose local filesystem is
 *  ephemeral (e.g. Render Free), so uploads persist in the database
 *  (TiDB/MySQL) instead of vanishing on restart. The storage_key is the
 *  same opaque key the local adapter returns (namespace/uuid-name). */
@Entity @Table(name="stored_files") @Getter @Setter @NoArgsConstructor
public class StoredFile {
  @Id @Column(name="storage_key", length=512) private String storageKey;
  @Column(name="namespace", length=64) private String namespace;
  @Column(name="content_type", length=128) private String contentType;
  @Column(name="size_bytes") private long sizeBytes;
  @Lob @Column(name="data") private byte[] data;
  @Column(name="created_at") private LocalDateTime createdAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
