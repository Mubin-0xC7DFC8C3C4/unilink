package com.unilink.integration.email;
public interface EmailProvider {
  void sendVerificationLink(String to, String link);
  void sendOtp(String to, String otp);
  void sendSimple(String to, String subject, String body);
  class ProviderException extends RuntimeException { public ProviderException(String m){ super(m); } }
}
