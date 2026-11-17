package com.unilink.security;
import javax.crypto.*; import javax.crypto.spec.*; import java.nio.charset.StandardCharsets;
import java.security.*; import java.util.Base64;
/** AES-256-GCM for TOTP secrets at rest (D-068). Key = SHA-256(ENCRYPTION_KEY). */
public final class AesGcm {
  private AesGcm(){}
  public static String encrypt(String keyMaterial, String plaintext){
    try { byte[] key = MessageDigest.getInstance("SHA-256").digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
      byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128, iv));
      byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length+ct.length]; System.arraycopy(iv,0,out,0,12); System.arraycopy(ct,0,out,12,ct.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (Exception e){ throw new IllegalStateException(e); }
  }
  public static String decrypt(String keyMaterial, String encoded){
    try { byte[] key = MessageDigest.getInstance("SHA-256").digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
      byte[] all = Base64.getDecoder().decode(encoded);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128, all, 0, 12));
      return new String(c.doFinal(all, 12, all.length-12), StandardCharsets.UTF_8);
    } catch (Exception e){ throw new IllegalStateException("Cannot decrypt stored secret", e); }
  }
}
