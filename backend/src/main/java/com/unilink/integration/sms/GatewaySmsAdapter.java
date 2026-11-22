package com.unilink.integration.sms;
import com.unilink.config.AppProperties; import org.slf4j.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.net.*; import java.net.http.*; import java.nio.charset.StandardCharsets; import java.time.Duration;
/** Real SMS-gateway adapter, selected only when a gateway is actually
 *  configured (SMS_PROVIDER=gateway). Fails clearly without credentials
 *  (Master Guide §1 rule 4) — but only once it has been selected. */
@Component @ConditionalOnProperty(prefix="app.sms", name="provider", havingValue="gateway")
public class GatewaySmsAdapter implements SmsProvider {
  private static final Logger log = LoggerFactory.getLogger(GatewaySmsAdapter.class);
  private final AppProperties props;
  public GatewaySmsAdapter(AppProperties props){ this.props=props; }
  @Override public void sendOtp(String phone, String otp){
    String url = props.sms()!=null?props.sms().apiUrl():null;
    String key = props.sms()!=null?props.sms().apiKey():null;
    if (url==null || url.isBlank() || key==null || key.isBlank())
      throw new ProviderException("SMS gateway not configured (SMS_API_URL / SMS_API_KEY missing)");
    try { HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
      String body = "to="+URLEncoder.encode(phone,StandardCharsets.UTF_8)+"&message="+URLEncoder.encode("Your UniLink OTP is "+otp,StandardCharsets.UTF_8);
      HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
        .header("Authorization","Bearer "+key).header("Content-Type","application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
      HttpResponse<String> resp = c.send(r, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode()>=300){ log.error("SMS gateway returned {}", resp.statusCode()); throw new ProviderException("SMS gateway error "+resp.statusCode()); }
    } catch (ProviderException e){ throw e; } catch (Exception e){ throw new ProviderException("SMS gateway call failed", e); }
  }
}
