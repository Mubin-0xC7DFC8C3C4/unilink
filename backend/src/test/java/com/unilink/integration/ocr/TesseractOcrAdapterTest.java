package com.unilink.integration.ocr;

import com.unilink.identity.service.CardPlausibilityChecker;
import com.unilink.identity.service.IdCardMatcher;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** Real Tesseract on a generated ID-card image (D-181): preprocessing,
 *  TSV confidence parsing and the matcher decision on genuine OCR output.
 *  The image is rendered in-test, so no fixture file is needed. */
class TesseractOcrAdapterTest {
  private static final IdCardMatcher.Subject SUBJECT = new IdCardMatcher.Subject(
      "Tanvir Mahmud", "232-115-310", "Metropolitan University",
      "Computer Science and Engineering — MU", "CSE", "House 12, Road 5, Dhanmondi, Dhaka");

  /** A clean, high-contrast card: name, ID, university, department, address. */
  static byte[] cardImage() throws Exception {
    BufferedImage img = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.WHITE); g.fillRect(0, 0, 1400, 900);
    g.setColor(Color.BLACK); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
    g.drawString("METROPOLITAN UNIVERSITY", 80, 110);
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 38));
    g.drawString("STUDENT IDENTITY CARD", 80, 180);
    g.drawString("Name: Tanvir Mahmud", 80, 300);
    g.drawString("ID: 232-115-310", 80, 380);
    g.drawString("Department: Computer Science and Engineering", 80, 460);
    g.drawString("Address: Dhanmondi, Dhaka", 80, 540);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(img, "png", out));
    return out.toByteArray();
  }

  @Test void realOcrReadsTheCardAndAutoVerifies() throws Exception {
    var adapter = new TesseractOcrAdapter("eng");
    OcrProvider.Result r = adapter.extract(cardImage(), "id.png", "Tanvir Mahmud", "232-115-310", "Metropolitan University");
    assertEquals("DONE", r.status(), "tesseract must read the generated card; text was: " + r.text());
    assertTrue(r.text().contains("Mahmud"), "OCR text must contain the name; text was: " + r.text());
    assertNotNull(r.confidence(), "TSV output must yield a mean word confidence");
    assertTrue(r.confidence() > 50, "a clean card must read confidently; confidence was " + r.confidence());
    var decision = new IdCardMatcher().match(r.text(), r.confidence(), SUBJECT);
    assertEquals(IdCardMatcher.Outcome.AUTO_VERIFIED, decision.outcome(),
        "genuine OCR output for the registered data must auto-verify; text was: " + r.text());
  }
  @Test void preprocessingProducesDecodablePng(){
    byte[] png = TesseractOcrAdapter.preprocess(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4});
    // Junk bytes are not a decodable image: the original bytes come back.
    assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4}, png);
  }
  @Test void tsvParsingReconstructsTextAndConfidence(){
    String tsv = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n"
        + "5\t1\t1\t1\t1\t1\t0\t0\t10\t10\t95\tTanvir\n"
        + "5\t1\t1\t1\t1\t2\t0\t0\t10\t10\t88\tMahmud\n"
        + "5\t1\t1\t1\t2\t1\t0\t0\t10\t10\t92\t232-115-310\n";
    var run = TesseractOcrAdapter.parseTsv(tsv);
    assertEquals("Tanvir Mahmud\n232-115-310", run.text());
    assertEquals((95 + 88 + 92) / 3.0, run.meanConfidence(), 0.01);
  }
  @Test void unknownLanguageFallsBackToEnglish() throws Exception {
    var adapter = new TesseractOcrAdapter("xx-bogus");
    OcrProvider.Result r = adapter.extract(cardImage(), "id.png", "Tanvir Mahmud", "232-115-310", "Metropolitan University");
    assertEquals("DONE", r.status(), "a missing language pack must fall back to eng, not fail the card");
    assertTrue(r.text().contains("Mahmud"));
  }
  /** A solid-colour image rendered in-test (synthetic — no real data). */
  static byte[] solidImage(Color colour) throws Exception {
    BufferedImage img = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(colour); g.fillRect(0, 0, 800, 500);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(img, "png", out));
    return out.toByteArray();
  }
  @Test void qualitySignalsFlagSolidImagesAsBlank() throws Exception {
    var white = TesseractOcrAdapter.computeQualitySignals(solidImage(Color.WHITE));
    assertTrue(white.decodable());
    assertEquals(0.0, white.pixelStdDev(), 0.001);
    assertEquals(1.0, white.nearUniformShare(), 0.001);
    assertTrue(new CardPlausibilityChecker().isBlankImage(white, ""),
        "a solid-white upload must read as blank");
    var blue = TesseractOcrAdapter.computeQualitySignals(solidImage(new Color(30, 90, 200)));
    assertTrue(new CardPlausibilityChecker().isBlankImage(blue, ""),
        "a solid-colour upload must read as blank");
    var card = TesseractOcrAdapter.computeQualitySignals(cardImage());
    assertTrue(card.decodable());
    assertTrue(card.pixelStdDev() > 8.0,
        "a genuine card image has contrast; stdDev was " + card.pixelStdDev());
    assertFalse(new CardPlausibilityChecker().isBlankImage(card, "METROPOLITAN UNIVERSITY"),
        "a genuine card image must never read as blank");
    var garbage = TesseractOcrAdapter.computeQualitySignals(new byte[]{1, 2, 3, 4, 5});
    assertFalse(garbage.decodable(), "random bytes are not a decodable image");
    assertFalse(new CardPlausibilityChecker().isBlankImage(garbage, "METROPOLITAN UNIVERSITY"),
        "undecodable bytes that still yielded text are not blank (WebP rule)");
  }
}
