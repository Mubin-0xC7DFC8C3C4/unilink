package com.unilink.integration.email;
import com.unilink.config.AppProperties; import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage; import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
/** SMTP adapter, selected only when SMTP is actually configured
 *  (MAIL_PROVIDER=smtp). Sends through Spring Boot's JavaMailSender, whose
 *  host/port/credentials/STARTTLS/timeouts come from the spring.mail.*
 *  properties (SMTP_HOST/SMTP_PORT/SMTP_USERNAME/SMTP_PASSWORD/
 *  SMTP_STARTTLS in application.yml — 10 s connect/read/write timeouts,
 *  so an unreachable route fails fast instead of hanging the request).
 *
 *  Fail-fast validation (2026-11-24): a half-configured SMTP used to fail
 *  silently — every send threw, OtpService swallowed it, and registration
 *  completed with no email ever delivered. Now the bean refuses to start
 *  unless the configuration can actually deliver: a non-localhost host
 *  needs a username and password (Gmail requires authentication), and a
 *  sender address must be set. The exception names the missing variables,
 *  never their values. A localhost host without credentials is allowed on
 *  purpose (local mail catchers such as MailHog on :1025). */
@Component @ConditionalOnProperty(prefix="app.mail", name="provider", havingValue="smtp")
public class SmtpEmailAdapter implements EmailProvider {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SmtpEmailAdapter.class);
  private final JavaMailSender sender; private final AppProperties props;
  public SmtpEmailAdapter(JavaMailSender sender, AppProperties props){ this.sender=sender; this.props=props; }
  @PostConstruct void validate(){
    // Only the auto-configured sender can be inspected: a custom or
    // mocked JavaMailSender (e.g. ProviderSelectionTest's mock) carries no
    // host/credentials to validate, so there is nothing to fail on.
    if (!(sender instanceof JavaMailSenderImpl impl)) return;
    String host = impl.getHost();
    String username = impl.getUsername();
    String password = impl.getPassword();
    boolean local = host==null || host.isBlank() || "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    if (host==null || host.isBlank())
      throw new IllegalStateException("SMTP is selected (MAIL_PROVIDER=smtp) but SMTP_HOST is not set.");
    if (!local && (username==null || username.isBlank() || password==null || password.isBlank()))
      throw new IllegalStateException("SMTP is selected (MAIL_PROVIDER=smtp) for host " + host
        + " but SMTP_USERNAME and/or SMTP_PASSWORD are not set. Gmail needs an App Password (Google Account > Security > 2-Step Verification > App passwords).");
    String from = props.mail()!=null ? props.mail().from() : null;
    if (from==null || from.isBlank())
      throw new IllegalStateException("SMTP is selected (MAIL_PROVIDER=smtp) but MAIL_FROM is not set.");
    if ("noreply@unilink.dev".equals(from))
      log.warn("SMTP selected with the placeholder sender MAIL_FROM=noreply@unilink.dev — Gmail rejects mail whose From is not the authenticated account or one of its aliases. Set MAIL_FROM to the Gmail account used for SMTP_USERNAME.");
    if (local)
      log.warn("SMTP selected against localhost — no real delivery will happen. Use a local mail catcher for development, or set SMTP_HOST to a real server for production.");
  }
  private void send(String to, String subject, String body){
    try { SimpleMailMessage m = new SimpleMailMessage();
      m.setFrom(props.mail()!=null&&props.mail().from()!=null?props.mail().from():"noreply@unilink.dev");
      m.setTo(to); m.setSubject(subject); m.setText(body); sender.send(m);
    } catch (Exception e){ throw new ProviderException("SMTP send failed: "+e.getMessage()); }
  }
  @Override public void sendVerificationLink(String to, String link){ send(to,"Verify your UniLink email","Hello,\n\nPlease verify your UniLink email address by clicking the link below. The link expires in 24 hours.\n\n"+link+"\n\nIf you did not create a UniLink account, you can ignore this email."); }
  @Override public void sendOtp(String to, String otp){ send(to,"Your UniLink code","Your UniLink verification code is "+otp+". It expires in 5 minutes. Never share it with anyone."); }
  @Override public void sendSimple(String to, String subject, String body){ send(to,subject,body); }
}
