package com.unilink.identity.dto;
import com.unilink.identity.domain.*;
import java.time.LocalDateTime;
/** Public master-data DTOs (ARCH: entities never leave the service layer).
 *  Record components mirror the entity JSON field names; no secrets or storage keys. */
public final class CatalogDtos {
  private CatalogDtos(){}
  public record UniversityDto(Long id, String name, String shortName, String emailDomains, Boolean active,
                              LocalDateTime createdAt, LocalDateTime updatedAt){
    public static UniversityDto from(University u){ return new UniversityDto(u.getId(), u.getName(), u.getShortName(), u.getEmailDomains(), u.getActive(), u.getCreatedAt(), u.getUpdatedAt()); }
  }
  public record DepartmentDto(Long id, Long universityId, Long catalogId, String name, Boolean active, LocalDateTime createdAt){
    public static DepartmentDto from(Department d){ return new DepartmentDto(d.getId(), d.getUniversityId(), d.getCatalogId(), d.getName(), d.getActive(), d.getCreatedAt()); }
  }
  public record BatchDto(Long id, Long universityId, Long departmentId, String label, Integer startYear, Boolean active, LocalDateTime createdAt){
    public static BatchDto from(Batch b){ return new BatchDto(b.getId(), b.getUniversityId(), b.getDepartmentId(), b.getLabel(), b.getStartYear(), b.getActive(), b.getCreatedAt()); }
  }
  public record SectorTagDto(Long id, String name, Boolean active, LocalDateTime createdAt){
    public static SectorTagDto from(SectorTag s){ return new SectorTagDto(s.getId(), s.getName(), s.getActive(), s.getCreatedAt()); }
  }
  public record SkillDto(Long id, String name, Boolean active, LocalDateTime createdAt){
    public static SkillDto from(Skill s){ return new SkillDto(s.getId(), s.getName(), s.getActive(), s.getCreatedAt()); }
  }
  public record DepartmentCatalogDto(Long id, String name, String shortName, Boolean active, LocalDateTime createdAt){
    public static DepartmentCatalogDto from(DepartmentCatalog c){ return new DepartmentCatalogDto(c.getId(), c.getName(), c.getShortName(), c.getActive(), c.getCreatedAt()); }
  }
  public record AnnouncementDto(Long id, Long adminId, String title, String body, String scope, Long universityId,
                                LocalDateTime expiresAt, Boolean active, LocalDateTime createdAt){
    public static AnnouncementDto from(Announcement a){ return new AnnouncementDto(a.getId(), a.getAdminId(), a.getTitle(), a.getBody(), a.getScope(), a.getUniversityId(), a.getExpiresAt(), a.getActive(), a.getCreatedAt()); }
  }
}
