package com.unilink.integration.ocr;
import org.slf4j.*; import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Component;
import java.awt.Graphics2D; import java.awt.image.BufferedImage; import java.awt.image.RescaleOp;
import java.io.ByteArrayInputStream; import java.nio.file.*; import java.util.ArrayList; import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
/** Tesseract adapter via ProcessBuilder (ben+eng). Failure never blocks the queue (NFR-FLT-03).
 *  D-181: the image is preprocessed in pure Java (no new dependency — Maven
 *  is offline): grayscale, contrast stretch, 2x upscale for small images.
 *  Formats ImageIO cannot decode (e.g. WebP) fall back to the raw bytes,
 *  which Tesseract reads directly. OCR runs with TSV output so the mean
 *  word confidence is a real signal for the matcher; if the configured
 *  languages fail (e.g. a missing .traineddata file), the run retries
 *  once with English alone instead of failing every card. */
@Component @Profile({"dev","prod","default"})
public class TesseractOcrAdapter implements OcrProvider {
  private static final Logger log = LoggerFactory.getLogger(TesseractOcrAdapter.class);
  private final String languages;
  public TesseractOcrAdapter(@Value("${app.ocr.languages:ben+eng}") String languages){ this.languages=languages; }

  @Override public Result extract(byte[] imageBytes, String fileName, String fullName, String studentId){
    return extract(imageBytes, fileName, fullName, studentId, null);
  }
  /** D-191: OCR both sides. The back is read with the same adapter path;
   *  a second pass on the back rotated 90 degrees counter-clockwise reads
   *  the vertical validity strip ("Validity : July'23 - June'27") that
   *  the default psm 6 pass cannot see — the strip is on the card's left
   *  edge and only the rotated text is searched for it, so the garbled
   *  rest of the rotated image is harmless. A null back image yields an
   *  empty FAILED back side (the matcher treats it as unreadable). */
  @Override public TwoSidedResult extractTwoSided(byte[] frontBytes, byte[] backBytes, String frontName, String backName,
                                                  String fullName, String studentId, String universityName){
    Result front = extract(frontBytes, frontName, fullName, studentId, universityName);
    Result back = backBytes == null ? new Result("", 0.0, "FAILED", null)
        : extract(backBytes, backName, fullName, studentId, universityName);
    String validity = backBytes == null ? null : extractValidity(backBytes);
    return new TwoSidedResult(new SideResult(front.text(), front.confidence(), front.status()),
        new SideResult(back.text(), back.confidence(), back.status()), validity);
  }
  /** The validity strip, e.g. "July'23 - June'27". The prefix is
   *  deliberately tolerant: real Tesseract output misreads "Validity"
   *  as "Valiaity" (the 'd' becomes 'a'), so the pattern accepts any
   *  v…t[yi] word before the date range. Only the first match is
   *  returned; an unreadable or absent strip is null (neutral). */
  private static final java.util.regex.Pattern VALIDITY_PATTERN =
      java.util.regex.Pattern.compile("(?i)v[a-z]{1,6}t[yi][^A-Za-z0-9]{0,5}([A-Za-z]{3,9}'?\\d{2}\\s*-\\s*[A-Za-z]{3,9}'?\\d{2})");
  static String findValidity(String text){
    if (text == null) return null;
    var m = VALIDITY_PATTERN.matcher(text);
    return m.find() ? m.group(1).trim() : null;
  }
  /** Rotates the image 90 degrees counter-clockwise (the direction that
   *  makes a left-edge vertical strip read horizontally) and runs
   *  Tesseract on it, returning the validity string or null. Best effort:
   *  any failure is null, never an exception. Visible for testing. */
  static String extractValidity(byte[] backBytes){
    try {
      byte[] rotated = rotate90Ccw(backBytes);
      if (rotated == null) return null;
      byte[] prepared = preprocess(rotated);
      Path tmp = Files.createTempFile("unilink-ocr-validity-", ".png"); Files.write(tmp, prepared);
      try {
        TsvRun run = runTesseract(tmp, "eng");
        return run == null ? null : findValidity(run.text());
      } finally { Files.deleteIfExists(tmp); }
    } catch (Exception e){ return null; }
  }
  /** 90-degree counter-clockwise rotation (PIL Image.rotate(90)
   *  equivalent), re-encoded as PNG. Returns null when the bytes are not
   *  a decodable image. Visible for testing. */
  static byte[] rotate90Ccw(byte[] imageBytes){
    try {
      BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
      if (src == null) return null;
      int w = src.getWidth(), h = src.getHeight();
      BufferedImage out = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = out.createGraphics();
      g.setColor(java.awt.Color.WHITE); g.fillRect(0, 0, h, w);
      java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
      at.translate(h / 2.0, w / 2.0);
      at.rotate(Math.toRadians(-90));
      at.translate(-w / 2.0, -h / 2.0);
      g.drawImage(src, at, null);
      g.dispose();
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      if (!ImageIO.write(out, "png", baos)) return null;
      return baos.toByteArray();
    } catch (Exception e){ return null; }
  }
  /** I-12: university name is scored as well (FR-AUTH-06). The 5-arg form
   *  is the real entry point; the 4-arg form keeps the old call shape. */
  @Override public Result extract(byte[] imageBytes, String fileName, String fullName, String studentId, String universityName){
    try {
      byte[] prepared = preprocess(imageBytes);
      String suffix = prepared == imageBytes ? ".img" : ".png";
      Path tmp = Files.createTempFile("unilink-ocr-", suffix); Files.write(tmp, prepared);
      try {
        TsvRun run = runTesseract(tmp, languages);
        if (run == null && !"eng".equals(languages)){
          log.warn("OCR with languages {} failed for {}; retrying with eng", languages, fileName);
          run = runTesseract(tmp, "eng");
        }
        if (run == null) return new Result("", 0.0, "FAILED", null);
        return new Result(run.text(), score(run.text(), fullName, studentId, universityName), "DONE", run.meanConfidence());
      } finally { Files.deleteIfExists(tmp); }
    } catch (Exception e){ log.warn("OCR failed for {}", fileName, e); return new Result("", 0.0, "FAILED", null); }
  }

