package com.unilink.integration.email;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unilink.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
/** Brevo HTTPS API adapter, selected only when MAIL_PROVIDER=brevo.
 *  D-189 (2026-11-25): Render Free blocks outbound TCP to SMTP ports
 *  25/465/587, so Gmail SMTP can never deliver from this deployment —
 *  every send failed with a TCP connect timeout (MailConnectException,
 *  "Connection timed out" on smtp.gmail.com:587) before any SMTP/TLS/
 *  authentication step. The Brevo API runs over HTTPS (port 443), which
 *  Render does not block. Free tier: 300 emails/day, no card; the only
 *  sender requirement is ONE verified sender email address (Brevo >
 *  Settings > Senders & IPs > Senders) — no custom domain or DNS is
 *  needed, and a verified sender can mail ANY recipient (unlike Resend,
 *  whose unverified accounts may only mail the account owner's address).
 *
 *  Sends are plain JSON over HTTPS: POST https://api.brevo.com/v3/smtp/email
 *  with an `api-key` header. A 2xx response carries a messageId; anything
 *  else is a ProviderException naming the HTTP status and Brevo's error
 *  message — never the request body, so the OTP is never written to a
 *  log or an exception message. Timeouts are 10 s (connect + request),
 *  so an unreachable Brevo fails fast instead of hanging the request.
 *
 *  Fail-fast validation: the bean refuses to start unless BREVO_API_KEY
 *  and a real MAIL_FROM are set. A half-configured Brevo used to fail
 *  silently the way SMTP did (every send threw, OtpService swallowed
 *  it, registration completed with no email ever delivered).
 *
 *  Ordering warning: deploy this code FIRST, then set MAIL_PROVIDER=brevo
 *  in Render. The previous release has no `brevo` provider, so setting
 *  the variable before the code is live leaves no EmailProvider bean and
 *  the app will not start. */
@Component @ConditionalOnProperty(prefix="app.mail", name="provider", havingValue="brevo")
public class BrevoEmailAdapter implements EmailProvider {
  static final String API_URL = "https://api.brevo.com/v3/smtp/email";
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BrevoEmailAdapter.class);
  private final AppProperties props;
  private final HttpClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  // Multiple constructors: Spring must be told which one to autowire.
  @Autowired
  public BrevoEmailAdapter(AppProperties props){
    this(props, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
  }
  // Package-private for tests: inject a mocked HttpClient so no real
  // socket is opened (the sandbox intercepts Java TCP connections).
  BrevoEmailAdapter(AppProperties props, HttpClient client){ this.props = props; this.client = client; }

  @PostConstruct void validate(){
    String key = props.mail()!=null ? props.mail().brevoApiKey() : null;
    if (key==null || key.isBlank())
      throw new IllegalStateException("MAIL_PROVIDER=brevo is selected but BREVO_API_KEY is not set. "
        + "Create an API key in Brevo (Settings > SMTP & API > API Keys) and set it as the BREVO_API_KEY environment variable on Render.");
    String from = props.mail()!=null ? props.mail().from() : null;
    if (from==null || from.isBlank())
      throw new IllegalStateException("MAIL_PROVIDER=brevo is selected but MAIL_FROM is not set. "
        + "Set MAIL_FROM to the sender address verified in Brevo (Settings > Senders & IPs > Senders).");
    if ("noreply@unilink.dev".equals(from))
      throw new IllegalStateException("MAIL_PROVIDER=brevo is selected but MAIL_FROM is still the placeholder noreply@unilink.dev. "
        + "Brevo only sends from a verified sender address — set MAIL_FROM to the address verified in Brevo (Settings > Senders & IPs > Senders).");
  }

  private void send(String to, String subject, String body){
    String from = props.mail()!=null ? props.mail().from() : "noreply@unilink.dev";
    String key = props.mail()!=null ? props.mail().brevoApiKey() : "";
    String json;
    try {
      json = mapper.writeValueAsString(Map.of(
        "sender", Map.of("name", "UniLink", "email", from),
        "to", List.of(Map.of("email", to)),
        "subject", subject,
        "textContent", body));
    } catch (Exception e){ throw new ProviderException("Brevo send failed: could not build request: " + e.getMessage()); }
    HttpRequest req = HttpRequest.newBuilder(URI.create(API_URL))
      .timeout(Duration.ofSeconds(10))
      .header("accept", "application/json")
      .header("api-key", key)
      .header("content-type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .build();
    try {
      HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() < 200 || res.statusCode() >= 300)
        throw new ProviderException("Brevo send failed: HTTP " + res.statusCode() + " — " + brevoError(res.body()));
      String messageId = "";
      try { JsonNode n = mapper.readTree(res.body()); messageId = n.path("messageId").asText(""); } catch (Exception ignored) { /* messageId is informational only */ }
      log.info("Brevo accepted email to {} (messageId {})", to, messageId.isBlank() ? "n/a" : messageId);
    } catch (ProviderException e){ throw e; }
    catch (InterruptedException e){ Thread.currentThread().interrupt(); throw new ProviderException("Brevo send failed: interrupted"); }
    catch (Exception e){ throw new ProviderException("Brevo send failed: " + e.getMessage()); }
  }
  /** Surfaces Brevo's error message/code only — never the request body,
   *  which contains the OTP. Brevo error bodies look like
   *  {"message":"...","code":"..."}. */
  private String brevoError(String body){
    if (body==null || body.isBlank()) return "empty error body";
    try {
      JsonNode n = mapper.readTree(body);
      String msg = n.path("message").asText("").trim();
      String code = n.path("code").asText("").trim();
      if (!msg.isBlank()) return code.isBlank() ? msg : code + ": " + msg;
    } catch (Exception ignored) { /* fall through to the raw body */ }
    return body.length() > 200 ? body.substring(0, 200) + "…" : body;
  }

  @Override public void sendVerificationLink(String to, String link){ send(to,"Verify your UniLink email","Hello,\n\nPlease verify your UniLink email address by clicking the link below. The link expires in 24 hours.\n\n"+link+"\n\nIf you did not create a UniLink account, you can ignore this email."); }
  @Override public void sendOtp(String to, String otp){ sendOtp(to, otp, 5); }
  @Override public void sendOtp(String to, String otp, int expiryMinutes){ send(to,"Your UniLink code","Your UniLink verification code is "+otp+". It expires in "+expiryMinutes+" minutes. Never share it with anyone."); }
  @Override public void sendSimple(String to, String subject, String body){ send(to,subject,body); }
}
