package com.unilink.integration.ocr;

import com.unilink.identity.service.IdCardMatcher;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** Real two-sided Tesseract (D-191): the front and the back are OCR'd
 *  with the same adapter path, and the back's vertical validity strip is
 *  read by a second pass rotated 90 degrees counter-clockwise. Images
 *  are rendered in-test (synthetic — no real card data). The back
 *  address is the university's address (Bateshwar, Sylhet), so the
 *  subject's registered address is the campus address, as the signup
 *  placeholder instructs. */
class TwoSidedTesseractOcrAdapterTest {
  private static final IdCardMatcher.Subject SUBJECT = new IdCardMatcher.Subject(
      "Tanvir Mahmud", "232-115-310", "Metropolitan University",
      "Computer Science and Engineering", "CSE", "Bateshwar, Sylhet-3104");

  /** Front: university, name, ID, programme line with the batch. */
  static byte[] frontImage() throws Exception {
    BufferedImage img = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.WHITE); g.fillRect(0, 0, 1400, 900);
    g.setColor(Color.BLACK); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
    g.drawString("METROPOLITAN UNIVERSITY", 80, 110);
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 38));
    g.drawString("STUDENT IDENTITY CARD", 80, 180);
    g.drawString("Name: Tanvir Mahmud", 80, 300);
    g.drawString("ID: 232-115-310", 80, 380);
    g.drawString("Programme: B.Sc.(Hons) in CSE - 59th Batch", 80, 460);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(img, "png", out));
    return out.toByteArray();
  }
  /** Back: the university + its address/contacts, and the vertical
   *  validity strip on the left edge (text running top-to-bottom, as on
   *  a real card). */
  static byte[] backImage() throws Exception {
    BufferedImage img = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.WHITE); g.fillRect(0, 0, 1400, 900);
    g.setColor(Color.BLACK);
    // The strip: drawn in a frame rotated 90 degrees clockwise, so the
    // stored pixels carry the text top-to-bottom on the left edge.
    Graphics2D strip = (Graphics2D) g.create();
    strip.translate(60, 450);
    strip.rotate(Math.toRadians(90));
    strip.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
    strip.drawString("Validity: July'23 - June'27", -330, 12);
    strip.dispose();
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
    g.drawString("METROPOLITAN UNIVERSITY", 180, 110);
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 38));
    g.drawString("If found kindly return to:", 180, 200);
    g.drawString("Bateshwar, Sylhet-3104", 180, 290);
    g.drawString("Hotline +88 01313-050044", 180, 380);
    g.drawString("Email info@metrouni.edu.bd", 180, 470);
    g.drawString("Web www.metrouni.edu.bd", 180, 560);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(img, "png", out));
    return out.toByteArray();
  }

  @Test void realTwoSidedOcrReadsFrontBackAndValidity() throws Exception {
    var adapter = new TesseractOcrAdapter("eng");
    OcrProvider.TwoSidedResult r = adapter.extractTwoSided(frontImage(), backImage(), "id.png", "id-back.png",
        "Tanvir Mahmud", "232-115-310", "Metropolitan University");
    assertEquals("DONE", r.front().status(), "front text was: " + r.front().text());
    assertTrue(r.front().text().contains("Mahmud"), "front must contain the name; text was: " + r.front().text());
    assertTrue(r.front().text().contains("232-115-310"), "front must contain the ID; text was: " + r.front().text());
    assertEquals("DONE", r.back().status(), "back text was: " + r.back().text());
    assertTrue(r.back().text().contains("METROPOLITAN"), "back must contain the university; text was: " + r.back().text());
    assertTrue(r.back().text().contains("Sylhet"), "back must contain the university address; text was: " + r.back().text());
    assertNotNull(r.validity(), "the rotated pass must read the validity strip; back text was: " + r.back().text());
    assertTrue(r.validity().contains("June"), "validity was: " + r.validity());
    var decision = new IdCardMatcher().matchTwoSided(r.front().text(), r.front().confidence(),
        r.back().text(), r.back().confidence(), r.validity(), "59", SUBJECT);
    assertEquals(IdCardMatcher.Outcome.AUTO_VERIFIED, decision.outcome(),
        "genuine two-sided OCR for the registered data must auto-verify; front=" + r.front().text()
        + " back=" + r.back().text() + " validity=" + r.validity() + " reason=" + decision.reason());
  }
  @Test void nullBackIsAnEmptyFailedSide() throws Exception {
    var adapter = new TesseractOcrAdapter("eng");
    OcrProvider.TwoSidedResult r = adapter.extractTwoSided(frontImage(), null, "id.png", null,
        "Tanvir Mahmud", "232-115-310", "Metropolitan University");
    assertEquals("", r.back().text());
    assertEquals("FAILED", r.back().status());
    assertNull(r.validity());
    var decision = new IdCardMatcher().matchTwoSided(r.front().text(), r.front().confidence(),
        r.back().text(), r.back().confidence(), r.validity(), "59", SUBJECT);
    assertEquals(IdCardMatcher.Outcome.MANUAL_REVIEW, decision.outcome(),
        "a missing back must never auto-verify");
  }
  @Test void rotate90CcwSwapsDimensionsAndFindValidityToleratesMisreads(){
    // A 200x100 image rotated 90 degrees CCW becomes 100x200.
    BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.WHITE); g.fillRect(0, 0, 200, 100);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertDoesNotThrow(() -> ImageIO.write(img, "png", out));
    byte[] rotated = TesseractOcrAdapter.rotate90Ccw(out.toByteArray());
    assertNotNull(rotated);
    BufferedImage back = assertDoesNotThrow(() -> ImageIO.read(new java.io.ByteArrayInputStream(rotated)));
    assertEquals(100, back.getWidth());
    assertEquals(200, back.getHeight());
    assertNull(TesseractOcrAdapter.rotate90Ccw(new byte[]{1, 2, 3}));
    // The validity regex tolerates Tesseract's "Valiaity" misread.
    assertEquals("July'23 - June'27", TesseractOcrAdapter.findValidity("Valiaity: July'23 - June'27"));
    assertEquals("July'23 - June'27", TesseractOcrAdapter.findValidity("Validity : July'23 - June'27"));
    assertNull(TesseractOcrAdapter.findValidity("no validity here"));
    assertNull(TesseractOcrAdapter.findValidity(null));
  }
}
