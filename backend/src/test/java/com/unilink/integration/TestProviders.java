package com.unilink.integration;
import com.unilink.integration.email.EmailProvider; import com.unilink.integration.ocr.OcrProvider;
import com.unilink.integration.sms.SmsProvider;
import org.springframework.boot.test.context.TestConfiguration; import org.springframework.context.annotation.*;
import java.util.concurrent.*;
import java.util.*;
/** Test fakes only (TEST_STRATEGY §3): record messages in memory so tests can read the OTP / link. */
@TestConfiguration
public class TestProviders {
  public static final Map<String,String> SMS_OTPS = new ConcurrentHashMap<>();
  public static final Map<String,String> EMAIL_OTPS = new ConcurrentHashMap<>();
  public static final Map<String,String> EMAIL_LINKS = new ConcurrentHashMap<>();
  public static final List<String> SIMPLE_EMAILS = new CopyOnWriteArrayList<>();
  @Bean @Primary public SmsProvider fakeSmsProvider(){
    return (phone, otp) -> SMS_OTPS.put(phone, otp);
  }
  @Bean @Primary public EmailProvider fakeEmailProvider(){
    return new EmailProvider(){
      @Override public void sendVerificationLink(String to, String link){ EMAIL_LINKS.put(to, link); }
      @Override public void sendOtp(String to, String otp){ EMAIL_OTPS.put(to, otp); }
      @Override public void sendSimple(String to, String subject, String body){ SIMPLE_EMAILS.add(to+":"+subject); }
    };
  }
  @Bean @Primary public OcrProvider fakeOcrProvider(){
    return (bytes, fileName, fullName, studentId) ->
      new OcrProvider.Result(fullName+" "+studentId+" ID CARD", 0.95, "DONE");
  }
  public static void reset(){ SMS_OTPS.clear(); EMAIL_OTPS.clear(); EMAIL_LINKS.clear(); SIMPLE_EMAILS.clear(); }
}
