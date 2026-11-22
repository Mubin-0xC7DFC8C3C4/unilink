package com.unilink.integration.sms;
import org.slf4j.*; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Component;
/** Log adapter: logs the OTP instead of sending it. This is the default
 *  (app.sms.provider=log, matchIfMissing) in EVERY profile — including
 *  prod — until a real gateway is configured with
 *  SMS_PROVIDER=gateway + SMS_API_URL/SMS_API_KEY. Profile-fencing it to
 *  dev made prod registration fail: the gateway adapter throws when
 *  unconfigured and the email fallback threw too, aborting sign-up. */
@Component @ConditionalOnProperty(prefix="app.sms", name="provider", havingValue="log", matchIfMissing=true)
public class LoggingSmsProvider implements SmsProvider {
  private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);
  @Override public void sendOtp(String phone, String otp){
    log.warn("SMS (log provider) to {}: OTP is {} (no real SMS gateway configured; see D-066)", phone, otp);
  }
}
