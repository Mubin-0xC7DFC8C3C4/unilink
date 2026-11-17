package com.unilink.integration.ocr;
public interface OcrProvider {
  record Result(String text, double matchScore, String status) {}
  Result extract(byte[] imageBytes, String fileName, String fullName, String studentId);
  /** I-12 (FR-AUTH-06): the scorer must also compare the university name.
   *  Default delegates to the 4-arg extract so existing fakes keep working;
   *  the Tesseract adapter overrides it. */
  default Result extract(byte[] imageBytes, String fileName, String fullName, String studentId, String universityName){
    return extract(imageBytes, fileName, fullName, studentId);
  }
  class ProviderException extends RuntimeException { public ProviderException(String m, Throwable t){ super(m,t); } }
}
