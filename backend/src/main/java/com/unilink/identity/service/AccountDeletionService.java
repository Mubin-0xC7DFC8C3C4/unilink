package com.unilink.identity.service;
import com.unilink.audit.AuditService;
import com.unilink.common.Exceptions;
import com.unilink.identity.domain.Attachment;
import com.unilink.identity.domain.Post;
import com.unilink.identity.domain.Profile;
import com.unilink.identity.domain.User;
import com.unilink.identity.domain.UserCredential;
import com.unilink.identity.domain.VerificationRequest;
import com.unilink.identity.repo.AttachmentRepository;
import com.unilink.identity.repo.PostRepository;
import com.unilink.identity.repo.ProfileRepository;
import com.unilink.identity.repo.UserCredentialRepository;
import com.unilink.identity.repo.UserRepository;
import com.unilink.identity.repo.VerificationRequestRepository;
import com.unilink.storage.StorageGateway;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
/** Self-service account deletion (SRS NFR-PRV-02, D-052): re-authenticate with
 *  the password, then anonymise-and-delete in one transaction. The users row
 *  is hard-deleted; every user-owned row follows via ON DELETE CASCADE and
 *  sent messages stay with sender_id SET NULL (shown as "Deleted user").
 *  Private storage objects (ID card, profile photo) are deleted, all sessions
 *  are invalidated, and the audit row keeps only the numeric id, which no
 *  longer resolves to a person. */
@Service
public class AccountDeletionService {
  private final UserRepository users;
  private final UserCredentialRepository creds;
  private final VerificationRequestRepository requests;
  private final ProfileRepository profiles;
  private final PostRepository posts;
  private final AttachmentRepository attachments;
  private final StorageGateway storage;
  private final PasswordEncoder encoder;
  private final SessionService sessions;
  private final AuditService audit;
  public AccountDeletionService(UserRepository users, UserCredentialRepository creds,
      VerificationRequestRepository requests, StorageGateway storage, PasswordEncoder encoder,
      SessionService sessions, AuditService audit, ProfileRepository profiles,
      PostRepository posts, AttachmentRepository attachments){
    this.users=users; this.creds=creds; this.requests=requests; this.storage=storage;
    this.encoder=encoder; this.sessions=sessions; this.audit=audit;
    this.profiles=profiles; this.posts=posts; this.attachments=attachments;
  }
  @Transactional
  public void deleteOwnAccount(Long userId, String password, String ip){
    User u = users.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    if ("DELETED".equals(u.getState()))
      throw new Exceptions.Conflict("ALREADY_DELETED", "Account is already deleted");
    UserCredential c = creds.findById(userId).orElseThrow(() -> new Exceptions.NotFound("User"));
    if (password==null || !encoder.matches(password, c.getPasswordHash()))
      throw new Exceptions.BadRequest("INVALID_CREDENTIALS", "Password is incorrect");
    Set<String> keys = new HashSet<>();
    if (u.getIdCardKey()!=null) keys.add(u.getIdCardKey());
    if (u.getProfilePhotoKey()!=null) keys.add(u.getProfilePhotoKey());
    // D-155: the profile cover photo was never collected, so its file
    // orphaned in storage after deletion (NFR-PRV-02: the user's images
    // are supposed to go with the account).
    profiles.findById(userId).map(Profile::getCoverKey).ifPresent(keys::add);
    // D-155: the user's posts cascade-delete with the users row, but the
    // attachments table has no FK to posts (polymorphic owner), so both
    // the attachment rows and their files would orphan. Collect them here
    // and remove both. Message attachments are deliberately NOT swept:
    // sent messages stay (sender SET NULL) as conversation history, so
    // their attachments stay readable to the other participants.
    for (Post p : posts.findByAuthorId(userId)){
      for (Attachment a : attachments.findByOwnerTypeAndOwnerId("POST", p.getId())){
        if (a.getStorageKey()!=null) keys.add(a.getStorageKey());
      }
      attachments.deleteByOwnerTypeAndOwnerId("POST", p.getId());
    }
    keys.forEach(storage::delete);
    // I-18: collect storage keys from ALL of the user's verification requests,
    // not only the latest — a resubmitted request leaves an older ID image
    // (and photo) behind that would otherwise orphan in private storage.
    for (VerificationRequest r : requests.findByUserIdOrderBySubmittedAtDesc(userId)){
      if (r.getIdCardKey()!=null) keys.add(r.getIdCardKey());
      if (r.getProfilePhotoKey()!=null) keys.add(r.getProfilePhotoKey());
    }
    keys.forEach(storage::delete);
    audit.write(null, "ACCOUNT_DELETED", "USER", userId,
      "Self-service account deletion (NFR-PRV-02, D-052)", Map.of("state", "DELETED"), ip);
    sessions.invalidateAllSessions(u.getEmail());
    users.delete(u);
  }
}
