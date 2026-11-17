package com.unilink.storage;
import com.unilink.common.Exceptions; import org.springframework.stereotype.Component;
/** Chain-of-Responsibility style content validation (ARCH §8): size, magic-byte sniffing, allow-list.
 *  The declared content type and the file extension are NEVER trusted — the
 *  sniffed type governs what is stored and served. */
@Component
public class UploadValidator {
  public static final long ID_CARD_MAX = 5L*1024*1024, PHOTO_MAX = 2L*1024*1024, COVER_MAX = 5L*1024*1024,
    ATTACHMENT_MAX = 10L*1024*1024;
  /** Per-post attachment count cap (D-104): at most 5 files on one post. */
  public static final int MAX_ATTACHMENTS_PER_POST = 5;

  public String sniffContentType(byte[] bytes, String declared){
    if (bytes==null || bytes.length<4) throw new Exceptions.BadRequest("INVALID_FILE","File is empty or unreadable");
    // WebP is a RIFF container (RIFF....WEBP). A bare RIFF prefix also matches
    // WAV/AVI, so require the WEBP marker at offset 8 as well.
    if (bytes.length>=12 && bytes[0]=='R' && bytes[1]=='I' && bytes[2]=='F' && bytes[3]=='F'){
      if (bytes[8]=='W' && bytes[9]=='E' && bytes[10]=='B' && bytes[11]=='P') return "image/webp";
      throw new Exceptions.BadRequest("INVALID_FILE_TYPE","File type not allowed");
    }
    if (isJpeg(bytes)) return "image/jpeg";
    if (isPng(bytes)) return "image/png";
    if (bytes[0]=='%' && bytes[1]=='P' && bytes[2]=='D' && bytes[3]=='F') return "application/pdf";
    throw new Exceptions.BadRequest("INVALID_FILE_TYPE","File type not allowed");
  }
  /** JPEG: SOI + a real marker code as the 4th byte. A file starting FF D8 FF 00
   *  (or any other non-marker) is not a JPEG, even though the old 3-byte check passed it. */
  private static boolean isJpeg(byte[] b){
    if (b.length<4 || b[0]!=(byte)0xFF || b[1]!=(byte)0xD8 || b[2]!=(byte)0xFF) return false;
    int m = b[3] & 0xFF;
    return (m>=0xC0 && m<=0xCF) || (m>=0xD0 && m<=0xD7)
      || (m>=0xDA && m<=0xDF) || (m>=0xE0 && m<=0xEF) || m==0xFE;
  }
  /** PNG: the full 8-byte signature, not just the 4-byte prefix (a file that
   *  starts "PNG" but lacks the rest of the signature is not a PNG). */
  private static boolean isPng(byte[] b){
    return b.length>=8 && b[0]==(byte)0x89 && b[1]=='P' && b[2]=='N' && b[3]=='G'
      && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10;
  }
  /** Validate an image upload; returns the sniffed content type for storage. */
  public String validateImage(byte[] bytes, long maxBytes, String declared){
    if (bytes.length>maxBytes) throw new Exceptions.BadRequest("FILE_TOO_LARGE","File exceeds the size limit");
    String type = sniffContentType(bytes, declared);
    if (!type.startsWith("image/")) throw new Exceptions.BadRequest("INVALID_FILE_TYPE","Only image files are allowed here");
    return type;
  }
  /** Validate a post/chat attachment (image or PDF); returns the sniffed content type. */
  public String validateAttachment(byte[] bytes, long maxBytes){
    if (bytes.length>maxBytes) throw new Exceptions.BadRequest("FILE_TOO_LARGE","File exceeds the size limit");
    return sniffContentType(bytes, null);
  }
  /** Make a client-supplied file name safe to persist and to send back in a
   *  Content-Disposition header: strip directories (no traversal, no absolute
   *  path), drop quotes, backslashes and control characters (no header
   *  injection), keep a readable name, cap the length. Null/blank becomes "file". */
  public static String sanitizeFileName(String fileName){
    if (fileName==null || fileName.isBlank()) return "file";
    String base = fileName.replace('\\','/');
    int slash = base.lastIndexOf('/');
    if (slash>=0) base = base.substring(slash+1);
    StringBuilder sb = new StringBuilder(base.length());
    for (int i = 0; i < base.length(); i++){
      char c = base.charAt(i);
      if ((c>='A' && c<='Z') || (c>='a' && c<='z') || (c>='0' && c<='9')
        || c=='.' || c=='-' || c=='_' || c=='(' || c==')' || c==' ') sb.append(c);
      else sb.append('_');
    }
    String clean = sb.toString().strip();
    while (clean.startsWith(".")) clean = clean.substring(1);
    if (clean.isEmpty() || ".".equals(clean) || "..".equals(clean)) return "file";
    if (clean.length()>200) clean = clean.substring(0, 200);
    return clean;
  }
}