  /** Grayscale + contrast + upscale. Returns the original bytes when the
   *  image cannot be decoded (the caller still sends them to Tesseract). */
  static byte[] preprocess(byte[] imageBytes){
    try {
      BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
      if (src == null) return imageBytes;
      BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
      Graphics2D g = gray.createGraphics(); g.drawImage(src, 0, 0, null); g.dispose();
      BufferedImage work = gray;
      if (gray.getWidth() < 1500){
        BufferedImage up = new BufferedImage(gray.getWidth() * 2, gray.getHeight() * 2, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2 = up.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(gray, 0, 0, up.getWidth(), up.getHeight(), null); g2.dispose();
        work = up;
      }
      BufferedImage out = new RescaleOp(1.3f, -20f, null).filter(work, null);
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      if (!ImageIO.write(out, "png", baos)) return imageBytes;
      return baos.toByteArray();
    } catch (Exception e){ return imageBytes; }
  }

  record TsvRun(String text, Double meanConfidence) {}
  /** One tesseract run with TSV output. Returns null when the process
   *  fails (non-zero exit or timeout), so the caller can retry. */
  private static TsvRun runTesseract(Path image, String langs){
    try {
      Process p = new ProcessBuilder("tesseract", image.toString(), "stdout", "-l", langs, "--psm", "6", "tsv")
          .redirectErrorStream(true).start();
      boolean done = p.waitFor(60, TimeUnit.SECONDS);
      if (!done){ p.destroyForcibly(); return null; }
      String raw = new String(p.getInputStream().readAllBytes());
      if (p.exitValue() != 0) return null;
      return parseTsv(raw);
    } catch (Exception e){ return null; }
  }

  /** TSV columns: level page block par line word left top width height conf text.
   *  Words are re-joined with spaces and a newline when the line number
   *  changes; mean confidence averages the words that carry one (conf -1
   *  means "no text"). Visible for testing. */
  static TsvRun parseTsv(String raw){
    if (raw == null || raw.isBlank()) return new TsvRun("", null);
    String[] lines = raw.split("\\R");
    StringBuilder text = new StringBuilder();
    List<Integer> confs = new ArrayList<>();
    int prevLine = -1;
    for (int i = 1; i < lines.length; i++){ // line 0 is the header
      String[] cols = lines[i].split("\\t", -1);
      if (cols.length < 12) continue;
      if (!"5".equals(cols[0])) continue; // level 5 = word
      String word = cols[11];
      if (word == null || word.isBlank()) continue;
      int lineNo;
      try { lineNo = Integer.parseInt(cols[4]); } catch (NumberFormatException e){ lineNo = prevLine; }
      if (text.length() > 0) text.append(lineNo != prevLine ? '\n' : ' ');
      text.append(word);
      try { int c = (int) Math.round(Double.parseDouble(cols[10])); if (c >= 0) confs.add(c); }
      catch (NumberFormatException ignored){ /* no confidence for this word */ }
      prevLine = lineNo;
    }
    Double mean = confs.isEmpty() ? null : confs.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    return new TsvRun(text.toString(), mean);
  }

  /** Heuristic match score: fraction of name tokens + student-id digits found in OCR text. */
  static double score(String text, String fullName, String studentId){
    return score(text, fullName, studentId, null);
  }
  /** I-12: as above, plus the university name. Generic words that appear on
   *  almost every ID card ("university", "of", "the", "institute", "college")
   *  and short tokens are skipped, so a card that merely says "University"
   *  scores nothing for the university component — only distinctive tokens
   *  (e.g. "dhaka", "metropolitan") count. When the university name yields
   *  no distinctive token the component is skipped entirely, so the score is
   *  unchanged for such names instead of being diluted. */
  static double score(String text, String fullName, String studentId, String universityName){
    if (text==null || text.isBlank()) return 0.0;
    String lower = text.toLowerCase();
    String[] tokens = fullName==null?new String[0]:fullName.toLowerCase().split("\\s+");
    int hit=0, total=0;
    for (String t : tokens){ if (t.length()<2) continue; total++; if (lower.contains(t)) hit++; }
    if (studentId!=null && !studentId.isBlank()){ total++; if (lower.replaceAll("\\s+","").contains(studentId.toLowerCase().replaceAll("\\s+",""))) hit++; }
    if (universityName!=null && !universityName.isBlank()){
      java.util.Set<String> stopwords = java.util.Set.of("university","of","the","and","institute","college","school","united","national");
      int uniHit=0, uniTotal=0;
      for (String t : universityName.toLowerCase().split("\\s+")){
        if (t.length()<3 || stopwords.contains(t)) continue;
        uniTotal++; if (lower.contains(t)) uniHit++;
      }
      if (uniTotal>0){ hit+=uniHit; total+=uniTotal; }
    }
    return total==0?0.0:(double)hit/total;
  }
}
