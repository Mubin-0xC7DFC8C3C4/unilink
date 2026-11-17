package com.unilink.integration.ocr;
import org.slf4j.*; import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Component;
import java.nio.file.*; import java.util.concurrent.TimeUnit;
/** Tesseract adapter via ProcessBuilder (ben+eng). Failure never blocks the queue (NFR-FLT-03). */
@Component @Profile({"dev","prod","default"})
public class TesseractOcrAdapter implements OcrProvider {
  private static final Logger log = LoggerFactory.getLogger(TesseractOcrAdapter.class);
  private final String languages;
  public TesseractOcrAdapter(@Value("${app.ocr.languages:ben+eng}") String languages){ this.languages=languages; }
  @Override public Result extract(byte[] imageBytes, String fileName, String fullName, String studentId){
    return extract(imageBytes, fileName, fullName, studentId, null);
  }
  /** I-12: university name is scored as well (FR-AUTH-06). The 5-arg form
   *  is the real entry point; the 4-arg form keeps the old call shape. */
  @Override public Result extract(byte[] imageBytes, String fileName, String fullName, String studentId, String universityName){
    try { Path tmp = Files.createTempFile("unilink-ocr-", ".img"); Files.write(tmp, imageBytes);
      try { Process p = new ProcessBuilder("tesseract", tmp.toString(), "stdout", "-l", languages, "--psm","6")
          .redirectErrorStream(true).start();
        boolean done = p.waitFor(60, TimeUnit.SECONDS);
        if (!done){ p.destroyForcibly(); return new Result("",0.0,"FAILED"); }
        String text = new String(p.getInputStream().readAllBytes());
        if (p.exitValue()!=0) return new Result(text,0.0,"FAILED");
        return new Result(text, score(text, fullName, studentId, universityName), "DONE");
      } finally { Files.deleteIfExists(tmp); }
    } catch (Exception e){ log.warn("OCR failed for {}", fileName, e); return new Result("",0.0,"FAILED"); }
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
