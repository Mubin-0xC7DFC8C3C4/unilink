package com.unilink.profile.api;
import com.unilink.community.FeedService;
import com.unilink.profile.ProfileDtos; import com.unilink.profile.ProfileService;
import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
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
  /** Profile photo / cover upload (FR-PROF-01): multipart `file`, image ≤2 MB. */
  @PutMapping(value="/me/photo", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String,String> uploadPhoto(Authentication a, @RequestParam("file") MultipartFile file) throws IOException {
    var p = policy.requireStudent(a.getPrincipal());
    service.uploadPhoto(p.userId(), file.getOriginalFilename(), file.getContentType(), file.getBytes());
    return Map.of("status","uploaded");
  }
  @PutMapping(value="/me/cover", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String,String> uploadCover(Authentication a, @RequestParam("file") MultipartFile file) throws IOException {
    var p = policy.requireStudent(a.getPrincipal());
    service.uploadCover(p.userId(), file.getOriginalFilename(), file.getContentType(), file.getBytes());
    return Map.of("status","uploaded");
  }
  @GetMapping("/{id}/photo") public ResponseEntity<ByteArrayResource> photo(Authentication a, @PathVariable Long id){
    policy.requireStudent(a.getPrincipal());
    var f = service.openPhoto(id);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType()))
      .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
      .body(new ByteArrayResource(f.bytes()));
  }
  @GetMapping("/{id}/cover") public ResponseEntity<ByteArrayResource> cover(Authentication a, @PathVariable Long id){
    policy.requireStudent(a.getPrincipal());
    var f = service.openCover(id);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType()))
      .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
      .body(new ByteArrayResource(f.bytes()));
  }
  /** Activity feed (FR-PROF-07). */
  @GetMapping("/me/activity") public List<FeedService.PostDto> myActivity(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return service.activity(p.userId());
  }
  @GetMapping("/{id}/activity") public List<FeedService.PostDto> activity(Authentication a, @PathVariable Long id){
    policy.requireStudent(a.getPrincipal()); return service.activity(id);
  }
  @PostMapping("/me/projects") public ProfileDtos.ProjectDto addProject(Authentication a, @RequestBody TextPayload r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    return service.addProject(p.userId(), r.title(), r.description(), r.url());
  }
  @GetMapping("/me/projects") public List<ProfileDtos.ProjectDto> myProjects(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return service.listProjects(p.userId());
  }
  @GetMapping("/{id}/projects") public List<ProfileDtos.ProjectDto> projects(Authentication a, @PathVariable Long id){
    policy.requireStudent(a.getPrincipal()); return service.listProjects(id);
  }
  @PutMapping("/me/projects/{id}") public ProfileDtos.ProjectDto updateProject(Authentication a, @PathVariable Long id, @RequestBody TextPayload r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    return service.updateProject(p.userId(), id, r.title(), r.description(), r.url());
  }
  @DeleteMapping("/me/projects/{id}") public Map<String,String> deleteProject(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); service.deleteProject(p.userId(), id); return Map.of("status","deleted");
  }
  @PostMapping("/me/education") public ProfileDtos.EducationEntryDto addEducation(Authentication a, @RequestBody TextPayload r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    return service.addEducation(p.userId(), r.institution(), r.degree(), r.year());
  }
  @GetMapping("/me/education") public List<ProfileDtos.EducationEntryDto> myEducation(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return service.listEducation(p.userId());
  }
  @GetMapping("/{id}/education") public List<ProfileDtos.EducationEntryDto> education(Authentication a, @PathVariable Long id){
    policy.requireStudent(a.getPrincipal()); return service.listEducation(id);
  }
  @PutMapping("/me/education/{id}") public ProfileDtos.EducationEntryDto updateEducation(Authentication a, @PathVariable Long id, @RequestBody TextPayload r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    return service.updateEducation(p.userId(), id, r.institution(), r.degree(), r.year());
  }
  @DeleteMapping("/me/education/{id}") public Map<String,String> deleteEducation(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); service.deleteEducation(p.userId(), id); return Map.of("status","deleted");
  }
  @PostMapping("/me/achievements") public ProfileDtos.AchievementDto addAchievement(Authentication a, @RequestBody TextPayload r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    return service.addAchievement(p.userId(), r.title(), r.description(), r.year());
  }
  @GetMapping("/me/achievements") public List<ProfileDtos.AchievementDto> myAchievements(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return service.listAchievements(p.userId());
  }
  @GetMapping("/{id}/achievements") public List<ProfileDtos.AchievementDto> achievements(Authentication a, @PathVariable Long id){
    policy.requireStudent(a.getPrincipal()); return service.listAchievements(id);
  }
  @PutMapping("/me/achievements/{id}") public ProfileDtos.AchievementDto updateAchievement(Authentication a, @PathVariable Long id, @RequestBody TextPayload r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    return service.updateAchievement(p.userId(), id, r.title(), r.description(), r.year());
  }
  @DeleteMapping("/me/achievements/{id}") public Map<String,String> deleteAchievement(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); service.deleteAchievement(p.userId(), id); return Map.of("status","deleted");
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
  /** Sector unfollow (FR-SEC-02): there was only a follow endpoint. */
  @DeleteMapping("/me/sectors/{sectorId}") public Map<String,String> unfollowSector(Authentication a, @PathVariable Long sectorId){
    var p = policy.requireStudent(a.getPrincipal()); service.unfollowSector(p.userId(), sectorId); return Map.of("status","unfollowed");
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
