package com.unilink.storage;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** EXIF stripping (D-104 / D-029a), dependency-free: craft minimal JPEG/PNG/WebP
 *  images carrying a GPS EXIF payload and assert the stored bytes no longer
 *  contain the EXIF/GPS payload, while pixel data and unrelated metadata survive. */
class ExifStripperTest {
  private static final byte[] GPS = "GPSLAT37.7749N_GPSLON122.4194W".getBytes(StandardCharsets.US_ASCII);

  // ---- fixture builders ----
  private static byte[] segment(int marker, byte[] payload){
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    b.write(0xFF); b.write(marker);
    int len = payload.length + 2;
    b.write((len >> 8) & 0xFF); b.write(len & 0xFF);
    b.write(payload, 0, payload.length);
    return b.toByteArray();
  }
  private static byte[] concat(byte[]... parts){
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    for (byte[] p : parts) b.write(p, 0, p.length);
    return b.toByteArray();
  }
  private static byte[] exifPayload(){
    return concat("Exif\0\0".getBytes(StandardCharsets.US_ASCII),
      "II*\0\u0008\0\0\0".getBytes(StandardCharsets.ISO_8859_1), GPS);
  }
  static byte[] jpegWithGpsExif(){
    return concat(
      new byte[]{(byte)0xFF, (byte)0xD8}, // SOI
      segment(0xE0, "JFIF\0\u0001\u0001\0\0\u0001\0\u0001\0\0".getBytes(StandardCharsets.ISO_8859_1)), // APP0 JFIF
      segment(0xE1, exifPayload()), // APP1 Exif with GPS
      segment(0xE1, "http://ns.adobe.com/xap/1.0/\0XMPKEEP".getBytes(StandardCharsets.US_ASCII)), // APP1 XMP (not Exif)
      segment(0xDB, new byte[]{0, 1, 2, 3}), // DQT
      segment(0xDA, new byte[]{1, 2, 3}), // SOS header
      new byte[]{0x11, 0x22, (byte)0xFF, 0x00, 0x33}, // scan data (contains a stuffed FF 00)
      new byte[]{(byte)0xFF, (byte)0xD9}); // EOI
  }
  static byte[] pngWithExif(){
    return concat(
      new byte[]{(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10}, // signature
      chunk("IHDR", new byte[]{0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0}),
      chunk("eXIf", concat("II*\0\u0008\0\0\0".getBytes(StandardCharsets.ISO_8859_1), GPS)),
      chunk("IDAT", new byte[]{0x08, 0x11, 0x22, 0x33}),
      chunk("IEND", new byte[0]));
  }
  private static byte[] chunk(String type, byte[] data){
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    int len = data.length;
    b.write((len >> 24) & 0xFF); b.write((len >> 16) & 0xFF); b.write((len >> 8) & 0xFF); b.write(len & 0xFF);
    b.write(type.getBytes(StandardCharsets.US_ASCII), 0, 4);
    b.write(data, 0, data.length);
    b.write(new byte[]{0, 0, 0, 0}, 0, 4); // CRC (values are irrelevant to the stripper)
    return b.toByteArray();
  }
  static byte[] webpWithExif(){
    byte[] vp8x = {0x08, 0, 0, 0, 0, 0, 0, 0, 0, 0}; // flags: EXIF present bit set
    byte[] exif = concat("II*\0\u0008\0\0\0".getBytes(StandardCharsets.ISO_8859_1), GPS, new byte[]{0x5A}); // odd length: exercises chunk padding
    return concat(
      "RIFF".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 0, 0, 0}, // size placeholder, fixed by the test
      "WEBP".getBytes(StandardCharsets.US_ASCII),
      webpChunk("VP8X", vp8x),
      webpChunk("EXIF", exif),
      webpChunk("VP8 ", new byte[]{0x11, 0x22, 0x33, 0x44}));
  }
  private static byte[] webpChunk(String type, byte[] data){
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    b.write(type.getBytes(StandardCharsets.US_ASCII), 0, 4);
    int len = data.length;
    b.write(len & 0xFF); b.write((len >> 8) & 0xFF); b.write((len >> 16) & 0xFF); b.write((len >> 24) & 0xFF);
    b.write(data, 0, data.length);
    if (data.length % 2 == 1) b.write(0); // even padding
    return b.toByteArray();
  }
  private static boolean contains(byte[] haystack, byte[] needle){
    outer: for (int i = 0; i + needle.length <= haystack.length; i++){
      for (int j = 0; j < needle.length; j++) if (haystack[i+j]!=needle[j]) continue outer;
      return true;
    }
    return false;
  }

  @Test void jpegExifSegmentDroppedButJfifXmpAndScanDataKept() {
    byte[] in = jpegWithGpsExif();
    assertTrue(contains(in, "Exif".getBytes(StandardCharsets.US_ASCII)), "fixture must contain Exif");
    byte[] out = ExifStripper.strip(in);
    assertFalse(contains(out, "Exif".getBytes(StandardCharsets.US_ASCII)), "no Exif marker may survive");
    assertFalse(contains(out, GPS), "GPS payload must be gone");
    assertTrue(contains(out, "JFIF".getBytes(StandardCharsets.US_ASCII)), "APP0 JFIF is not EXIF and must survive");
    assertTrue(contains(out, "XMPKEEP".getBytes(StandardCharsets.US_ASCII)), "non-Exif APP1 (XMP) must survive");
    assertTrue(contains(out, new byte[]{0x11, 0x22, (byte)0xFF, 0x00, 0x33}), "scan data must survive byte-for-byte");
    assertEquals((byte)0xFF, out[0]); assertEquals((byte)0xD8, out[1]); // SOI intact
    assertEquals((byte)0xFF, out[out.length-2]); assertEquals((byte)0xD9, out[out.length-1]); // EOI intact
    assertTrue(out.length < in.length, "dropping a segment must shrink the file");
  }

