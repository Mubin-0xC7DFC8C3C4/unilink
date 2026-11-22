package com.unilink.integration.email;
import org.slf4j.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Component;
/** Log adapter: logs email content instead of sending. Default
 *  (app.mail.provider=log, matchIfMissing) in EVERY profile — including
 *  prod — until real SMTP is configured with MAIL_PROVIDER=smtp +
 *  SMTP_HOST. Profile-fencing it to dev made prod registration fail:
 *  the SMTP adapter throws when no server is configured, and that
 *  exception aborted sign-up (and password-reset requests). */
@Component @ConditionalOnProperty(prefix="app.mail", name="provider", havingValue="log", matchIfMissing=true)
public class LoggingEmailProvider implements EmailProvider {
  private static final Logger log = LoggerFactory.getLogger(LoggingEmailProvider.class);
  @Override public void sendVerificationLink(String to, String link){ log.warn("Log email to {}: verification link {}", to, link); }
  @Override public void sendOtp(String to, String otp){ log.warn("Log email to {}: OTP {}", to, otp); }
  @Override public void sendSimple(String to, String subject, String body){ log.warn("Log email to {}: [{}] {}", to, subject, body); }
}
