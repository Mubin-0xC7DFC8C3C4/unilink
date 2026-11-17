package com.unilink.common;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static org.junit.jupiter.api.Assertions.*;

/** An upload over the configured 10 MB multipart limit must map to 413 with
 *  code FILE_TOO_LARGE, not a generic 500. Direct handler test: the
 *  multipart resolver raises MaxUploadSizeExceededException before any
 *  controller runs, so the handler mapping is the behaviour under test. */
class GlobalExceptionHandlerTest {
  @Test void oversizeUploadMapsTo413() {
    var handler = new GlobalExceptionHandler();
    var req = new MockHttpServletRequest("POST", "/api/profiles/me/cover");
    var resp = handler.tooLarge(new MaxUploadSizeExceededException(10L*1024*1024), req);
    assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, resp.getStatusCode());
    assertEquals(413, resp.getBody().getStatus());
    assertEquals("FILE_TOO_LARGE", resp.getBody().getCode());
    assertEquals("/api/profiles/me/cover", resp.getBody().getPath());
  }
}
