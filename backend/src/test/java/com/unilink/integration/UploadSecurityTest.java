package com.unilink.integration;

import com.unilink.common.Exceptions;
import com.unilink.content.PostService;
import com.unilink.identity.domain.*;
import com.unilink.identity.dto.AuthDtos.RegisterForm;
import com.unilink.identity.repo.*;
import com.unilink.identity.service.AuthService;
import com.unilink.messaging.MessagingService;
import com.unilink.profile.ProfileService;
import com.unilink.security.Principals;
import com.unilink.storage.StorageGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** File-upload security (Worker C, Phase 3): EXIF stripping on every image
 *  upload path, the per-post attachment cap, ownership/scope checks, size and
 *  type limits, and traversal-safe file names — all against the real services
 *  on H2. No live MySQL is involved. */
@SpringBootTest
@Import(TestProviders.class)
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:unilink-uploadsec;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.flyway.enabled=false",
  "spring.jpa.hibernate.ddl-auto=create-drop",
  "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
  "app.storage.local-path=./target/test-storage"
})
@Transactional
class UploadSecurityTest {
  @Autowired TestDataFactory factory;
  @Autowired PostService posts;
  @Autowired ProfileService profiles;
  @Autowired MessagingService messaging;
  @Autowired AuthService auth;
  @Autowired StorageGateway storage;
  @Autowired UserRepository users;
  @Autowired ProfileRepository profileRepo;
  @Autowired DepartmentRepository departments;
  @Autowired AttachmentRepository attachments;
  @Autowired MessageAttachmentRepository messageAttachments;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach void createH2OnlyTables(){
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION (PRIMARY_ID CHAR(36) PRIMARY KEY, SESSION_ID CHAR(36), CREATION_TIME BIGINT, LAST_ACCESS_TIME BIGINT, MAX_INACTIVE_INTERVAL INT, EXPIRY_TIME TIMESTAMP, PRINCIPAL_NAME VARCHAR(100))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID CHAR(36), ATTRIBUTE_NAME VARCHAR(200), ATTRIBUTE_BYTES BLOB, PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME))");
    TestProviders.reset();
  }

  // ---- fixtures ----
  private static byte[] segment(int marker, byte[] payload){
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    b.write(0xFF); b.write(marker);
    int len = payload.length + 2;
    b.write((len >> 8) & 0xFF); b.write(len & 0xFF);
    b.write(payload, 0, payload.length);
    return b.toByteArray();
  }
  private static byte[] concat(byte[]... parts){
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    for (byte[] p : parts) b.write(p, 0, p.length);
    return b.toByteArray();
  }
  private static final byte[] GPS = "GPSLAT37.7749N_GPSLON122.4194W".getBytes(StandardCharsets.US_ASCII);
  private static byte[] jpegWithGpsExif(){
    return concat(new byte[]{(byte)0xFF, (byte)0xD8},
      segment(0xE1, concat("Exif\0\0".getBytes(StandardCharsets.US_ASCII),
        "II*\0\u0008\0\0\0".getBytes(StandardCharsets.ISO_8859_1), GPS)),
      segment(0xDA, new byte[]{1, 2, 3}), new byte[]{0x11, 0x22},
      new byte[]{(byte)0xFF, (byte)0xD9});
  }
  private static byte[] pngWithExif(){
    return concat(new byte[]{(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10},
      pngChunk("IHDR", new byte[]{0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0}),
      pngChunk("eXIf", concat("II*\0\u0008\0\0\0".getBytes(StandardCharsets.ISO_8859_1), GPS)),
      pngChunk("IDAT", new byte[]{0x08, 0x11, 0x22, 0x33}),
      pngChunk("IEND", new byte[0]));
  }
  private static byte[] pngChunk(String type, byte[] data){
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    int len = data.length;
    b.write((len >> 24) & 0xFF); b.write((len >> 16) & 0xFF); b.write((len >> 8) & 0xFF); b.write(len & 0xFF);
    b.write(type.getBytes(StandardCharsets.US_ASCII), 0, 4);
    b.write(data, 0, data.length);
    b.write(new byte[]{0, 0, 0, 0}, 0, 4);
    return b.toByteArray();
  }
  private static byte[] webpWithExif(){
    byte[] vp8x = {0x08, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    byte[] exif = concat("II*\0\u0008\0\0\0".getBytes(StandardCharsets.ISO_8859_1), GPS, new byte[]{0x5A});
    byte[] in = concat("RIFF".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 0, 0, 0},
      "WEBP".getBytes(StandardCharsets.US_ASCII),
      webpChunk("VP8X", vp8x), webpChunk("EXIF", exif), webpChunk("VP8 ", new byte[]{0x11, 0x22, 0x33, 0x44}));
    int riffSize = in.length - 8;
    in[4] = (byte)(riffSize & 0xFF); in[5] = (byte)((riffSize >> 8) & 0xFF);
    in[6] = (byte)((riffSize >> 16) & 0xFF); in[7] = (byte)((riffSize >> 24) & 0xFF);
    return in;
  }
  private static byte[] webpChunk(String type, byte[] data){
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    b.write(type.getBytes(StandardCharsets.US_ASCII), 0, 4);
    int len = data.length;
    b.write(len & 0xFF); b.write((len >> 8) & 0xFF); b.write((len >> 16) & 0xFF); b.write((len >> 24) & 0xFF);
    b.write(data, 0, data.length);
    if (data.length % 2 == 1) b.write(0);
    return b.toByteArray();
  }
  private static byte[] plainPng(){
    return new byte[]{(byte)0x89,'P','N','G',13,10,26,10,1,2,3,4};
  }
  private static byte[] pdfBytes(int size){
    byte[] b = new byte[size];
    byte[] head = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(head, 0, b, 0, head.length);
    return b;
  }
  private static boolean contains(byte[] haystack, byte[] needle){
    outer: for (int i = 0; i + needle.length <= haystack.length; i++){
      for (int j = 0; j < needle.length; j++) if (haystack[i+j]!=needle[j]) continue outer;
      return true;
    }
    return false;
  }
  private static boolean containsText(byte[] haystack, String text){
    return contains(haystack, text.getBytes(StandardCharsets.US_ASCII));
  }

  private Principals.UnilinkUserPrincipal principal(User u){
    Long catalog = departments.findById(u.getDepartmentId()).map(Department::getCatalogId).orElse(null);
    return new Principals.UnilinkUserPrincipal(u.getId(), u.getEmail(), "x", u.getUniversityId(),
      u.getDepartmentId(), catalog, u.getState(), true);
  }
  private record Students(User a, User b, User c, User du) {}
  private Students fixture(){
    var f = factory.seedMaster();
    User a = factory.verifiedStudent("up-a@example.com", "UPA", f.mu(), f.muCse(), f.muBatch());
    User b = factory.verifiedStudent("up-b@example.com", "UPB", f.mu(), f.muCse(), f.muBatch());
    User c = factory.verifiedStudent("up-c@example.com", "UPC", f.mu(), f.muCse(), f.muBatch());
    User du = factory.verifiedStudent("up-du@example.com", "UPDU", f.du(), f.duCse(), f.duBatch());
    return new Students(a, b, c, du);
  }
  private User user(String email){ return users.findByEmail(email).orElseThrow(); }
  private byte[] storedBytes(String key) throws Exception {
    assertNotNull(key, "expected a storage key");
    return storage.open(key).readAllBytes();
  }

  @Test void profilePhotoExifIsStripped() throws Exception {
    var s = fixture();
    profiles.uploadPhoto(s.a().getId(), "photo.jpg", "image/jpeg", jpegWithGpsExif());
    byte[] stored = storedBytes(user("up-a@example.com").getProfilePhotoKey());
    assertFalse(containsText(stored, "Exif"), "stored photo must not contain EXIF");
    assertFalse(contains(stored, GPS), "stored photo must not contain the GPS payload");
    assertTrue(contains(stored, new byte[]{0x11, 0x22}), "image data must survive stripping");
  }

  @Test void coverExifIsStrippedAndWebpPhotoExifIsStripped() throws Exception {
    var s = fixture();
    profiles.uploadCover(s.a().getId(), "cover.png", "image/png", pngWithExif());
    byte[] cover = storedBytes(profileRepo.findById(s.a().getId()).orElseThrow().getCoverKey());
    assertFalse(containsText(cover, "eXIf"), "stored cover must not contain an eXIf chunk");
    assertFalse(contains(cover, GPS), "stored cover must not contain the GPS payload");
    profiles.uploadPhoto(s.b().getId(), "photo.webp", "image/webp", webpWithExif());
    byte[] photo = storedBytes(user("up-b@example.com").getProfilePhotoKey());
    assertFalse(contains(photo, GPS), "stored WebP photo must not contain the GPS payload");
    int riffSize = (photo[4] & 0xFF) | ((photo[5] & 0xFF) << 8) | ((photo[6] & 0xFF) << 16) | ((photo[7] & 0xFF) << 24);
    assertEquals(photo.length - 8, riffSize, "stored WebP RIFF size must match the stripped file");
  }

  @Test void idCardAndPhotoExifAreStrippedOnRegister() throws Exception {
    var f = factory.seedMaster();
    RegisterForm form = new RegisterForm();
    form.setFullName("Upload Sec"); form.setPhone("01712345678"); form.setEmail("up-new@example.com");
    form.setUniversityId(f.mu().getId()); form.setDepartmentId(f.muCse().getId()); form.setBatch("44");
    form.setAddress("Test address"); form.setStudentId("UPNEW"); form.setPassword("Student123");
    form.setIdCard(new MockMultipartFile("idCard", "id.jpg", "image/jpeg", jpegWithGpsExif()));
    form.setIdCardBack(new MockMultipartFile("idCardBack", "id-back.png", "image/png", pngWithExif()));
    form.setProfilePhoto(new MockMultipartFile("profilePhoto", "photo.png", "image/png", pngWithExif()));
    auth.register(form);
    User u = user("up-new@example.com");
    assertFalse(containsText(storedBytes(u.getIdCardKey()), "Exif"), "stored ID card must not contain EXIF");
    assertFalse(contains(storedBytes(u.getIdCardKey()), GPS), "stored ID card must not contain the GPS payload");
    assertFalse(contains(storedBytes(u.getProfilePhotoKey()), GPS), "stored sign-up photo must not contain GPS");
  }

  @Test void resubmittedIdCardExifIsStripped() throws Exception {
    var f = factory.seedMaster();
    User u = new User();
    u.setFullName("Rejected One"); u.setEmail("up-rej@example.com"); u.setPhone("01712345679");
    u.setAddress("Test address"); u.setStudentId("UPREJ"); u.setUniversityId(f.mu().getId());
    u.setDepartmentId(f.muCse().getId()); u.setBatchId(f.muBatch().getId()); u.setState("REJECTED");
    u = users.save(u);
    auth.resubmit(u.getId(), new MockMultipartFile("idCard", "id.jpg", "image/jpeg", jpegWithGpsExif()), null);
    byte[] stored = storedBytes(user("up-rej@example.com").getIdCardKey());
    assertFalse(containsText(stored, "Exif"), "resubmitted ID card must not contain EXIF");
    assertFalse(contains(stored, GPS), "resubmitted ID card must not contain the GPS payload");
  }

  @Test void postAttachmentCountIsCappedAtFive() {
    var s = fixture();
    var p = principal(s.a());
    var post = posts.createPost(p, "POST", "DEPARTMENT", "t", "b", null);
    for (int i = 1; i <= 5; i++)
      posts.addPostAttachment(p, post.id(), "a" + i + ".png", "image/png", plainPng());
    assertEquals(5, attachments.countByOwnerTypeAndOwnerId("POST", post.id()));
    var e = assertThrows(Exceptions.BadRequest.class,
      () -> posts.addPostAttachment(p, post.id(), "a6.png", "image/png", plainPng()));
    assertEquals("ATTACHMENT_LIMIT", e.code());
    assertEquals(400, e.status().value(), "over the limit must be a 400");
    assertEquals(5, attachments.countByOwnerTypeAndOwnerId("POST", post.id()),
      "the rejected 6th file must not be stored");
  }

  @Test void postAttachmentExifIsStrippedAndPdfPassesThrough() throws Exception {
    var s = fixture();
    var p = principal(s.a());
    var post = posts.createPost(p, "POST", "DEPARTMENT", "t", "b", null);
    var img = posts.addPostAttachment(p, post.id(), "pic.jpg", "image/jpeg", jpegWithGpsExif());
    String imgKey = attachments.findByOwnerTypeAndOwnerId("POST", post.id()).stream()
      .filter(a -> a.getId().equals(img.id())).findFirst().orElseThrow().getStorageKey();
    assertFalse(contains(storedBytes(imgKey), GPS), "stored post image must not contain the GPS payload");
    var pdf = posts.addPostAttachment(p, post.id(), "doc.pdf", "application/pdf", pdfBytes(100));
    assertEquals("application/pdf", pdf.contentType());
    assertEquals(Long.valueOf(100), pdf.sizeBytes());
  }

  @Test void wrongOwnerCannotAddOrDeletePostAttachment() throws Exception {
    var s = fixture();
    var post = posts.createPost(principal(s.a()), "POST", "DEPARTMENT", "t", "b", null);
    assertThrows(Exceptions.NotFound.class,
      () -> posts.addPostAttachment(principal(s.b()), post.id(), "x.png", "image/png", plainPng()),
      "only the post owner may attach");
    var att = posts.addPostAttachment(principal(s.a()), post.id(), "x.png", "image/png", plainPng());
    String key = attachments.findById(att.id()).orElseThrow().getStorageKey();
    assertThrows(Exceptions.NotFound.class,
      () -> posts.deletePostAttachment(principal(s.b()), post.id(), att.id()),
      "a non-owner delete must 404, not reveal the attachment");
    assertTrue(storage.exists(key), "failed non-owner delete must leave the file in place");
    posts.deletePostAttachment(principal(s.a()), post.id(), att.id());
    assertTrue(attachments.findById(att.id()).isEmpty(), "owner delete removes the row");
    assertFalse(storage.exists(key), "owner delete removes the stored object too");
  }

  @Test void deletedPostAttachmentsAreNotDownloadable() {
    var s = fixture();
    var post = posts.createPost(principal(s.a()), "POST", "DEPARTMENT", "t", "b", null);
    var att = posts.addPostAttachment(principal(s.a()), post.id(), "x.png", "image/png", plainPng());
    posts.deletePost(principal(s.a()), post.id());
    assertThrows(Exceptions.NotFound.class, () -> posts.listPostAttachments(principal(s.a()), post.id()));
    assertThrows(Exceptions.NotFound.class,
      () -> posts.openPostAttachment(principal(s.a()), post.id(), att.id()));
  }

  @Test void postAttachmentDownloadFollowsPostVisibility() throws Exception {
    var s = fixture();
    var dept = posts.createPost(principal(s.a()), "POST", "DEPARTMENT", "Dept only", "b", null);
    var deptAtt = posts.addPostAttachment(principal(s.a()), dept.id(), "x.png", "image/png", plainPng());
    // An unverified student cannot see DEPARTMENT content (D-016).
    User unverifiedRow = new User();
    unverifiedRow.setFullName("Unverified"); unverifiedRow.setEmail("up-unv@example.com"); unverifiedRow.setPhone("01712345680");
    unverifiedRow.setAddress("x"); unverifiedRow.setStudentId("UPUNV"); unverifiedRow.setUniversityId(s.a().getUniversityId());
    unverifiedRow.setDepartmentId(s.a().getDepartmentId()); unverifiedRow.setBatchId(s.a().getBatchId()); unverifiedRow.setState("REGISTERED");
    final User unverified = users.save(unverifiedRow);
    assertThrows(Exceptions.NotFound.class,
      () -> posts.openPostAttachment(principal(unverified), dept.id(), deptAtt.id()));
    // The same unverified student CAN download a NATIONAL post's attachment.
    var nat = posts.createPost(principal(s.a()), "POST", "NATIONAL", "National", "b", null);
    var natAtt = posts.addPostAttachment(principal(s.a()), nat.id(), "x.png", "image/png", plainPng());
    assertNotNull(posts.openPostAttachment(principal(unverified), nat.id(), natAtt.id()));
  }

  @Test void messageAttachmentExifStrippedAndOnePerMessage() throws Exception {
    var s = fixture();
    var room = messaging.createPrivateRoom(principal(s.a()), s.b().getId());
    var msg = messaging.sendMessageWithAttachment(principal(s.a()), room.publicId(), "see attached",
      "pic.jpg", "image/jpeg", jpegWithGpsExif());
    assertTrue(msg.hasAttachment());
    var atts = messageAttachments.findByMessageId(msg.id());
    assertEquals(1, atts.size(), "one attachment per message by API design (D-105)");
    assertFalse(contains(storedBytes(atts.get(0).getStorageKey()), GPS),
      "stored chat image must not contain the GPS payload");
    assertEquals("pic.jpg", atts.get(0).getFileName());
  }

  @Test void nonMemberCannotListOrDownloadMessageAttachment() {
    var s = fixture();
    var room = messaging.createPrivateRoom(principal(s.a()), s.b().getId());
    var msg = messaging.sendMessageWithAttachment(principal(s.a()), room.publicId(), "hi",
      "x.png", "image/png", plainPng());
    var attId = messageAttachments.findByMessageId(msg.id()).get(0).getId();
    assertThrows(Exceptions.NotFound.class,
      () -> messaging.listMessageAttachments(principal(s.c()), room.publicId(), msg.id()));
    assertThrows(Exceptions.NotFound.class,
      () -> messaging.openMessageAttachment(principal(s.c()), room.publicId(), msg.id(), attId));
    // A member of the room can.
    assertEquals(1, messaging.listMessageAttachments(principal(s.b()), room.publicId(), msg.id()).size());
  }

  @Test void oversizedAndWrongTypeUploadsAreRejected() {
    var s = fixture();
    byte[] overPhoto = new byte[2 * 1024 * 1024 + 1];
    System.arraycopy(plainPng(), 0, overPhoto, 0, plainPng().length);
    assertThrows(Exceptions.BadRequest.class,
      () -> profiles.uploadPhoto(s.a().getId(), "big.png", "image/png", overPhoto));
    byte[] overAtt = new byte[10 * 1024 * 1024 + 1];
    System.arraycopy(plainPng(), 0, overAtt, 0, plainPng().length);
    var post = posts.createPost(principal(s.a()), "POST", "DEPARTMENT", "t", "b", null);
    var e = assertThrows(Exceptions.BadRequest.class,
      () -> posts.addPostAttachment(principal(s.a()), post.id(), "big.png", "image/png", overAtt));
    assertEquals("FILE_TOO_LARGE", e.code());
    byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 1, 2, 3, 4};
    assertThrows(Exceptions.BadRequest.class,
      () -> profiles.uploadPhoto(s.a().getId(), "a.gif", "image/gif", gif), "GIF is not an allowed type");
    assertThrows(Exceptions.BadRequest.class,
      () -> profiles.uploadPhoto(s.a().getId(), "doc.pdf", "application/pdf", pdfBytes(100)),
      "PDF is not a profile photo");
    assertThrows(Exceptions.BadRequest.class,
      () -> profiles.uploadCover(s.a().getId(), "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]),
      "cover over 5 MB must be rejected");
  }

  @Test void boundarySizesPass() {
    var s = fixture();
    byte[] photoAtCap = new byte[2 * 1024 * 1024];
    System.arraycopy(plainPng(), 0, photoAtCap, 0, plainPng().length);
    assertDoesNotThrow(() -> profiles.uploadPhoto(s.a().getId(), "cap.png", "image/png", photoAtCap));
    var post = posts.createPost(principal(s.a()), "POST", "DEPARTMENT", "t", "b", null);
    assertDoesNotThrow(() -> posts.addPostAttachment(principal(s.a()), post.id(), "cap.pdf", "application/pdf", pdfBytes(10 * 1024 * 1024)));
  }

  @Test void traversalFileNameIsSanitised() {
    var s = fixture();
    profiles.uploadPhoto(s.a().getId(), "../../../evil.png", "image/png", plainPng());
    String key = user("up-a@example.com").getProfilePhotoKey();
    assertTrue(key.startsWith("profile-photos/"), "stored key must stay in its namespace");
    assertFalse(key.contains(".."), "stored key must not contain a traversal");
    assertTrue(storage.exists(key));
    var post = posts.createPost(principal(s.a()), "POST", "DEPARTMENT", "t", "b", null);
    var att = posts.addPostAttachment(principal(s.a()), post.id(), "a\"b\r\n.png", "image/png", plainPng());
    assertEquals("a_b__.png", att.fileName(), "stored file name must be header-safe");
    assertFalse(att.fileName().contains("\"") || att.fileName().contains("\r") || att.fileName().contains("\n"));
  }

  @Test void coverReplacementDeletesTheOldFile() throws Exception {
    var s = fixture();
    profiles.uploadCover(s.a().getId(), "c1.png", "image/png", plainPng());
    String key1 = profileRepo.findById(s.a().getId()).orElseThrow().getCoverKey();
    assertTrue(storage.exists(key1));
    profiles.uploadCover(s.a().getId(), "c2.png", "image/png", plainPng());
    String key2 = profileRepo.findById(s.a().getId()).orElseThrow().getCoverKey();
    assertNotEquals(key1, key2);
    assertFalse(storage.exists(key1), "replacing the cover must delete the previous object");
    assertTrue(storage.exists(key2));
  }
}
