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
  class ProviderException extends RuntimeException { public ProviderException(String m, Throwable t){ super(m,t); } }
}
