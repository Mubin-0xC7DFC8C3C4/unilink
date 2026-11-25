package com.unilink.profile;
import com.unilink.identity.domain.*;
import java.time.LocalDateTime;
/** Profile item DTOs (ARCH: entities never leave the service layer). Components mirror the entity JSON. */
public final class ProfileDtos {
  private ProfileDtos(){}
  /** D-203: githubOwner/githubRepo carry the Pin to Profile link (both
   *  null = not pinned); the profile renders pinned rows through
   *  LiveRepoViewer with real GitHub data. */
  public record ProjectDto(Long id, Long userId, String title, String description, String url, LocalDateTime createdAt,
                           String githubOwner, String githubRepo){
    public static ProjectDto from(Project p){ return new ProjectDto(p.getId(), p.getUserId(), p.getTitle(), p.getDescription(), p.getUrl(), p.getCreatedAt(), p.getGithubOwner(), p.getGithubRepo()); }
  }
  public record EducationEntryDto(Long id, Long userId, String institution, String degree, Integer year, LocalDateTime createdAt){
    public static EducationEntryDto from(EducationEntry e){ return new EducationEntryDto(e.getId(), e.getUserId(), e.getInstitution(), e.getDegree(), e.getYear(), e.getCreatedAt()); }
  }
  public record AchievementDto(Long id, Long userId, String title, String description, Integer year, LocalDateTime createdAt){
    public static AchievementDto from(Achievement a){ return new AchievementDto(a.getId(), a.getUserId(), a.getTitle(), a.getDescription(), a.getYear(), a.getCreatedAt()); }
  }
}