  @Test void jpegWithoutExifIsUnchanged() {
    byte[] in = concat(new byte[]{(byte)0xFF, (byte)0xD8},
      segment(0xE0, "JFIF\0\u0001\u0001\0".getBytes(StandardCharsets.ISO_8859_1)),
      segment(0xDA, new byte[]{1}), new byte[]{0x55}, new byte[]{(byte)0xFF, (byte)0xD9});
    assertArrayEquals(in, ExifStripper.strip(in));
  }

  @Test void jpegWithOnlyNonExifApp1IsUnchanged() {
    byte[] in = concat(new byte[]{(byte)0xFF, (byte)0xD8},
      segment(0xE1, "http://ns.adobe.com/xap/1.0/\0XMPKEEP".getBytes(StandardCharsets.US_ASCII)),
      new byte[]{(byte)0xFF, (byte)0xD9});
    assertArrayEquals(in, ExifStripper.strip(in));
  }

  @Test void malformedJpegIsReturnedUnchangedNotThrown() {
    // APP1 claims a length far past EOF: unparseable, so fail-open (see class doc).
    byte[] in = {(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE1, 0x01, 0x00, 'E', 'x'};
    assertArrayEquals(in, ExifStripper.strip(in));
    assertArrayEquals(new byte[]{1, 2, 3}, ExifStripper.strip(new byte[]{1, 2, 3}));
    assertNull(ExifStripper.strip(null));
  }

  @Test void pngExifChunkDroppedButImageChunksKept() {
    byte[] in = pngWithExif();
    byte[] out = ExifStripper.strip(in);
    assertFalse(contains(out, "eXIf".getBytes(StandardCharsets.US_ASCII)), "no eXIf chunk may survive");
    assertFalse(contains(out, GPS), "GPS payload must be gone");
    assertTrue(contains(out, "IHDR".getBytes(StandardCharsets.US_ASCII)));
    assertTrue(contains(out, "IDAT".getBytes(StandardCharsets.US_ASCII)));
    assertTrue(contains(out, "IEND".getBytes(StandardCharsets.US_ASCII)));
    assertTrue(out.length < in.length);
  }

  @Test void pngWithoutExifIsUnchanged() {
    byte[] in = concat(new byte[]{(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10},
      chunk("IHDR", new byte[]{0, 0, 0, 1}), chunk("IEND", new byte[0]));
    assertArrayEquals(in, ExifStripper.strip(in));
  }

  @Test void webpExifChunkDroppedRiffSizeFixedAndFlagCleared() {
    byte[] in = webpWithExif();
    // Fix the fixture's RIFF size the way a real encoder would.
    int riffSize = in.length - 8;
    in[4] = (byte)(riffSize & 0xFF); in[5] = (byte)((riffSize >> 8) & 0xFF);
    in[6] = (byte)((riffSize >> 16) & 0xFF); in[7] = (byte)((riffSize >> 24) & 0xFF);
    byte[] out = ExifStripper.strip(in);
    assertFalse(contains(out, GPS), "GPS payload must be gone");
    assertFalse(contains(out, "EXIF".getBytes(StandardCharsets.US_ASCII)), "no EXIF chunk may survive");
    assertTrue(contains(out, "VP8X".getBytes(StandardCharsets.US_ASCII)));
    assertTrue(contains(out, new byte[]{0x11, 0x22, 0x33, 0x44}), "VP8 payload must survive");
    int outRiffSize = (out[4] & 0xFF) | ((out[5] & 0xFF) << 8) | ((out[6] & 0xFF) << 16) | ((out[7] & 0xFF) << 24);
    assertEquals(out.length - 8, outRiffSize, "RIFF size must describe the stripped file");
    int vp8xAt = new String(out, StandardCharsets.US_ASCII).indexOf("VP8X");
    assertEquals(0, out[vp8xAt + 8] & 0x08, "VP8X EXIF-present flag must be cleared");
    assertTrue(out.length < in.length);
  }

  @Test void webpWithoutExifIsUnchanged() {
    byte[] in = concat("RIFF".getBytes(StandardCharsets.US_ASCII), new byte[]{20, 0, 0, 0},
      "WEBP".getBytes(StandardCharsets.US_ASCII), webpChunk("VP8 ", new byte[]{1, 2, 3, 4}));
    assertArrayEquals(in, ExifStripper.strip(in));
  }

  @Test void pdfPassesThroughUnchanged() {
    byte[] pdf = "%PDF-1.7\n%GPSLAT37.7749N\n".getBytes(StandardCharsets.US_ASCII);
    assertArrayEquals(pdf, ExifStripper.strip(pdf), "PDFs carry no EXIF segment; stripping must not touch them");
  }
}
