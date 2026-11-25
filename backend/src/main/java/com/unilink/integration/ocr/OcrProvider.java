package com.unilink.integration.ocr;
public interface OcrProvider {
  /** confidence is the mean per-word confidence (0–100) from Tesseract's
   *  TSV output, or null when the engine did not report one. */
  record Result(String text, double matchScore, String status, Double confidence) {
    Result(String text, double matchScore, String status){ this(text, matchScore, status, null); }
  }
  Result extract(byte[] imageBytes, String fileName, String fullName, String studentId);
  /** I-12 (FR-AUTH-06): the scorer must also compare the university name.
   *  Default delegates to the 4-arg extract so existing fakes keep working;
   *  the Tesseract adapter overrides it. */
  default Result extract(byte[] imageBytes, String fileName, String fullName, String studentId, String universityName){
    return extract(imageBytes, fileName, fullName, studentId);
  }
  /** One side of a two-sided ID card: OCR text, mean word confidence,
   *  and the engine status (DONE / FAILED). */
  record SideResult(String text, Double confidence, String status) {}
  /** Both sides of an ID card plus the validity string read from the
   *  back's vertical strip (e.g. "July'23 - June'27"), or null when the
   *  strip could not be read. The validity is best-effort evidence only;
   *  an unreadable validity is neutral, never a failure. */
  record TwoSidedResult(SideResult front, SideResult back, String validity) {}
  /** D-191: OCR both sides of an ID card. The default runs the single-side
   *  extract on the front and reports the university name as the back
   *  text, so existing fakes (which cannot OCR a real back) keep working
   *  and still exercise the two-sided decision path; the Tesseract
   *  adapter overrides this with real back OCR plus the rotated validity
   *  pass. A null back image yields an empty FAILED back side, which the
   *  matcher treats as unreadable (MANUAL_REVIEW), never as a match. */
  default TwoSidedResult extractTwoSided(byte[] frontBytes, byte[] backBytes, String frontName, String backName,
                                         String fullName, String studentId, String universityName){
    Result front = extract(frontBytes, frontName, fullName, studentId, universityName);
    SideResult back = backBytes == null
        ? new SideResult("", null, "FAILED")
        : new SideResult(universityName == null ? "" : universityName, 95.0, "DONE");
    return new TwoSidedResult(new SideResult(front.text(), front.confidence(), front.status()), back, null);
  }
  class ProviderException extends RuntimeException { public ProviderException(String m, Throwable t){ super(m,t); } }
}
