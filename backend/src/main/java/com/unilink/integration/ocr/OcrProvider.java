package com.unilink.integration.ocr;
public interface OcrProvider {
  record Result(String text, double matchScore, String status) {}
  Result extract(byte[] imageBytes, String fileName, String fullName, String studentId);
  class ProviderException extends RuntimeException { public ProviderException(String m, Throwable t){ super(m,t); } }
}
