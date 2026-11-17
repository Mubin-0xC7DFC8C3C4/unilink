package com.unilink.storage;
import com.unilink.common.Exceptions; import org.springframework.stereotype.Component;
import java.util.*;
/** Chain-of-Responsibility style content validation (ARCH §8): size, magic-byte sniffing, allow-list. */
@Component
public class UploadValidator {
  public static final long ID_CARD_MAX = 5L*1024*1024, PHOTO_MAX = 2L*1024*1024, COVER_MAX = 5L*1024*1024,
    ATTACHMENT_MAX = 10L*1024*1024;
  private static final Map<String,byte[]> MAGIC = Map.of(
    "image/jpeg", new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF},
    "image/png", new byte[]{(byte)0x89,'P','N','G'},
    "application/pdf", new byte[]{'%','P','D','F'});
  public String sniffContentType(byte[] bytes, String declared){
    if (bytes==null || bytes.length<4) throw new Exceptions.BadRequest("INVALID_FILE","File is empty or unreadable");
    // WebP is a RIFF container (RIFF....WEBP). A bare RIFF prefix also matches
    // WAV/AVI, so require the WEBP marker at offset 8 as well.
    if (bytes.length>=12 && bytes[0]=='R' && bytes[1]=='I' && bytes[2]=='F' && bytes[3]=='F'){
      if (bytes[8]=='W' && bytes[9]=='E' && bytes[10]=='B' && bytes[11]=='P') return "image/webp";
      throw new Exceptions.BadRequest("INVALID_FILE_TYPE","File type not allowed");
    }
    for (var e : MAGIC.entrySet()){ byte[] m=e.getValue();
      boolean ok=true; for(int i=0;i<m.length;i++) if(bytes[i]!=m[i]){ok=false;break;}
      if (ok) return e.getKey(); }
    throw new Exceptions.BadRequest("INVALID_FILE_TYPE","File type not allowed");
  }
  public void validateImage(byte[] bytes, long maxBytes, String declared){
    if (bytes.length>maxBytes) throw new Exceptions.BadRequest("FILE_TOO_LARGE","File exceeds the size limit");
    String type = sniffContentType(bytes, declared);
    if (!type.startsWith("image/")) throw new Exceptions.BadRequest("INVALID_FILE_TYPE","Only image files are allowed here");
  }
  public void validateAttachment(byte[] bytes, long maxBytes){
    if (bytes.length>maxBytes) throw new Exceptions.BadRequest("FILE_TOO_LARGE","File exceeds the 10 MB limit");
    sniffContentType(bytes, null);
  }
}
