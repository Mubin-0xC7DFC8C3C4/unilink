package com.unilink.storage;
import com.unilink.common.Exceptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Upload validation hardening (D-106/D-107): magic-byte sniffing must reject
 *  look-alikes (JPEG with a non-marker 4th byte, PNG without its full 8-byte
 *  signature, GIF), size limits must bite exactly at the D-029a caps, and
 *  file names must be sanitised for storage and for Content-Disposition. */
class UploadValidatorTest {
  private final UploadValidator validator = new UploadValidator();
  private static final byte[] PNG = {(byte)0x89,'P','N','G',13,10,26,10,1,2,3,4};
  private static final byte[] WEBP = {'R','I','F','F',20,0,0,0,'W','E','B','P','V','P','8',' ',1,2,3};
  private static final byte[] WAV = {'R','I','F','F',20,0,0,0,'W','A','V','E','f','m','t',' ',1,2,3};

  @Test void riffWithoutWebpIsRejected() {
    assertThrows(Exceptions.BadRequest.class,
      () -> validator.sniffContentType(WAV, "audio/wav"));
  }

  @Test void riffWithWebpPasses() {
    assertEquals("image/webp", validator.sniffContentType(WEBP, "image/webp"));
  }

  @Test void pngStillPasses() {
    assertEquals("image/png", validator.sniffContentType(PNG, "image/png"));
  }

  @Test void shortRiffIsRejected() {
    assertThrows(Exceptions.BadRequest.class,
      () -> validator.sniffContentType(new byte[]{'R','I','F','F'}, null));
  }

  @Test void jpegNeedsRealMarkerAsFourthByte() {
    assertEquals("image/jpeg",
      validator.sniffContentType(new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE1,1,2,3,4}, "image/jpeg"));
    assertEquals("image/jpeg",
      validator.sniffContentType(new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,1,2,3,4}, "image/jpeg"));
    assertThrows(Exceptions.BadRequest.class,
      () -> validator.sniffContentType(new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF,0x00,1,2,3,4}, "image/jpeg"),
      "FF D8 FF 00 is not a JPEG");
  }

  @Test void pngNeedsFullEightByteSignature() {
    assertThrows(Exceptions.BadRequest.class,
      () -> validator.sniffContentType(new byte[]{(byte)0x89,'P','N','G',0,0,0,0}, "image/png"),
      "4-byte PNG prefix without the rest of the signature is not a PNG");
  }

  @Test void gifAndTextAreRejected() {
    assertThrows(Exceptions.BadRequest.class,
      () -> validator.sniffContentType(new byte[]{'G','I','F','8','9','a',1,2}, "image/gif"));
    assertThrows(Exceptions.BadRequest.class,
      () -> validator.sniffContentType("hello world, not an image".getBytes(), "text/plain"));
  }

  @Test void pdfIsAnAttachmentNotAnImage() {
    byte[] pdf = "%PDF-1.7\n1 2 3".getBytes();
    assertEquals("application/pdf", validator.validateAttachment(pdf, UploadValidator.ATTACHMENT_MAX));
    assertThrows(Exceptions.BadRequest.class,
      () -> validator.validateImage(pdf, UploadValidator.PHOTO_MAX, "application/pdf"));
  }

  @Test void declaredTypeAndExtensionAreIgnoredSniffingGoverns() {
    // Declared as PNG, actually a JPEG: the sniffed type wins.
    assertEquals("image/jpeg", validator.sniffContentType(
      new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,1,2,3,4}, "image/png"));
  }

  @Test void photoSizeLimitBitesExactlyAtCap() {
    byte[] atCap = new byte[(int) UploadValidator.PHOTO_MAX];
    System.arraycopy(PNG, 0, atCap, 0, PNG.length);
    assertDoesNotThrow(() -> validator.validateImage(atCap, UploadValidator.PHOTO_MAX, "image/png"));
    byte[] overCap = new byte[(int) UploadValidator.PHOTO_MAX + 1];
    System.arraycopy(PNG, 0, overCap, 0, PNG.length);
    var e = assertThrows(Exceptions.BadRequest.class,
      () -> validator.validateImage(overCap, UploadValidator.PHOTO_MAX, "image/png"));
    assertEquals("FILE_TOO_LARGE", e.code());
  }

  @Test void attachmentSizeLimitBitesExactlyAtCap() {
    byte[] atCap = new byte[(int) UploadValidator.ATTACHMENT_MAX];
    System.arraycopy(PNG, 0, atCap, 0, PNG.length);
    assertDoesNotThrow(() -> validator.validateAttachment(atCap, UploadValidator.ATTACHMENT_MAX));
    byte[] overCap = new byte[(int) UploadValidator.ATTACHMENT_MAX + 1];
    System.arraycopy(PNG, 0, overCap, 0, PNG.length);
    var e = assertThrows(Exceptions.BadRequest.class,
      () -> validator.validateAttachment(overCap, UploadValidator.ATTACHMENT_MAX));
    assertEquals("FILE_TOO_LARGE", e.code());
  }

  @Test void sanitizeFileNameStripsTraversalQuotesAndControlChars() {
    assertEquals("file", UploadValidator.sanitizeFileName(null));
    assertEquals("file", UploadValidator.sanitizeFileName("   "));
    assertEquals("passwd", UploadValidator.sanitizeFileName("../../etc/passwd"));
    assertEquals("evil.png", UploadValidator.sanitizeFileName("..\\..\\evil.png"));
    assertEquals("passwd", UploadValidator.sanitizeFileName("/etc/passwd"));
    assertEquals("a_b__.png", UploadValidator.sanitizeFileName("a\"b\r\n.png"));
    assertEquals("my photo (1).png", UploadValidator.sanitizeFileName("my photo (1).png"));
    assertEquals("file", UploadValidator.sanitizeFileName(".."));
    assertEquals(200, UploadValidator.sanitizeFileName("a".repeat(300) + ".png").length());
    String safe = UploadValidator.sanitizeFileName("x\"y\r\nz.png");
    assertFalse(safe.contains("\"") || safe.contains("\r") || safe.contains("\n"),
      "sanitised name must be safe inside a quoted Content-Disposition value");
  }
}
