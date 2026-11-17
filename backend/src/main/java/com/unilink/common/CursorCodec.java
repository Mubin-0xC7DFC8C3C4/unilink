package com.unilink.common;
import java.nio.charset.StandardCharsets; import java.util.Base64;
/** Keyset cursor: base64url("createdAtEpochMilli:id"). */
public final class CursorCodec {
  private CursorCodec(){}
  public static String encode(long epochMilli, long id){
    return Base64.getUrlEncoder().withoutPadding().encodeToString((epochMilli+":"+id).getBytes(StandardCharsets.UTF_8));
  }
  public static long[] decode(String cursor){
    try { String[] p = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":");
      return new long[]{ Long.parseLong(p[0]), Long.parseLong(p[1]) }; }
    catch (Exception e){ throw new Exceptions.BadRequest("INVALID_CURSOR","Invalid pagination cursor"); }
  }
}
