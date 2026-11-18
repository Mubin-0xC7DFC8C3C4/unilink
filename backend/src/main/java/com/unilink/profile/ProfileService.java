package com.unilink.profile;
import com.unilink.common.Exceptions; import com.unilink.community.FeedService;
import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*; import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import com.unilink.storage.ExifStripper;
import com.unilink.storage.StorageGateway; import com.unilink.storage.UploadValidator;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.*;
@Service
public class ProfileService {
  public record ProfileDto(Long userId, String fullName, String email, String phone, Long universityId, Long departmentId,
                           Long batchId, String studentId, String state, String bio, String headline, String links,
                           List<String> skills, List<String> sectors, long followers, long following, boolean isOwner,
                           String universityName, String departmentName, String batchLabel) {}
  public record PhotoFile(byte[] bytes, String contentType) {}
  private final UserRepository users; private final ProfileRepository profiles; private final ProjectRepository projects;
  private final EducationEntryRepository education; private final AchievementRepository achievements;
  private final UserSkillRepository userSkills; private final SkillRepository skills; private final FollowRepository follows;
  private final UserSectorFollowRepository sectorFollows; private final SectorTagRepository sectors;
  private final IdentityChangeRequestRepository changeRequests; private final AccessPolicy policy;
  private final PostRepository posts; private final FeedService feed;
  private final UniversityRepository universities; private final DepartmentRepository departments; private final BatchRepository batches;
  private final StorageGateway storage; private final UploadValidator uploads;
  public ProfileService(UserRepository users, ProfileRepository profiles, ProjectRepository projects,
      EducationEntryRepository education, AchievementRepository achievements, UserSkillRepository userSkills,
      SkillRepository skills, FollowRepository follows, UserSectorFollowRepository sectorFollows,
      SectorTagRepository sectors, IdentityChangeRequestRepository changeRequests, AccessPolicy policy,
      PostRepository posts, FeedService feed,
      UniversityRepository universities, DepartmentRepository departments, BatchRepository batches,
      StorageGateway storage, UploadValidator uploads){
    this.users=users; this.profiles=profiles; this.projects=projects; this.education=education; this.achievements=achievements;
    this.userSkills=userSkills; this.skills=skills; this.follows=follows; this.sectorFollows=sectorFollows;
    this.sectors=sectors; this.changeRequests=changeRequests; this.policy=policy;
    this.posts=posts; this.feed=feed;
    this.universities=universities; this.departments=departments; this.batches=batches;
    this.storage=storage; this.uploads=uploads;
  }
  @Transactional(readOnly=true)
  public ProfileDto getProfile(Long targetUserId, Principals.UnilinkUserPrincipal viewer){
    User u = users.findById(targetUserId).orElseThrow(() -> new Exceptions.NotFound("User"));
    boolean owner = viewer!=null && viewer.userId().equals(targetUserId);
    Profile p = profiles.findById(targetUserId).orElse(null);
    // Privacy: phone hidden by default; address NEVER in another user's DTO (D-016 / FR-PROF).
    String phone = (owner || (p!=null && Boolean.TRUE.equals(p.getPhoneVisible()))) ? u.getPhone() : null;
    List<String> skillNames = userSkills.findByUserId(targetUserId).stream()
      .map(us -> skills.findById(us.getSkillId()).map(Skill::getName).orElse("?")).toList();
    List<String> sectorNames = sectorFollows.findByUserId(targetUserId).stream()
      .map(us -> sectors.findById(us.getSectorTagId()).map(SectorTag::getName).orElse("?")).toList();
    return new ProfileDto(u.getId(), u.getFullName(), u.getEmail(), phone, u.getUniversityId(), u.getDepartmentId(),
      u.getBatchId(), u.getStudentId(), u.getState(), p==null?null:p.getBio(), p==null?null:p.getHeadline(),
      p==null?null:p.getLinks(), skillNames, sectorNames,
      follows.countByFollowedId(targetUserId), follows.countByFollowerId(targetUserId), owner,
      u.getUniversityId()==null?null:universities.findById(u.getUniversityId()).map(University::getName).orElse(null),
      u.getDepartmentId()==null?null:departments.findById(u.getDepartmentId()).map(Department::getName).orElse(null),
      u.getBatchId()==null?null:batches.findById(u.getBatchId()).map(Batch::getLabel).orElse(null));
  }
  @Transactional
  public ProfileDto updateOwn(Long userId, String bio, String headline, String links, Boolean phoneVisible){
    Profile p = profiles.findById(userId).orElseGet(() -> { Profile np=new Profile(); np.setId(userId); return np; });
    if (bio!=null) p.setBio(bio); if (headline!=null) p.setHeadline(headline);
    if (links!=null){ if (links.contains("javascript:")) throw new Exceptions.BadRequest("BAD_LINK","Links must use http(s)"); p.setLinks(links); }
    if (phoneVisible!=null) p.setPhoneVisible(phoneVisible);
    profiles.save(p);
    return getProfile(userId, null);
  }
  @Transactional public ProfileDtos.ProjectDto addProject(Long userId, String title, String desc, String url){
    if (url!=null && url.toLowerCase().contains("javascript:")) throw new Exceptions.BadRequest("BAD_LINK","Links must use http(s)");
    // D-140: a null/blank title used to persist a junk row with 200.
    if (title==null || title.isBlank()) throw new Exceptions.BadRequest("TITLE_REQUIRED","A project needs a title");
    Project pr=new Project(); pr.setUserId(userId); pr.setTitle(title); pr.setDescription(desc); pr.setUrl(url); return ProfileDtos.ProjectDto.from(projects.save(pr));
  }
  @Transactional public void deleteProject(Long userId, Long id){
    Project pr = projects.findByIdAndUserId(id, userId).orElseThrow(() -> new Exceptions.NotFound("Project")); projects.delete(pr);
  }
  @Transactional public ProfileDtos.EducationEntryDto addEducation(Long userId, String inst, String degree, Integer year){
    // D-140: a null/blank institution used to persist a junk row with 200.
    if (inst==null || inst.isBlank()) throw new Exceptions.BadRequest("INSTITUTION_REQUIRED","An education entry needs an institution");
    EducationEntry e=new EducationEntry(); e.setUserId(userId); e.setInstitution(inst); e.setDegree(degree); e.setYear(year); return ProfileDtos.EducationEntryDto.from(education.save(e));
  }
  @Transactional public ProfileDtos.AchievementDto addAchievement(Long userId, String title, String desc, Integer year){
    // D-140: a null/blank title used to persist a junk row with 200.
    if (title==null || title.isBlank()) throw new Exceptions.BadRequest("TITLE_REQUIRED","An achievement needs a title");
    Achievement a=new Achievement(); a.setUserId(userId); a.setTitle(title); a.setDescription(desc); a.setYear(year); return ProfileDtos.AchievementDto.from(achievements.save(a));
  }
  @Transactional public void addSkill(Long userId, String skillName){
    // D-140: a null/blank name used to persist a junk Skill row with 200.
    if (skillName==null || skillName.isBlank()) throw new Exceptions.BadRequest("SKILL_NAME_REQUIRED","A skill needs a name");
    Skill s = skills.findByName(skillName).orElseGet(() -> { Skill ns=new Skill(); ns.setName(skillName); return skills.save(ns); });
    if (!userSkills.existsByUserIdAndSkillId(userId, s.getId())){
      UserSkill us=new UserSkill(); us.setUserId(userId); us.setSkillId(s.getId()); userSkills.save(us);
    }
  }
  @Transactional public void removeSkill(Long userId, Long skillId){ userSkills.deleteByUserIdAndSkillId(userId, skillId); }
  @Transactional public void followSector(Long userId, Long sectorId){
    // D-150: user_sector_follows has an FK to sector_tags (RESTRICT), so
    // following a non-existent sector used to fail as a 500.
    if (!sectors.existsById(sectorId)) throw new Exceptions.NotFound("Sector");
    if (!sectorFollows.existsByUserIdAndSectorTagId(userId, sectorId)){
      UserSectorFollow f=new UserSectorFollow(); f.setUserId(userId); f.setSectorTagId(sectorId); sectorFollows.save(f);
    }
  }
  @Transactional public void follow(Long followerId, Long followedId){
    if (followerId.equals(followedId)) throw new Exceptions.BadRequest("CANNOT_FOLLOW_SELF","Cannot follow yourself");
    users.findById(followedId).orElseThrow(() -> new Exceptions.NotFound("User"));
    if (!follows.existsByFollowerIdAndFollowedId(followerId, followedId)){
      Follow f=new Follow(); f.setFollowerId(followerId); f.setFollowedId(followedId); follows.save(f);
    }
  }
  @Transactional public void unfollow(Long followerId, Long followedId){ follows.deleteByFollowerIdAndFollowedId(followerId, followedId); }
  @Transactional public void unfollowSector(Long userId, Long sectorId){ sectorFollows.deleteByUserIdAndSectorTagId(userId, sectorId); }
  @Transactional public IdentityChangeRequest requestIdentityChange(Long userId, Long newUniversityId, Long newDepartmentId, String newStudentId){
    // D-151: one PENDING request per user. A second submit while one is
    // pending used to create a duplicate row; the admin decide path then
    // had two live requests for the same change.
    if (changeRequests.existsByUserIdAndStatus(userId, "PENDING"))
      throw new Exceptions.Conflict("REQUEST_PENDING","You already have a pending identity-change request");
    // D-017: user stays Verified with old values until a reviewer approves.
    IdentityChangeRequest r=new IdentityChangeRequest();
    r.setUserId(userId); r.setNewUniversityId(newUniversityId); r.setNewDepartmentId(newDepartmentId);
    r.setNewStudentId(newStudentId); r.setStatus("PENDING");
    return changeRequests.save(r);
  }
  // ---- portfolio lists + edit + delete (FR-PROF-03/04) ----
  @Transactional(readOnly=true)
  public List<ProfileDtos.ProjectDto> listProjects(Long userId){
    users.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    return projects.findByUserId(userId).stream().map(ProfileDtos.ProjectDto::from).toList();
  }
  @Transactional
  public ProfileDtos.ProjectDto updateProject(Long userId, Long id, String title, String desc, String url){
    if (url!=null && url.toLowerCase().contains("javascript:")) throw new Exceptions.BadRequest("BAD_LINK","Links must use http(s)");
    Project pr = projects.findByIdAndUserId(id, userId).orElseThrow(() -> new Exceptions.NotFound("Project"));
    if (title!=null) pr.setTitle(title);
    if (desc!=null) pr.setDescription(desc);
    if (url!=null) pr.setUrl(url);
    return ProfileDtos.ProjectDto.from(projects.save(pr));
  }
  @Transactional(readOnly=true)
  public List<ProfileDtos.EducationEntryDto> listEducation(Long userId){
    users.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    return education.findByUserId(userId).stream().map(ProfileDtos.EducationEntryDto::from).toList();
  }
  @Transactional
  public ProfileDtos.EducationEntryDto updateEducation(Long userId, Long id, String institution, String degree, Integer year){
    EducationEntry e = education.findByIdAndUserId(id, userId).orElseThrow(() -> new Exceptions.NotFound("Education entry"));
    if (institution!=null) e.setInstitution(institution);
    if (degree!=null) e.setDegree(degree);
    if (year!=null) e.setYear(year);
    return ProfileDtos.EducationEntryDto.from(education.save(e));
  }
  @Transactional
  public void deleteEducation(Long userId, Long id){
    EducationEntry e = education.findByIdAndUserId(id, userId).orElseThrow(() -> new Exceptions.NotFound("Education entry"));
    education.delete(e);
  }
  @Transactional(readOnly=true)
  public List<ProfileDtos.AchievementDto> listAchievements(Long userId){
    users.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    return achievements.findByUserId(userId).stream().map(ProfileDtos.AchievementDto::from).toList();
  }
  @Transactional
  public ProfileDtos.AchievementDto updateAchievement(Long userId, Long id, String title, String desc, Integer year){
    Achievement a = achievements.findByIdAndUserId(id, userId).orElseThrow(() -> new Exceptions.NotFound("Achievement"));
    if (title!=null) a.setTitle(title);
    if (desc!=null) a.setDescription(desc);
    if (year!=null) a.setYear(year);
    return ProfileDtos.AchievementDto.from(achievements.save(a));
  }
  @Transactional
  public void deleteAchievement(Long userId, Long id){
    Achievement a = achievements.findByIdAndUserId(id, userId).orElseThrow(() -> new Exceptions.NotFound("Achievement"));
    achievements.delete(a);
  }
  // ---- activity feed (FR-PROF-07): the user's own public posts, newest first ----
  // F1: the caller's visibility scope applies here too — an unverified caller
  // sees NATIONAL posts only (D-016); a verified caller sees posts in their
  // scope, reusing the same list-scope predicate as PostService.visibleEvents
  // (same university, or DEPARTMENT_HUB in their catalog). The owner gets no
  // exemption: this matches PostService.getPost, which applies D-016 to owners too.
  @Transactional(readOnly=true)
  public List<FeedService.PostDto> activity(Long userId, Principals.UnilinkUserPrincipal viewer){
    users.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    return posts.findByAuthorIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 20)).stream()
      .filter(p -> "ACTIVE".equals(p.getStatus()))
      .filter(p -> visibleTo(p, viewer)).map(feed::toDto).toList();
  }
  private boolean visibleTo(Post post, Principals.UnilinkUserPrincipal viewer){
    if (viewer==null || !"VERIFIED".equals(viewer.state())) return "NATIONAL".equals(post.getVisibility());
    return "NATIONAL".equals(post.getVisibility())
      || (post.getScopeUniversityId()!=null && post.getScopeUniversityId().equals(viewer.universityId()))
      || ("DEPARTMENT_HUB".equals(post.getVisibility())
          && post.getScopeCatalogId()!=null && post.getScopeCatalogId().equals(viewer.catalogId()));
  }
  // ---- profile photo / cover upload (FR-PROF-01): photo ≤2 MB, cover ≤5 MB
  //  (D-029a); images are EXIF-stripped before storage (D-106); the previous
  //  stored object is deleted so stale files do not accumulate. The new object
  //  is stored BEFORE the old one is deleted, so a failed store never loses
  //  the current photo/cover. The caller can only ever replace their OWN
  //  photo/cover: the key deleted is the one read from their own row (D-107). ----
  @Transactional
  public void uploadPhoto(Long userId, String fileName, String contentType, byte[] bytes){
    if (bytes==null || bytes.length==0) throw new Exceptions.BadRequest("INVALID_FILE","File is empty or unreadable");
    String sniffed = uploads.validateImage(bytes, UploadValidator.PHOTO_MAX, contentType);
    byte[] stored = ExifStripper.strip(bytes);
    User u = users.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    String key = storage.store("profile-photos", UploadValidator.sanitizeFileName(fileName), sniffed, stored);
    if (u.getProfilePhotoKey()!=null) storage.delete(u.getProfilePhotoKey());
    u.setProfilePhotoKey(key); users.save(u);
  }
  @Transactional
  public void uploadCover(Long userId, String fileName, String contentType, byte[] bytes){
    if (bytes==null || bytes.length==0) throw new Exceptions.BadRequest("INVALID_FILE","File is empty or unreadable");
    String sniffed = uploads.validateImage(bytes, UploadValidator.COVER_MAX, contentType);
    byte[] stored = ExifStripper.strip(bytes);
    Profile p = profiles.findById(userId).orElseGet(() -> { Profile np=new Profile(); np.setId(userId); return np; });
    String key = storage.store("profile-covers", UploadValidator.sanitizeFileName(fileName), sniffed, stored);
    if (p.getCoverKey()!=null) storage.delete(p.getCoverKey());
    p.setCoverKey(key); profiles.save(p);
  }
  @Transactional(readOnly=true)
  public PhotoFile openPhoto(Long userId){
    User u = users.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    if (u.getProfilePhotoKey()==null || !storage.exists(u.getProfilePhotoKey())) throw new Exceptions.NotFound("Photo");
    return readImage(u.getProfilePhotoKey());
  }
  @Transactional(readOnly=true)
  public PhotoFile openCover(Long userId){
    Profile p = profiles.findById(userId).orElseThrow(() -> new Exceptions.NotFound("Photo"));
    if (p.getCoverKey()==null || !storage.exists(p.getCoverKey())) throw new Exceptions.NotFound("Photo");
    return readImage(p.getCoverKey());
  }
  private PhotoFile readImage(String key){
    try {
      byte[] bytes = storage.open(key).readAllBytes();
      return new PhotoFile(bytes, uploads.sniffContentType(bytes, null));
    } catch (IOException e){ throw new IllegalStateException("Storage unavailable", e); }
  }
}
