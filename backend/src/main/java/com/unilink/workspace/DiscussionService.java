package com.unilink.workspace;

import com.unilink.common.Exceptions;
import com.unilink.identity.domain.ProjectDiscussion;
import com.unilink.identity.domain.User;
import com.unilink.identity.repo.GithubConnectionRepository;
import com.unilink.identity.repo.ProjectDiscussionRepository;
import com.unilink.identity.repo.UserRepository;
import com.unilink.security.AccessPolicy;
import com.unilink.security.Principals;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** UniLink-native project discussions (persisted; NOT GitHub Issues).
 *  Lives outside the api package so controllers stay free of entity and
 *  repository dependencies (architecture rules). */
@Service
public class DiscussionService {
  public record DiscussionDto(Long id, String repoFullName, Long authorId, String authorName, Long parentId,
                              String body, String fileRef, LocalDateTime createdAt, boolean authorIsCreator) {}
  public record DiscussionBody(String repo, String body, Long parentId, String fileRef) {}

  private final ProjectDiscussionRepository discussions;
  private final UserRepository users;
  private final GithubConnectionRepository connections;
  private final AccessPolicy policy;

  public DiscussionService(ProjectDiscussionRepository discussions, UserRepository users,
                           GithubConnectionRepository connections, AccessPolicy policy) {
    this.discussions = discussions; this.users = users; this.connections = connections; this.policy = policy;
  }

  private DiscussionDto toDto(ProjectDiscussion d, String repoOwnerLogin) {
    String name = users.findById(d.getAuthorId()).map(User::getFullName).orElse("Deleted user");
    boolean creator = repoOwnerLogin != null && connections.findByUserId(d.getAuthorId())
      .map(c -> c.getUsername().equalsIgnoreCase(repoOwnerLogin)).orElse(false);
    return new DiscussionDto(d.getId(), d.getRepoFullName(), d.getAuthorId(), name, d.getParentId(),
      d.getBody(), d.getFileRef(), d.getCreatedAt(), creator);
  }

  public List<DiscussionDto> list(String repoFullName) {
    String ownerLogin = repoFullName.contains("/") ? repoFullName.substring(0, repoFullName.indexOf('/')) : null;
    return discussions.findByRepoFullNameOrderByCreatedAtAsc(repoFullName).stream()
      .map(d -> toDto(d, ownerLogin)).toList();
  }

  public DiscussionDto post(Principals.UnilinkUserPrincipal principal, DiscussionBody b) {
    policy.requireVerified(principal);
    if (b.repo() == null || !b.repo().contains("/")) throw new Exceptions.BadRequest("INVALID_REPO", "Repository must be owner/name");
    if (b.body() == null || b.body().isBlank()) throw new Exceptions.BadRequest("EMPTY_BODY", "Comment cannot be empty");
    ProjectDiscussion d = new ProjectDiscussion();
    d.setRepoFullName(b.repo()); d.setAuthorId(principal.userId()); d.setBody(b.body());
    if (b.parentId() != null) {
      ProjectDiscussion parent = discussions.findById(b.parentId()).orElseThrow(() -> new Exceptions.NotFound("Discussion comment"));
      if (!parent.getRepoFullName().equals(b.repo())) throw new Exceptions.BadRequest("INVALID_PARENT", "Reply parent belongs to a different repository");
      d.setParentId(b.parentId());
    }
    d.setFileRef(b.fileRef());
    d = discussions.save(d);
    return toDto(d, b.repo().substring(0, b.repo().indexOf('/')));
  }
}
