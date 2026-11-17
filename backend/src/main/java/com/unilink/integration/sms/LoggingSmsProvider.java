package com.unilink.integration.sms;
import org.slf4j.*; import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Component;
/** Dev adapter: logs the OTP, clearly labelled DEV-ONLY. Never used in prod. */
@Component @Profile({"dev","default"})
public class LoggingSmsProvider implements SmsProvider {
  private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);
  @Override public void sendOtp(String phone, String otp){
    log.warn("DEV-ONLY SMS to {}: OTP is {} (no real SMS gateway configured; see D-066)", phone, otp);
  }
}
