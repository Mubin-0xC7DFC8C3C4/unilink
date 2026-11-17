package com.unilink.integration;
import com.unilink.identity.domain.*; import com.unilink.identity.repo.*;
import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
/** Builders for integration tests (TEST_STRATEGY §3): two universities, CSE catalog in both, batches. */
@Component
public class TestDataFactory {
  private final UniversityRepository universities; private final DepartmentCatalogRepository catalogs;
  private final DepartmentRepository departments; private final BatchRepository batches;
  private final UserRepository users; private final UserCredentialRepository creds; private final ProfileRepository profiles;
  private final VerificationRequestRepository requests; private final AdminAccountRepository admins;
  private final PostRepository posts;
  private final PasswordEncoder encoder;
  public TestDataFactory(UniversityRepository u, DepartmentCatalogRepository c, DepartmentRepository d,
      BatchRepository b, UserRepository users, UserCredentialRepository creds, ProfileRepository p,
      VerificationRequestRepository r, AdminAccountRepository a, PostRepository posts, PasswordEncoder e){
    universities=u; catalogs=c; departments=d; batches=b; this.users=users; this.creds=creds; profiles=p;
    requests=r; admins=a; this.posts=posts; encoder=e;
  }
  public record Fixture(University mu, University du, Department muCse, Department duCse, Batch muBatch, Batch duBatch) {}
  @Transactional
  public Fixture seedMaster(){
    University mu = universities.findByName("Metropolitan University").orElseGet(() -> {
      University x=new University(); x.setName("Metropolitan University"); x.setShortName("MU"); x.setActive(true); return universities.save(x); });
    University du = universities.findByName("University of Dhaka").orElseGet(() -> {
      University x=new University(); x.setName("University of Dhaka"); x.setShortName("DU"); x.setActive(true); return universities.save(x); });
    DepartmentCatalog cse = catalogs.findAll().stream().filter(c->"Computer Science and Engineering".equals(c.getName())).findFirst().orElseGet(() -> {
      DepartmentCatalog x=new DepartmentCatalog(); x.setName("Computer Science and Engineering"); x.setShortName("CSE"); x.setActive(true); return catalogs.save(x); });
    Department muCse = departments.findByUniversityIdAndCatalogId(mu.getId(), cse.getId()).orElseGet(() -> {
      Department x=new Department(); x.setUniversityId(mu.getId()); x.setCatalogId(cse.getId()); x.setName("CSE — MU"); x.setActive(true); return departments.save(x); });
    Department duCse = departments.findByUniversityIdAndCatalogId(du.getId(), cse.getId()).orElseGet(() -> {
      Department x=new Department(); x.setUniversityId(du.getId()); x.setCatalogId(cse.getId()); x.setName("CSE — DU"); x.setActive(true); return departments.save(x); });
    Batch muBatch = batches.findByUniversityIdAndDepartmentIdAndActiveTrue(mu.getId(), muCse.getId()).stream().findFirst().orElseGet(() -> {
      Batch x=new Batch(); x.setUniversityId(mu.getId()); x.setDepartmentId(muCse.getId()); x.setLabel("45"); x.setStartYear(2022); x.setActive(true); return batches.save(x); });
    Batch duBatch = batches.findByUniversityIdAndDepartmentIdAndActiveTrue(du.getId(), duCse.getId()).stream().findFirst().orElseGet(() -> {
      Batch x=new Batch(); x.setUniversityId(du.getId()); x.setDepartmentId(duCse.getId()); x.setLabel("45"); x.setStartYear(2022); x.setActive(true); return batches.save(x); });
    return new Fixture(mu, du, muCse, duCse, muBatch, duBatch);
  }
  @Transactional
  public User verifiedStudent(String email, String studentId, University uni, Department dept, Batch batch){
    User u = new User(); u.setFullName("Test "+studentId); u.setEmail(email); u.setPhone("+880100000000");
    u.setAddress("Test address"); u.setStudentId(studentId); u.setUniversityId(uni.getId());
    u.setDepartmentId(dept.getId()); u.setBatchId(batch.getId()); u.setState("VERIFIED");
    u.setPhoneVerifiedAt(LocalDateTime.now()); u.setEmailVerifiedAt(LocalDateTime.now());
    u = users.save(u);
    UserCredential c = new UserCredential(); c.setId(u.getId()); c.setPasswordHash(encoder.encode("Student123")); creds.save(c);
    Profile p = new Profile(); p.setId(u.getId()); p.setPhoneVisible(false); profiles.save(p);
    VerificationRequest r = new VerificationRequest(); r.setUserId(u.getId()); r.setStatus("APPROVED");
    r.setOcrStatus("DONE"); r.setOcrMatchScore(0.95); r.setDecidedAt(LocalDateTime.now()); requests.save(r);
    return u;
  }
  @Transactional
  public AdminAccount admin(String email, String role, Long universityId){
    AdminAccount a = new AdminAccount(); a.setEmail(email); a.setPasswordHash(encoder.encode("Admin12345678"));
    a.setRole(role); a.setUniversityId(universityId); a.setActive(true); a.setTotpEnrolled(false);
    return admins.save(a);
  }
  @Transactional
  public Post post(User author, String visibility, String title){
    Post p = new Post(); p.setAuthorId(author.getId()); p.setType("POST"); p.setVisibility(visibility);
    p.setScopeUniversityId(author.getUniversityId()); p.setScopeDepartmentId(author.getDepartmentId());
    p.setScopeCatalogId(departments.findById(author.getDepartmentId()).map(Department::getCatalogId).orElse(null));
    p.setTitle(title); p.setBody("Body of "+title); p.setStatus("ACTIVE");
    return posts.save(p);
  }
}
