package com.unilink.dev;
import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import org.slf4j.*; import org.springframework.boot.ApplicationArguments; import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile; import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
/** DEV-ONLY seeder (never runs in prod — @Profile("dev")). Demo accounts documented in docs/phase-reports/backend.md.
 *  Seed ID images are placeholders, not real files; seed verifications are pre-approved by the seeder. */
@Component @Profile("dev") @Order(2)
public class DevSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);
  private final UserRepository users; private final UserCredentialRepository creds; private final ProfileRepository profiles;
  private final AdminAccountRepository admins; private final UniversityRepository universities;
  private final DepartmentRepository departments; private final BatchRepository batches;
  private final VerificationRequestRepository requests; private final PostRepository posts; private final EventDetailRepository events;
  private final DepartmentCatalogRepository catalogs;
  private final PasswordEncoder encoder;
  public DevSeeder(UserRepository users, UserCredentialRepository creds, ProfileRepository profiles,
      AdminAccountRepository admins, UniversityRepository universities, DepartmentRepository departments,
      BatchRepository batches, VerificationRequestRepository requests, PostRepository posts,
      EventDetailRepository events, DepartmentCatalogRepository catalogs, PasswordEncoder encoder){
    this.users=users; this.creds=creds; this.profiles=profiles; this.admins=admins; this.universities=universities;
    this.departments=departments; this.batches=batches; this.requests=requests; this.posts=posts; this.events=events;
    this.catalogs=catalogs; this.encoder=encoder;
  }
  @Override @Transactional public void run(ApplicationArguments args){
    if (users.count() > 0) { log.info("Dev seeder skipped: users already exist"); return; }
    University mu = universities.findByName("Metropolitan University").orElseThrow();
    University du = universities.findByName("University of Dhaka").orElseThrow();
    Department muCse = departments.findByUniversityIdAndCatalogId(mu.getId(), catalogId("Computer Science and Engineering")).orElseThrow();
    Department duCse = departments.findByUniversityIdAndCatalogId(du.getId(), catalogId("Computer Science and Engineering")).orElseThrow();
    Batch muBatch = batches.findByUniversityIdAndDepartmentIdAndActiveTrue(mu.getId(), muCse.getId()).stream().findFirst().orElseThrow();
    Batch duBatch = batches.findByUniversityIdAndDepartmentIdAndActiveTrue(du.getId(), duCse.getId()).stream().findFirst().orElseThrow();
    moderator("mod.mu@unilink.dev", mu.getId());
    moderator("mod.du@unilink.dev", du.getId());
    User rahim = student("Rahim Uddin","rahim@student.mu.edu.bd","+8801711111111","House 12, Road 5, Dhaka","MU-CSE-45001", mu, muCse, muBatch, true);
    User nusrat = student("Nusrat Jahan","nusrat@student.mu.edu.bd","+8801711111112","House 3, Road 2, Dhaka","MU-CSE-45002", mu, muCse, muBatch, true);
    User tanvir = student("Tanvir Hasan","tanvir@student.du.edu.bd","+8801811111111","Hall 1, DU Campus","DU-CSE-45001", du, duCse, duBatch, true);
    student("Arif Chowdhury","arif@student.mu.edu.bd","+8801711111113","House 9, Dhaka","MU-CSE-45003", mu, muCse, muBatch, false);
    post(rahim, "POST","DEPARTMENT","Welcome to CSE 45","Hello everyone! This is our department space.", null);
    post(nusrat, "POST","UNIVERSITY","MU Hackathon prep","Forming teams for the national hackathon — reply if interested.", null);
    post(tanvir, "POST","DEPARTMENT_HUB","CSE across universities","Sharing notes for the data structures course.", null);
    post(rahim, "POST","NATIONAL","Open source in Bangladesh","A few thoughts on contributing to open source as a student.", null);
    Post ev = post(nusrat, "EVENT","NATIONAL","National Hackathon 2026","48-hour hackathon. Register on the external site.", null);
    EventDetail ed = new EventDetail(); ed.setId(ev.getId()); ed.setDeadline(LocalDateTime.now().plusDays(30));
    ed.setRegLink("https://example.org/hackathon-2026"); ed.setLocation("Dhaka"); ed.setRegistrationClosed(false); events.save(ed);
    log.warn("Dev seed complete: 2 moderators, 3 verified students, 1 unverified student, sample posts/event. Demo passwords are in docs/phase-reports/backend.md (DEV ONLY).");
  }
  private Long catalogId(String name){
    return catalogs.findAll().stream().filter(c -> name.equals(c.getName())).findFirst()
      .orElseThrow(() -> new IllegalStateException("Catalog not seeded: "+name)).getId();
  }
  private void moderator(String email, Long universityId){
    if (admins.findByEmail(email).isPresent()) return;
    AdminAccount a = new AdminAccount(); a.setEmail(email); a.setPasswordHash(encoder.encode("Moderator123!"));
    a.setRole("UNIVERSITY_MODERATOR"); a.setUniversityId(universityId); a.setActive(true); a.setTotpEnrolled(false);
    admins.save(a);
  }
  private User student(String name, String email, String phone, String address, String studentId,
                       University uni, Department dept, Batch batch, boolean verified){
    User u = new User(); u.setFullName(name); u.setEmail(email); u.setPhone(phone); u.setAddress(address);
    u.setStudentId(studentId); u.setUniversityId(uni.getId()); u.setDepartmentId(dept.getId()); u.setBatchId(batch.getId());
    u.setState(verified?"VERIFIED":"REGISTERED");
    if (verified){ u.setPhoneVerifiedAt(LocalDateTime.now()); u.setEmailVerifiedAt(LocalDateTime.now()); }
    u.setProfilePhotoKey("seed/placeholder-photo"); u.setIdCardKey("seed/placeholder-id");
    u = users.save(u);
    UserCredential c = new UserCredential(); c.setId(u.getId()); c.setPasswordHash(encoder.encode("Student123")); creds.save(c);
    Profile p = new Profile(); p.setId(u.getId()); p.setPhoneVisible(false); profiles.save(p);
    VerificationRequest r = new VerificationRequest(); r.setUserId(u.getId());
    r.setStatus(verified?"APPROVED":"AWAITING_VERIFICATION"); r.setOcrStatus("DONE"); r.setOcrMatchScore(verified?0.95:0.0);
    r.setIdCardKey("seed/placeholder-id"); r.setProfilePhotoKey("seed/placeholder-photo");
    if (verified) r.setDecidedAt(LocalDateTime.now());
    requests.save(r);
    return u;
  }
  private Post post(User author, String type, String visibility, String title, String body, String unused){
    Post p = new Post(); p.setAuthorId(author.getId()); p.setType(type); p.setVisibility(visibility);
    p.setScopeUniversityId(author.getUniversityId()); p.setScopeDepartmentId(author.getDepartmentId());
    p.setScopeCatalogId(departments.findById(author.getDepartmentId()).map(Department::getCatalogId).orElse(null));
    p.setTitle(title); p.setBody(body); p.setStatus("ACTIVE");
    return posts.save(p);
  }
}
