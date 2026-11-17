package com.unilink.identity.service;
import com.unilink.audit.AuditService;
import com.unilink.common.Exceptions;
import com.unilink.identity.domain.User;
import com.unilink.identity.domain.UserCredential;
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
  private final StorageGateway storage;
  private final PasswordEncoder encoder;
  private final SessionService sessions;
  private final AuditService audit;
  public AccountDeletionService(UserRepository users, UserCredentialRepository creds,
      VerificationRequestRepository requests, StorageGateway storage, PasswordEncoder encoder,
      SessionService sessions, AuditService audit){
    this.users=users; this.creds=creds; this.requests=requests; this.storage=storage;
    this.encoder=encoder; this.sessions=sessions; this.audit=audit;
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
    requests.findTopByUserIdOrderBySubmittedAtDesc(userId).ifPresent(r -> {
      if (r.getIdCardKey()!=null) keys.add(r.getIdCardKey());
      if (r.getProfilePhotoKey()!=null) keys.add(r.getProfilePhotoKey());
    });
    keys.forEach(storage::delete);
    audit.write(null, "ACCOUNT_DELETED", "USER", userId,
      "Self-service account deletion (NFR-PRV-02, D-052)", Map.of("state", "DELETED"), ip);
    sessions.invalidateAllSessions(u.getEmail());
    users.delete(u);
  }
}
