package com.unilink.identity.service;
import com.unilink.identity.domain.DepartmentAlias;
import com.unilink.identity.domain.DepartmentCatalog;
import com.unilink.identity.dto.CatalogDtos.*;
import com.unilink.identity.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
/** Public master data for the sign-up form and filters (FR-ADM-10 data, read-only).
 *  Extracted from PublicController so controllers never touch repositories (ARCH). */
@Service
public class PublicCatalogService {
  private final UniversityRepository universities; private final DepartmentRepository departments;
  private final BatchRepository batches; private final SectorTagRepository sectors; private final SkillRepository skills;
  private final DepartmentCatalogRepository catalogs; private final DepartmentAliasRepository aliases;
  public PublicCatalogService(UniversityRepository u, DepartmentRepository d, BatchRepository b, SectorTagRepository s, SkillRepository k, DepartmentCatalogRepository c, DepartmentAliasRepository a){
    universities=u; departments=d; batches=b; sectors=s; skills=k; catalogs=c; aliases=a;
  }
  @Transactional(readOnly=true)
  public List<UniversityDto> universities(){ return universities.findByActiveTrue().stream().map(UniversityDto::from).toList(); }
  @Transactional(readOnly=true)
  public List<DepartmentDto> departments(Long universityId){
    var rows = departments.findByUniversityIdAndActiveTrue(universityId);
    if (rows.isEmpty()) return List.of();
    // D-198: enrich with the catalog short_name + aliases so the sign-up
    // search can match "CSE" against "Computer Science and Engineering".
    var catIds = rows.stream().map(com.unilink.identity.domain.Department::getCatalogId).filter(Objects::nonNull).distinct().toList();
    Map<Long, DepartmentCatalog> catById = catalogs.findAllById(catIds).stream()
        .collect(Collectors.toMap(DepartmentCatalog::getId, c -> c));
    Map<Long, List<String>> aliasesByCat = aliasesByCatalog(catIds);
    return rows.stream().map(d -> {
      var c = catById.get(d.getCatalogId());
      return DepartmentDto.from(d, c == null ? null : c.getShortName(), aliasesByCat.getOrDefault(d.getCatalogId(), List.of()));
    }).toList();
  }
  @Transactional(readOnly=true)
  public List<DepartmentCatalogDto> catalog(){ return toCatalogDtos(catalogs.findByActiveTrue()); }
  /** D-198: server-side catalog search across name, short_name and
   *  aliases, case-insensitive substring. No-q form is unchanged. */
  @Transactional(readOnly=true)
  public List<DepartmentCatalogDto> catalog(String q){
    if (q == null || q.isBlank()) return catalog();
    return toCatalogDtos(catalogs.searchActive(escapeLike(q.trim())));
  }
  private List<DepartmentCatalogDto> toCatalogDtos(List<DepartmentCatalog> rows){
    if (rows.isEmpty()) return List.of();
    Map<Long, List<String>> aliasesByCat = aliasesByCatalog(rows.stream().map(DepartmentCatalog::getId).toList());
    return rows.stream().map(c -> DepartmentCatalogDto.from(c, aliasesByCat.getOrDefault(c.getId(), List.of()))).toList();
  }
  private Map<Long, List<String>> aliasesByCatalog(List<Long> catalogIds){
    if (catalogIds.isEmpty()) return Map.of();
    return aliases.findByCatalogIdInAndActiveTrue(catalogIds).stream()
        .collect(Collectors.groupingBy(DepartmentAlias::getCatalogId,
            Collectors.mapping(DepartmentAlias::getAlias, Collectors.toList())));
  }
  /** Escape LIKE wildcards so a query containing % or _ matches literally. */
  static String escapeLike(String q){ return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
  @Transactional(readOnly=true)
  public List<BatchDto> batches(Long universityId, Long departmentId){
    // D-153: scoped query instead of loading the whole batches table.
    return (departmentId==null ? batches.findByUniversityIdAndActiveTrue(universityId)
      : batches.findByUniversityIdAndDepartmentIdAndActiveTrue(universityId, departmentId))
      .stream().map(BatchDto::from).toList();
  }
  @Transactional(readOnly=true)
  public List<SectorTagDto> sectors(){ return sectors.findByActiveTrue().stream().map(SectorTagDto::from).toList(); }
  @Transactional(readOnly=true)
  public List<SkillDto> skills(){ return skills.findByActiveTrue().stream().map(SkillDto::from).toList(); }
}
