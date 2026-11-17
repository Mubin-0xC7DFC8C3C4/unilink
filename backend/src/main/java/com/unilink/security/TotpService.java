package com.unilink.security;
import org.springframework.stereotype.Service;
import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer; import java.security.SecureRandom; import java.time.Instant;
/** RFC 6238 TOTP (SHA-1, 30 s, 6 digits), window ±1 step. Base32 secret, no external dep. */
@Service
public class TotpService {
  private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  public String generateSecret(){
    byte[] b = new byte[20]; new SecureRandom().nextBytes(b);
    StringBuilder sb = new StringBuilder();
    int buffer=0, bits=0;
    for (byte v : b){ buffer=(buffer<<8)|(v&0xff); bits+=8; while(bits>=5){ sb.append(BASE32.charAt((buffer>>(bits-5))&31)); bits-=5; } }
    if (bits>0) sb.append(BASE32.charAt((buffer<<(5-bits))&31));
    return sb.toString();
  }
  public String otpauthUrl(String secret, String account, String issuer){
    return "otpauth://totp/"+issuer+":"+account+"?secret="+secret+"&issuer="+issuer+"&digits=6&period=30";
  }
  public boolean verify(String base32Secret, String code){
    if (code==null || !code.matches("\\d{6}")) return false;
    long step = Instant.now().getEpochSecond()/30;
    for (long s=step-1; s<=step+1; s++) if (generate(base32Secret,s).equals(code)) return true;
    return false;
  }
  private String generate(String base32Secret, long step){
    try {
      byte[] key = decodeBase32(base32Secret);
      Mac mac = Mac.getInstance("HmacSHA1"); mac.init(new SecretKeySpec(key,"HmacSHA1"));
      byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
      int off = h[h.length-1]&0xf;
      int v = ((h[off]&0x7f)<<24)|((h[off+1]&0xff)<<16)|((h[off+2]&0xff)<<8)|(h[off+3]&0xff);
      return String.format("%06d", v % 1_000_000);
    } catch (Exception e){ throw new IllegalStateException(e); }
  }
  private byte[] decodeBase32(String s){
    s=s.replace("=","").toUpperCase(); java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
    int buffer=0,bits=0;
    for(char c: s.toCharArray()){ int v=BASE32.indexOf(c); if(v<0) continue; buffer=(buffer<<5)|v; bits+=5;
      if(bits>=8){ out.write((buffer>>(bits-8))&0xff); bits-=8; } }
    return out.toByteArray();
  }
}
