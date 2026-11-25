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
  /** D-197: image-quality evidence for one side, measured by the
   *  adapter from the decoded image (pixel standard deviation / the
   *  share of pixels near the modal grey value). {@link #unknown()} is
   *  the "no signal" value for fakes and for sides with no image: the
   *  plausibility check treats it as neutral, never as blank. A
   *  negative stdDev means "no measurement was made". */
  record QualitySignals(boolean decodable, double pixelStdDev, double nearUniformShare) {
    public static QualitySignals unknown(){ return new QualitySignals(true, -1.0, -1.0); }
    /** True when the adapter measured something: a decodable image with
     *  a pixel measurement, or an undecodable one (decodable=false is
     *  itself evidence). Only {@link #unknown()} has no signal. */
    public boolean hasSignal(){ return !decodable || pixelStdDev >= 0; }
  }
  /** One side of a two-sided ID card: OCR text, mean word confidence,
   *  the engine status (DONE / FAILED), and the image-quality signals
   *  (D-197). The 3-arg form keeps existing fakes working with an
   *  unknown (neutral) quality signal. */
  record SideResult(String text, Double confidence, String status, QualitySignals quality) {
    public SideResult(String text, Double confidence, String status){ this(text, confidence, status, QualitySignals.unknown()); }
  }
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
    // D-197: the fake has no decoded image to measure, so both sides
    // carry an unknown (neutral) quality signal — the plausibility
    // check never treats a fake side as blank.
    SideResult back = backBytes == null
        ? new SideResult("", null, "FAILED", QualitySignals.unknown())
        : new SideResult(universityName == null ? "" : universityName, 95.0, "DONE", QualitySignals.unknown());
    return new TwoSidedResult(new SideResult(front.text(), front.confidence(), front.status(), QualitySignals.unknown()), back, null);
  }
  class ProviderException extends RuntimeException { public ProviderException(String m, Throwable t){ super(m,t); } }
}
