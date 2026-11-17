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
    try { Path tmp = Files.createTempFile("unilink-ocr-", ".img"); Files.write(tmp, imageBytes);
      try { Process p = new ProcessBuilder("tesseract", tmp.toString(), "stdout", "-l", languages, "--psm","6")
          .redirectErrorStream(true).start();
        boolean done = p.waitFor(60, TimeUnit.SECONDS);
        if (!done){ p.destroyForcibly(); return new Result("",0.0,"FAILED"); }
        String text = new String(p.getInputStream().readAllBytes());
        if (p.exitValue()!=0) return new Result(text,0.0,"FAILED");
        return new Result(text, score(text, fullName, studentId), "DONE");
      } finally { Files.deleteIfExists(tmp); }
    } catch (Exception e){ log.warn("OCR failed for {}", fileName, e); return new Result("",0.0,"FAILED"); }
  }
  /** Heuristic match score: fraction of name tokens + student-id digits found in OCR text. */
  static double score(String text, String fullName, String studentId){
    if (text==null || text.isBlank()) return 0.0;
    String lower = text.toLowerCase();
    String[] tokens = fullName==null?new String[0]:fullName.toLowerCase().split("\\s+");
    int hit=0, total=0;
    for (String t : tokens){ if (t.length()<2) continue; total++; if (lower.contains(t)) hit++; }
    if (studentId!=null && !studentId.isBlank()){ total++; if (lower.replaceAll("\\s+","").contains(studentId.replaceAll("\\s+",""))) hit++; }
    return total==0?0.0:(double)hit/total;
  }
}
