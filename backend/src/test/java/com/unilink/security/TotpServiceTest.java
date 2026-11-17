package com.unilink.security;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
/** TOTP: generated secrets verify; wrong codes do not. */
class TotpServiceTest {
  private final TotpService totp = new TotpService();
  @Test void generateAndVerify() throws Exception {
    String secret = totp.generateSecret();
    assertNotNull(secret); assertTrue(secret.length() >= 16);
    Method gen = TotpService.class.getDeclaredMethod("generate", String.class, long.class);
    gen.setAccessible(true);
    long step = java.time.Instant.now().getEpochSecond()/30;
    String code = (String) gen.invoke(totp, secret, step);
    assertTrue(totp.verify(secret, code));
    assertFalse(totp.verify(secret, "000000".equals(code) ? "000001" : "000000"));
  }
  @Test void invalidCodesRejected(){
    assertFalse(totp.verify(totp.generateSecret(), "123"));
    assertFalse(totp.verify(totp.generateSecret(), "abcdef"));
    assertFalse(totp.verify(totp.generateSecret(), null));
  }
}
