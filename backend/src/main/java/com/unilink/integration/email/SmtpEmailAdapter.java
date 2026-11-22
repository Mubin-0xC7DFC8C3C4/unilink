package com.unilink.integration.email;
import com.unilink.config.AppProperties; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage; import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
/** SMTP adapter, selected only when SMTP is actually configured
 *  (MAIL_PROVIDER=smtp). Requires SMTP_HOST etc.; fails clearly if the
 *  sender is misconfigured — but only once it has been selected. */
@Component @ConditionalOnProperty(prefix="app.mail", name="provider", havingValue="smtp")
public class SmtpEmailAdapter implements EmailProvider {
  private final JavaMailSender sender; private final AppProperties props;
  public SmtpEmailAdapter(JavaMailSender sender, AppProperties props){ this.sender=sender; this.props=props; }
  private void send(String to, String subject, String body){
    try { SimpleMailMessage m = new SimpleMailMessage();
      m.setFrom(props.mail()!=null&&props.mail().from()!=null?props.mail().from():"noreply@unilink.dev");
      m.setTo(to); m.setSubject(subject); m.setText(body); sender.send(m);
    } catch (Exception e){ throw new ProviderException("SMTP send failed: "+e.getMessage()); }
  }
  @Override public void sendVerificationLink(String to, String link){ send(to,"Verify your UniLink email","Click to verify your email: "+link); }
  @Override public void sendOtp(String to, String otp){ send(to,"Your UniLink code","Your UniLink verification code is "+otp+". It expires in 5 minutes."); }
  @Override public void sendSimple(String to, String subject, String body){ send(to,subject,body); }
}
