package com.unilink.identity.service;
import com.unilink.identity.dto.CatalogDtos.*;
import com.unilink.identity.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** Public master data for the sign-up form and filters (FR-ADM-10 data, read-only).
 *  Extracted from PublicController so controllers never touch repositories (ARCH). */
@Service
public class PublicCatalogService {
  private final UniversityRepository universities; private final DepartmentRepository departments;
  private final BatchRepository batches; private final SectorTagRepository sectors; private final SkillRepository skills;
  private final DepartmentCatalogRepository catalogs;
  public PublicCatalogService(UniversityRepository u, DepartmentRepository d, BatchRepository b, SectorTagRepository s, SkillRepository k, DepartmentCatalogRepository c){
    universities=u; departments=d; batches=b; sectors=s; skills=k; catalogs=c;
  }
  @Transactional(readOnly=true)
  public List<UniversityDto> universities(){ return universities.findByActiveTrue().stream().map(UniversityDto::from).toList(); }
  @Transactional(readOnly=true)
  public List<DepartmentDto> departments(Long universityId){ return departments.findByUniversityIdAndActiveTrue(universityId).stream().map(DepartmentDto::from).toList(); }
  @Transactional(readOnly=true)
  public List<DepartmentCatalogDto> catalog(){ return catalogs.findByActiveTrue().stream().map(DepartmentCatalogDto::from).toList(); }
  @Transactional(readOnly=true)
  public List<BatchDto> batches(Long universityId, Long departmentId){
    return (departmentId==null ? batches.findAll().stream().filter(b->b.getUniversityId().equals(universityId)).toList()
      : batches.findByUniversityIdAndDepartmentIdAndActiveTrue(universityId, departmentId))
      .stream().map(BatchDto::from).toList();
  }
  @Transactional(readOnly=true)
  public List<SectorTagDto> sectors(){ return sectors.findByActiveTrue().stream().map(SectorTagDto::from).toList(); }
  @Transactional(readOnly=true)
  public List<SkillDto> skills(){ return skills.findByActiveTrue().stream().map(SkillDto::from).toList(); }
}
