package com.unilink.storage;
import java.io.ByteArrayOutputStream;
/** Dependency-free EXIF stripping (D-029a: "Images re-encoded to strip EXIF"; D-104).
 *  Parses the container and drops only the metadata segment/chunk that carries
 *  EXIF; pixel data is copied byte-for-byte, so OCR and rendering are unaffected.
 *  JPEG: drop APP1 (0xFFE1) segments whose payload starts with "Exif\0\0".
 *  PNG: drop "eXIf" chunks. WebP: drop "EXIF" chunks, repair the RIFF size, and
 *  clear the EXIF flag in the VP8X chunk. PDFs and other non-images are returned
 *  unchanged (PDFs carry no EXIF segment; XMP/ICC data is out of scope, see D-104).
 *  A structurally malformed file is returned unchanged (fail-open): it already
 *  passed magic-byte sniffing, no decoder can read EXIF from an unparseable
 *  container, and rejecting it here would change previously-accepted uploads. */
public final class ExifStripper {
  private ExifStripper(){}
  private static final byte[] EXIF_HEADER = {'E','x','i','f',0,0};

  /** Strip EXIF from a JPEG/PNG/WebP image; any other bytes are returned unchanged. */
  public static byte[] strip(byte[] bytes){
    if (bytes==null || bytes.length<12) return bytes;
    if (isJpeg(bytes)) return stripJpeg(bytes);
    if (isPng(bytes)) return stripPng(bytes);
    if (isWebp(bytes)) return stripWebp(bytes);
    return bytes;
  }
  static boolean isJpeg(byte[] b){ return b.length>=3 && b[0]==(byte)0xFF && b[1]==(byte)0xD8 && b[2]==(byte)0xFF; }
  static boolean isPng(byte[] b){
    return b.length>=8 && b[0]==(byte)0x89 && b[1]=='P' && b[2]=='N' && b[3]=='G'
      && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10;
  }
  static boolean isWebp(byte[] b){
    return b.length>=12 && b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F'
      && b[8]=='W' && b[9]=='E' && b[10]=='B' && b[11]=='P';
  }

  // ---- JPEG: SOI, then length-prefixed segments until SOS; scan data is copied verbatim ----
  private static byte[] stripJpeg(byte[] in){
    ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
    out.write(in, 0, 2); // SOI
    int pos = 2;
    boolean dropped = false;
    while (pos + 1 < in.length){
      if (in[pos]!=(byte)0xFF) return in; // malformed: expected a marker
      int p = pos;
      while (p < in.length && in[p]==(byte)0xFF) p++; // fill bytes before the marker code
      if (p >= in.length) return in;
      int marker = in[p] & 0xFF;
      if (marker==0x01 || (marker>=0xD0 && marker<=0xD9)){
        // Standalone marker (TEM, RSTn, SOI, EOI): no length field. Bytes from
        // `pos` to `p` inclusive are the fill 0xFFs plus the paired FF and the
        // marker code itself: p - pos + 1 bytes.
        out.write(in, pos, p - pos + 1);
        pos = p + 1;
        if (marker==0xD9){ out.write(in, pos, in.length - pos); return out.toByteArray(); } // EOI: rest is trailing data
        continue;
      }
      if (p + 2 >= in.length) return in; // truncated length field
      int len = ((in[p+1] & 0xFF) << 8) | (in[p+2] & 0xFF); // length includes its own 2 bytes
      if (len < 2) return in;
      // Segment bytes from `pos`: fill 0xFFs, then FF + marker + length + payload.
      long segEnd = (long) p + 1 + len;
      if (segEnd > in.length) return in; // malformed: segment overruns the file
      if (marker==0xE1 && len >= 2 + EXIF_HEADER.length && startsWith(in, p + 3, EXIF_HEADER)){
        dropped = true; // drop this APP1 Exif segment
      } else {
        out.write(in, pos, (int)(segEnd - pos));
      }
      pos = (int) segEnd;
      if (marker==0xDA){ out.write(in, pos, in.length - pos); break; } // SOS: scan data follows, copy verbatim
    }
    if (pos < in.length) out.write(in, pos, in.length - pos); // truncated tail after the last full segment
    return dropped ? out.toByteArray() : in;
  }

  // ---- PNG: 8-byte signature, then length/type/data/CRC chunks; stop after IEND ----
  private static byte[] stripPng(byte[] in){
    ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
    out.write(in, 0, 8); // signature
    int pos = 8;
    boolean dropped = false;
    while (pos + 8 <= in.length){
      long len = ((long)(in[pos] & 0xFF) << 24) | ((long)(in[pos+1] & 0xFF) << 16)
        | ((long)(in[pos+2] & 0xFF) << 8) | (in[pos+3] & 0xFF);
      String type = new String(in, pos + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
      long chunkEnd = (long) pos + 12 + len;
      if (chunkEnd > in.length) return in; // malformed: chunk overruns the file
      if ("eXIf".equals(type)){
        dropped = true;
      } else {
        out.write(in, pos, (int)(chunkEnd - pos));
      }
      pos = (int) chunkEnd;
      if ("IEND".equals(type)){ out.write(in, pos, in.length - pos); return dropped ? out.toByteArray() : in; }
    }
    if (pos < in.length) out.write(in, pos, in.length - pos); // trailing bytes after the last full chunk
    return dropped ? out.toByteArray() : in;
  }

  // ---- WebP: RIFF size + "WEBP", then type/size/data chunks (even-padded) ----
  private static byte[] stripWebp(byte[] in){
    ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
    out.write(in, 0, 12); // RIFF + size placeholder + WEBP
    int pos = 12;
    boolean dropped = false;
    int vp8xFlagsPos = -1; // offset in `out` of the VP8X flags byte (chunk start + 8)
    while (pos + 8 <= in.length){
      String type = new String(in, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
      long size = ((long)(in[pos+4] & 0xFF)) | ((long)(in[pos+5] & 0xFF) << 8)
        | ((long)(in[pos+6] & 0xFF) << 16) | ((long)(in[pos+7] & 0xFF) << 24);
      long chunkEnd = (long) pos + 8 + size + (size % 2 == 1 ? 1 : 0);
      if (chunkEnd > in.length) return in; // malformed: chunk overruns the file
      if ("EXIF".equals(type)){
        dropped = true;
      } else {
        if ("VP8X".equals(type) && size >= 1) vp8xFlagsPos = out.size() + 8;
        out.write(in, pos, (int)(chunkEnd - pos));
      }
      pos = (int) chunkEnd;
    }
    if (pos < in.length) out.write(in, pos, in.length - pos);
    if (!dropped) return in;
    byte[] result = out.toByteArray();
    int riffSize = result.length - 8; // RIFF size covers everything after the size field
    result[4] = (byte)(riffSize & 0xFF);
    result[5] = (byte)((riffSize >> 8) & 0xFF);
    result[6] = (byte)((riffSize >> 16) & 0xFF);
    result[7] = (byte)((riffSize >> 24) & 0xFF);
    if (vp8xFlagsPos >= 0) result[vp8xFlagsPos] &= ~0x08; // clear the VP8X "EXIF present" flag
    return result;
  }

  private static boolean startsWith(byte[] in, int off, byte[] prefix){
    if (off + prefix.length > in.length) return false;
    for (int i = 0; i < prefix.length; i++) if (in[off+i]!=prefix[i]) return false;
    return true;
  }
}
