package com.unilink.profile.api;
import com.fasterxml.jackson.databind.JsonNode;
import com.unilink.community.FeedService;
import com.unilink.profile.ProfileDtos; import com.unilink.profile.ProfileService;
import com.unilink.security.AccessPolicy; import com.unilink.security.IpResolver;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
@RestController @RequestMapping("/api/profiles")
public class ProfileController {
  private final ProfileService service; private final AccessPolicy policy; private final IpResolver ipResolver;
  public ProfileController(ProfileService s, AccessPolicy p, IpResolver ip){ service=s; policy=p; ipResolver=ip; }
  public record UpdateProfile(String bio, String headline, String links, Boolean phoneVisible) {}
  public record TextPayload(String title, String description, String url, String institution, String degree, Integer year) {}
  public record PhoneChangeRequest(String newPhone) {}
  public record PhoneChangeVerifyRequest(String otp) {}
  @GetMapping("/me") public ProfileService.ProfileDto me(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return service.getProfile(p.userId(), p);
  }
  @PutMapping("/me") public ProfileService.ProfileDto updateMe(Authentication a, @RequestBody UpdateProfile r){
    var p = policy.requireStudent(a.getPrincipal());
    return service.updateOwn(p.userId(), r.bio(), r.headline(), r.links(), r.phoneVisible(), p);
  }
  /** D-202: true PATCH — absent = untouched, explicit null = clear.
   *  Field presence is read off the raw JSON node: a record cannot tell
   *  an absent field from an explicit null. PUT stays for compatibility. */
  @PatchMapping("/me") public ProfileService.ProfileDto patchMe(Authentication a, @RequestBody JsonNode body){
    var p = policy.requireStudent(a.getPrincipal());
    if (body==null || !body.isObject()) throw new com.unilink.common.Exceptions.BadRequest("INVALID_REQUEST","Request body must be a JSON object");
    var patch = new ProfileService.PatchUpdate(
      body.has("bio"), body.has("bio") && !body.get("bio").isNull() ? body.get("bio").asText() : null,
      body.has("headline"), body.has("headline") && !body.get("headline").isNull() ? body.get("headline").asText() : null,
      body.has("links"), body.has("links") && !body.get("links").isNull() ? body.get("links").asText() : null,
      body.has("phoneVisible"), body.has("phoneVisible") && !body.get("phoneVisible").isNull() ? body.get("phoneVisible").asBoolean() : null);
    return service.patchOwn(p.userId(), patch, p);
  }
  // ---- phone change (D-199): two-step, OTP re-verification of the NEW
  // number. Kept out of the generic profile save: the old verified number
  // stays in force until the new one verifies. Re-sending is a repeat
  // POST here with the same newPhone (same caps apply). ----
  @PostMapping("/me/phone-change") public Map<String,String> requestPhoneChange(Authentication a, @RequestBody PhoneChangeRequest r, jakarta.servlet.http.HttpServletRequest req){
    var p = policy.requireStudent(a.getPrincipal());
    service.requestPhoneChange(p.userId(), r.newPhone(), ipResolver.resolve(req));
    return Map.of("status","OTP_SENT");
  }
  @PostMapping("/me/phone-change/verify") public Map<String,String> verifyPhoneChange(Authentication a, @RequestBody PhoneChangeVerifyRequest r){
    var p = policy.requireStudent(a.getPrincipal());
    String phone = service.verifyPhoneChange(p.userId(), r.otp());
    return Map.of("status","verified","phone",phone);
  }
  @PostMapping("/me/phone-change/cancel") public Map<String,String> cancelPhoneChange(Authentication a){
    var p = policy.requireStudent(a.getPrincipal());
    service.cancelPhoneChange(p.userId());
    return Map.of("status","cancelled");
  }
  @GetMapping("/{id}") public ProfileService.ProfileDto get(Authentication a, @PathVariable Long id){
    // F7: use requireStudent like every other student controller — an admin
    // principal used to hit a raw cast (ClassCastException -> 500); now it is 403.
    var p = policy.requireStudent(a.getPrincipal());
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
  /** D-202: remove the caller's own photo / cover (key cleared, stored
   *  object deleted). There is no target id to spoof: /me always resolves
   *  to the principal. */
  @DeleteMapping("/me/photo") public Map<String,String> deletePhoto(Authentication a){
    var p = policy.requireStudent(a.getPrincipal());
    service.deletePhoto(p.userId());
    return Map.of("status","deleted");
  }
  @DeleteMapping("/me/cover") public Map<String,String> deleteCover(Authentication a){
    var p = policy.requireStudent(a.getPrincipal());
    service.deleteCover(p.userId());
    return Map.of("status","deleted");
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
  @GetMapping("/{id}/followers") public List<ProfileService.PersonDto> followers(Authentication a, @PathVariable Long id, @RequestParam(defaultValue="50") int limit){
    var viewer = policy.requireStudent(a.getPrincipal()); return service.followers(id, viewer.userId(), limit);
  }
  @GetMapping("/{id}/following") public List<ProfileService.PersonDto> followingList(Authentication a, @PathVariable Long id, @RequestParam(defaultValue="50") int limit){
    var viewer = policy.requireStudent(a.getPrincipal()); return service.following(id, viewer.userId(), limit);
  }
  @GetMapping("/suggestions") public List<ProfileService.PersonDto> suggestions(Authentication a, @RequestParam(defaultValue="10") int limit){
    var viewer = policy.requireStudent(a.getPrincipal()); return service.suggestions(viewer, limit);
  }
  @GetMapping("/me/activity-items") public List<ProfileService.ActivityItemDto> myActivityItems(Authentication a){
    var viewer = policy.requireStudent(a.getPrincipal()); return service.activityItems(viewer.userId(), viewer);
  }
  @GetMapping("/{id}/activity-items") public List<ProfileService.ActivityItemDto> activityItems(Authentication a, @PathVariable Long id){
    var viewer = policy.requireStudent(a.getPrincipal()); return service.activityItems(id, viewer);
  }
  @GetMapping("/me/activity") public List<FeedService.PostDto> myActivity(Authentication a){
    var p = policy.requireStudent(a.getPrincipal()); return service.activity(p.userId(), p);
  }
  @GetMapping("/{id}/activity") public List<FeedService.PostDto> activity(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); return service.activity(id, p);
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
  /** D-203: Pin to Profile — link a project row to one of the caller's own
   *  public GitHub repositories (validated live against the GitHub API). */
  public record PinBody(String owner, String repo) {}
  @PostMapping("/me/projects/{id}/pin") public ProfileDtos.ProjectDto pinProject(Authentication a, @PathVariable Long id, @RequestBody PinBody b){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    return service.pinProject(p.userId(), id, b.owner(), b.repo());
  }
  @DeleteMapping("/me/projects/{id}/pin") public ProfileDtos.ProjectDto unpinProject(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    return service.unpinProject(p.userId(), id);
  }
  @DeleteMapping("/me/projects/{id}") public Map<String,String> deleteProject(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p); service.deleteProject(p.userId(), id); return Map.of("status","deleted");
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
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p); service.deleteEducation(p.userId(), id); return Map.of("status","deleted");
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
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p); service.deleteAchievement(p.userId(), id); return Map.of("status","deleted");
  }
  @PostMapping("/me/skills") public Map<String,String> addSkill(Authentication a, @RequestBody Map<String,String> r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    service.addSkill(p.userId(), r.get("name")); return Map.of("status","added");
  }
  @DeleteMapping("/me/skills/{skillId}") public Map<String,String> removeSkill(Authentication a, @PathVariable Long skillId){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p); service.removeSkill(p.userId(), skillId); return Map.of("status","removed");
  }
  @PostMapping("/me/sectors/{sectorId}") public Map<String,String> followSector(Authentication a, @PathVariable Long sectorId){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p); service.followSector(p.userId(), sectorId); return Map.of("status","followed");
  }
  /** Sector unfollow (FR-SEC-02): there was only a follow endpoint. */
  @DeleteMapping("/me/sectors/{sectorId}") public Map<String,String> unfollowSector(Authentication a, @PathVariable Long sectorId){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p); service.unfollowSector(p.userId(), sectorId); return Map.of("status","unfollowed");
  }
  @PostMapping("/{id}/follow") public Map<String,String> follow(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    service.follow(p.userId(), id); return Map.of("status","followed");
  }
  @DeleteMapping("/{id}/follow") public Map<String,String> unfollow(Authentication a, @PathVariable Long id){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p); service.unfollow(p.userId(), id); return Map.of("status","unfollowed");
  }
  public record IdentityChangeRequest(Long newUniversityId, Long newDepartmentId, String newStudentId) {}
  @PostMapping("/me/identity-change") public IdentityChangeRequest submitIdentityChange(Authentication a, @RequestBody IdentityChangeRequest r){
    var p = policy.requireStudent(a.getPrincipal()); policy.requireVerified(p);
    service.requestIdentityChange(p.userId(), r.newUniversityId(), r.newDepartmentId(), r.newStudentId());
    return r;
  }
}
