package com.unilink.integration.email;
import org.slf4j.*; import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Component;
/** Dev adapter: logs email content instead of sending (no SMTP credentials in dev). */
@Component @Profile({"dev","default"})
public class LoggingEmailProvider implements EmailProvider {
  private static final Logger log = LoggerFactory.getLogger(LoggingEmailProvider.class);
  @Override public void sendVerificationLink(String to, String link){ log.warn("DEV-ONLY email to {}: verification link {}", to, link); }
  @Override public void sendOtp(String to, String otp){ log.warn("DEV-ONLY email to {}: OTP {}", to, otp); }
  @Override public void sendSimple(String to, String subject, String body){ log.warn("DEV-ONLY email to {}: [{}] {}", to, subject, body); }
}
