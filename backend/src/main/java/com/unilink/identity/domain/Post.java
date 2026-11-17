package com.unilink.identity.domain;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;

@Entity @Table(name="posts") @Getter @Setter @NoArgsConstructor
@AllArgsConstructor
public class Post {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Version private Long version;
  @Column(name="author_id") private Long authorId;
  @Column(name="type") private String type;
  @Column(name="visibility") private String visibility;
  @Column(name="scope_university_id") private Long scopeUniversityId;
  @Column(name="scope_department_id") private Long scopeDepartmentId;
  @Column(name="scope_catalog_id") private Long scopeCatalogId;
  @Column(name="title") private String title;
  @Lob
  @Column(name="body") private String body;
  @Column(name="status") private String status;
  @Column(name="created_at") private LocalDateTime createdAt;
  @Column(name="updated_at") private LocalDateTime updatedAt;
  @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }
}
