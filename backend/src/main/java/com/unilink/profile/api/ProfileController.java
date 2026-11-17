package com.unilink.profile.api;
import com.unilink.profile.ProfileDtos; import com.unilink.profile.ProfileService;
import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/profiles")
public class ProfileController {
  private final ProfileService service; private final AccessPolicy policy;
  public ProfileController(ProfileService s, AccessPolicy p){ service=s; policy=p; }
  public record UpdateProfile(String bio, String headline, String links, Boolean phoneVisible) {}
  public record TextPayload(String title, String description, String url, String institution, String degree, Integer year) {}
  @GetMapping("/me") public ProfileService.ProfileDto me(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return service.getProfile(p.userId(), p);
  }
  @PutMapping("/me") public ProfileService.ProfileDto updateMe(Authentication a, @RequestBody UpdateProfile r){
    var p = policy.requireStudent(a.getPrincipal());
    return service.updateOwn(p.userId(), r.bio(), r.headline(), r.links(), r.phoneVisible());
  }
  @GetMapping("/{id}") public ProfileService.ProfileDto get(Authentication a, @PathVariable Long id){
    Principals.UnilinkUserPrincipal p = a==null?null:(Principals.UnilinkUserPrincipal) a.getPrincipal();
    return service.getProfile(id, p);
  }
  @PostMapping("/me/projects") public ProfileDtos.ProjectDto addProject(Authentication a, @RequestBody TextPayload r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    return service.addProject(p.userId(), r.title(), r.description(), r.url());
  }
  @DeleteMapping("/me/projects/{id}") public Map<String,String> deleteProject(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); service.deleteProject(p.userId(), id); return Map.of("status","deleted");
  }
  @PostMapping("/me/education") public ProfileDtos.EducationEntryDto addEducation(Authentication a, @RequestBody TextPayload r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    return service.addEducation(p.userId(), r.institution(), r.degree(), r.year());
  }
  @PostMapping("/me/achievements") public ProfileDtos.AchievementDto addAchievement(Authentication a, @RequestBody TextPayload r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    return service.addAchievement(p.userId(), r.title(), r.description(), r.year());
  }
  @PostMapping("/me/skills") public Map<String,String> addSkill(Authentication a, @RequestBody Map<String,String> r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    service.addSkill(p.userId(), r.get("name")); return Map.of("status","added");
  }
  @DeleteMapping("/me/skills/{skillId}") public Map<String,String> removeSkill(Authentication a, @PathVariable Long skillId){
    var p = policy.requireStudent(a.getPrincipal()); service.removeSkill(p.userId(), skillId); return Map.of("status","removed");
  }
  @PostMapping("/me/sectors/{sectorId}") public Map<String,String> followSector(Authentication a, @PathVariable Long sectorId){
    var p = policy.requireStudent(a.getPrincipal()); service.followSector(p.userId(), sectorId); return Map.of("status","followed");
  }
  @PostMapping("/{id}/follow") public Map<String,String> follow(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    service.follow(p.userId(), id); return Map.of("status","followed");
  }
  @DeleteMapping("/{id}/follow") public Map<String,String> unfollow(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); service.unfollow(p.userId(), id); return Map.of("status","unfollowed");
  }
  public record IdentityChangeRequest(Long newUniversityId, Long newDepartmentId, String newStudentId) {}
  @PostMapping("/me/identity-change") public IdentityChangeRequest submitIdentityChange(Authentication a, @RequestBody IdentityChangeRequest r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    service.requestIdentityChange(p.userId(), r.newUniversityId(), r.newDepartmentId(), r.newStudentId());
    return r;
  }
}
