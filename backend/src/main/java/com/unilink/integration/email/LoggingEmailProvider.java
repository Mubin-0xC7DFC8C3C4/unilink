package com.unilink.integration.email;
import org.slf4j.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Component;
/** Log adapter: records that an email WOULD have been sent, for local
 *  development without an SMTP server. Default (app.mail.provider=log,
 *  matchIfMissing) in EVERY profile — including prod — until real SMTP is
 *  configured with MAIL_PROVIDER=smtp.
 *
 *  Security rule (2026-11-24): this adapter NEVER logs a secret. Earlier
 *  it printed the full verification link and the plaintext OTP, which is
 *  how codes ended up in the production Render logs while
 *  MAIL_PROVIDER=log was set. Only the recipient and the message kind are
 *  logged; the link, the code and the body are never written anywhere. */
@Component @ConditionalOnProperty(prefix="app.mail", name="provider", havingValue="log", matchIfMissing=true)
public class LoggingEmailProvider implements EmailProvider {
  private static final Logger log = LoggerFactory.getLogger(LoggingEmailProvider.class);
  @Override public void sendVerificationLink(String to, String link){ log.warn("Log email to {}: verification link (not sent — log provider; link omitted)", to); }
  @Override public void sendOtp(String to, String otp){ log.warn("Log email to {}: verification code (not sent — log provider; code omitted)", to); }
  @Override public void sendSimple(String to, String subject, String body){ log.warn("Log email to {}: [{}] (not sent — log provider; body omitted)", to, subject); }
}
