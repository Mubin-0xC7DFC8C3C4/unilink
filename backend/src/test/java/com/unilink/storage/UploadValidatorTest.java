package com.unilink.storage;
import com.unilink.common.Exceptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** WebP sniffing: a bare RIFF prefix also matches WAV/AVI, so the WEBP
 *  marker at offset 8 is required as well. */
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
}
