package com.unilink.integration.email;
public interface EmailProvider {
  void sendVerificationLink(String to, String link);
  void sendOtp(String to, String otp);
  /** Sends a verification code whose validity window is {@code expiryMinutes}
   *  (5 = phone fallback, 10 = signup email OTP, 15 = password reset), so
   *  the message can state the true expiry instead of a fixed number.
   *  D-189: the SMTP adapter used to tell every recipient "5 minutes"
   *  even for the 10- and 15-minute codes. Defaults to the plain sendOtp
   *  so existing providers and test fakes keep working unchanged. */
  default void sendOtp(String to, String otp, int expiryMinutes){ sendOtp(to, otp); }
  void sendSimple(String to, String subject, String body);
  class ProviderException extends RuntimeException { public ProviderException(String m){ super(m); } }
}
