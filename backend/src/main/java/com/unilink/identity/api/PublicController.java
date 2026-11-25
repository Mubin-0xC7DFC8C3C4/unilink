package com.unilink.identity.api;
import com.unilink.identity.dto.CatalogDtos.*;
import com.unilink.identity.service.PublicCatalogService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
/** Public master data for the sign-up form and filters (FR-ADM-10 data, read-only).
 *  Thin controller (ARCH): all reads go through the service and return DTOs. */
@RestController @RequestMapping("/api/public")
public class PublicController {
  private final PublicCatalogService catalog;
  public PublicController(PublicCatalogService catalog){ this.catalog=catalog; }
  @GetMapping("/universities") public List<UniversityDto> universities(){ return catalog.universities(); }
  @GetMapping("/departments") public List<DepartmentDto> departments(@RequestParam Long universityId){ return catalog.departments(universityId); }
  @GetMapping("/department-catalog") public List<DepartmentCatalogDto> catalog(@RequestParam(required=false) String q){ return catalog.catalog(q); }
  @GetMapping("/batches") public List<BatchDto> batches(@RequestParam Long universityId, @RequestParam(required=false) Long departmentId){
    return catalog.batches(universityId, departmentId);
  }
  @GetMapping("/sectors") public List<SectorTagDto> sectors(){ return catalog.sectors(); }
  @GetMapping("/skills") public List<SkillDto> skills(){ return catalog.skills(); }
}
