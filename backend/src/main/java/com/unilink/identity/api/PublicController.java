package com.unilink.identity.api;
import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
/** Public master data for the sign-up form and filters (FR-ADM-10 data, read-only). */
@RestController @RequestMapping("/api/public")
public class PublicController {
  private final UniversityRepository universities; private final DepartmentRepository departments;
  private final BatchRepository batches; private final SectorTagRepository sectors; private final SkillRepository skills;
  private final DepartmentCatalogRepository catalogs;
  public PublicController(UniversityRepository u, DepartmentRepository d, BatchRepository b, SectorTagRepository s, SkillRepository k, DepartmentCatalogRepository c){
    universities=u; departments=d; batches=b; sectors=s; skills=k; catalogs=c;
  }
  @GetMapping("/universities") public List<University> universities(){ return universities.findByActiveTrue(); }
  @GetMapping("/departments") public List<Department> departments(@RequestParam Long universityId){ return departments.findByUniversityIdAndActiveTrue(universityId); }
  @GetMapping("/department-catalog") public List<DepartmentCatalog> catalog(){ return catalogs.findByActiveTrue(); }
  @GetMapping("/batches") public List<Batch> batches(@RequestParam Long universityId, @RequestParam(required=false) Long departmentId){
    return departmentId==null ? batches.findAll().stream().filter(b->b.getUniversityId().equals(universityId)).toList()
      : batches.findByUniversityIdAndDepartmentIdAndActiveTrue(universityId, departmentId);
  }
  @GetMapping("/sectors") public List<SectorTag> sectors(){ return sectors.findByActiveTrue(); }
  @GetMapping("/skills") public List<Skill> skills(){ return skills.findByActiveTrue(); }
}
