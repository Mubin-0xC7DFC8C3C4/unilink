package com.unilink.profile;
import com.unilink.common.Exceptions; import com.unilink.identity.domain.*;
import com.unilink.identity.repo.*; import com.unilink.security.AccessPolicy; import com.unilink.security.Principals;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class ProfileService {
  public record ProfileDto(Long userId, String fullName, String email, String phone, Long universityId, Long departmentId,
                           Long batchId, String studentId, String state, String bio, String headline, String links,
                           List<String> skills, List<String> sectors, long followers, long following, boolean isOwner) {}
  private final UserRepository users; private final ProfileRepository profiles; private final ProjectRepository projects;
  private final EducationEntryRepository education; private final AchievementRepository achievements;
  private final UserSkillRepository userSkills; private final SkillRepository skills; private final FollowRepository follows;
  private final UserSectorFollowRepository sectorFollows; private final SectorTagRepository sectors;
  private final IdentityChangeRequestRepository changeRequests; private final AccessPolicy policy;
  public ProfileService(UserRepository users, ProfileRepository profiles, ProjectRepository projects,
      EducationEntryRepository education, AchievementRepository achievements, UserSkillRepository userSkills,
      SkillRepository skills, FollowRepository follows, UserSectorFollowRepository sectorFollows,
      SectorTagRepository sectors, IdentityChangeRequestRepository changeRequests, AccessPolicy policy){
    this.users=users; this.profiles=profiles; this.projects=projects; this.education=education; this.achievements=achievements;
    this.userSkills=userSkills; this.skills=skills; this.follows=follows; this.sectorFollows=sectorFollows;
    this.sectors=sectors; this.changeRequests=changeRequests; this.policy=policy;
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
      follows.countByFollowedId(targetUserId), follows.countByFollowerId(targetUserId), owner);
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
    Project pr=new Project(); pr.setUserId(userId); pr.setTitle(title); pr.setDescription(desc); pr.setUrl(url); return ProfileDtos.ProjectDto.from(projects.save(pr));
  }
  @Transactional public void deleteProject(Long userId, Long id){
    Project pr = projects.findByIdAndUserId(id, userId).orElseThrow(() -> new Exceptions.NotFound("Project")); projects.delete(pr);
  }
  @Transactional public ProfileDtos.EducationEntryDto addEducation(Long userId, String inst, String degree, Integer year){
    EducationEntry e=new EducationEntry(); e.setUserId(userId); e.setInstitution(inst); e.setDegree(degree); e.setYear(year); return ProfileDtos.EducationEntryDto.from(education.save(e));
  }
  @Transactional public ProfileDtos.AchievementDto addAchievement(Long userId, String title, String desc, Integer year){
    Achievement a=new Achievement(); a.setUserId(userId); a.setTitle(title); a.setDescription(desc); a.setYear(year); return ProfileDtos.AchievementDto.from(achievements.save(a));
  }
  @Transactional public void addSkill(Long userId, String skillName){
    Skill s = skills.findByName(skillName).orElseGet(() -> { Skill ns=new Skill(); ns.setName(skillName); return skills.save(ns); });
    if (!userSkills.existsByUserIdAndSkillId(userId, s.getId())){
      UserSkill us=new UserSkill(); us.setUserId(userId); us.setSkillId(s.getId()); userSkills.save(us);
    }
  }
  @Transactional public void removeSkill(Long userId, Long skillId){ userSkills.deleteByUserIdAndSkillId(userId, skillId); }
  @Transactional public void followSector(Long userId, Long sectorId){
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
  @Transactional public IdentityChangeRequest requestIdentityChange(Long userId, Long newUniversityId, Long newDepartmentId, String newStudentId){
    // D-017: user stays Verified with old values until a reviewer approves.
    IdentityChangeRequest r=new IdentityChangeRequest();
    r.setUserId(userId); r.setNewUniversityId(newUniversityId); r.setNewDepartmentId(newDepartmentId);
    r.setNewStudentId(newStudentId); r.setStatus("PENDING");
    return changeRequests.save(r);
  }
}
