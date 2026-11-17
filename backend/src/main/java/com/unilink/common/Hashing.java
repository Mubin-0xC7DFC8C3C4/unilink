package com.unilink.common;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest;
public final class Hashing {
  private Hashing(){}
  public static String sha256(String input){
    try { byte[] d = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(); for (byte b : d) sb.append(String.format("%02x", b)); return sb.toString(); }
    catch (Exception e){ throw new IllegalStateException(e); }
  }
}
