package com.unilink.identity;

import com.unilink.identity.service.CardPlausibilityChecker;
import com.unilink.integration.ocr.OcrProvider.QualitySignals;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Card plausibility (D-197): image-quality signals, OCR-text structure
 *  and the university-agnostic card-marker lexicon. The checker is pure,
 *  so these tests need no Tesseract and no images — quality signals are
 *  supplied directly (the adapter's measurement of real pixels is covered
 *  by TesseractOcrAdapterTest). Fixtures are synthetic; no real card data
 *  is used. */
class CardPlausibilityCheckerTest {
  private static final CardPlausibilityChecker CHECKER = new CardPlausibilityChecker();
  /** A solid-white / solid-colour image: no pixel variation, every pixel
   *  at the modal grey value. */
  private static final QualitySignals BLANK = new QualitySignals(true, 0.0, 1.0);
  private static final QualitySignals SOLID_COLOUR = new QualitySignals(true, 0.5, 0.99);
  /** A real photo: varied pixels, no dominant grey value. */
  private static final QualitySignals REAL_PHOTO = new QualitySignals(true, 45.0, 0.4);
  /** A card on white stock: a white background (high near-mode share)
   *  but far too much contrast for the blank bar. */
  private static final QualitySignals WHITE_BACKGROUND_CARD = new QualitySignals(true, 55.0, 0.92);
  /** Bytes ImageIO cannot decode (a corrupt file — or a WebP, which
   *  ImageIO cannot read but Tesseract can). */
  private static final QualitySignals UNDECODABLE = new QualitySignals(false, -1.0, -1.0);
  private static final String CARD_FRONT = """
      METROPOLITAN UNIVERSITY
      STUDENT IDENTITY CARD
      Name: Tanvir Mahmud
      ID: 232-115-310
      Programme: B.Sc.(Hons) in CSE - 59th Batch
      """;
  private static final String CARD_BACK = """
      METROPOLITAN UNIVERSITY
      If found kindly return to:
      Bateshwar, Sylhet-3104
      Validity: July'23 - June'27
      """;
  private static final String PROSE = """
      The quick brown fox jumps over the lazy dog while the river flows
      gently through the valley below the hills, and the morning light
      warms the quiet fields where the travellers rest.
      """;

  @Test void solidWhiteImageIsBlank(){
    assertTrue(CHECKER.isBlankImage(BLANK, ""));
    assertTrue(CHECKER.isBlankImage(BLANK, "   "), "whitespace-only OCR text does not rescue a blank image");
  }
  @Test void solidColourImageIsBlank(){
    assertTrue(CHECKER.isBlankImage(SOLID_COLOUR, ""));
  }
  @Test void realPhotoIsNotBlank(){
    assertFalse(CHECKER.isBlankImage(REAL_PHOTO, ""));
    assertFalse(CHECKER.isBlankImage(REAL_PHOTO, "Smiling face outdoors"));
  }
  @Test void whiteBackgroundCardIsNotBlank(){
    assertFalse(CHECKER.isBlankImage(WHITE_BACKGROUND_CARD, CARD_FRONT),
        "a real card on white stock must never be called blank");
  }
  @Test void unknownQualityIsNeutral(){
    assertFalse(CHECKER.isBlankImage(QualitySignals.unknown(), ""));
    assertFalse(CHECKER.isBlankImage(null, ""), "no signal must never fail a side");
  }
  @Test void undecodableWithNoTextIsBlankButWithTextIsNot(){
    assertTrue(CHECKER.isBlankImage(UNDECODABLE, ""),
        "an undecodable image that yielded nothing readable is corrupt");
    assertFalse(CHECKER.isBlankImage(UNDECODABLE, CARD_FRONT),
        "a genuine WebP card is undecodable to ImageIO but readable to Tesseract");
  }
  @Test void textStructureCountsWordsLinesAndDigitRuns(){
    var s = CardPlausibilityChecker.structure("Name: Tanvir Mahmud\nID: 232-115-310");
    assertEquals(5, s.wordCount());
    assertEquals(2, s.lineCount());
    assertTrue(s.hasDigitRun(), "the student ID is a digit run");
    var plain = CardPlausibilityChecker.structure("hello world");
    assertEquals(2, plain.wordCount());
    assertEquals(1, plain.lineCount());
    assertFalse(plain.hasDigitRun());
    assertEquals(0, CardPlausibilityChecker.structure("  \n ").wordCount());
  }
  @Test void markerLexiconCountsDistinctCardWords(){
    assertTrue(CardPlausibilityChecker.countDistinctMarkers(CARD_FRONT) >= 2,
        "a real card front carries several markers");
    assertTrue(CardPlausibilityChecker.countDistinctMarkers(CARD_FRONT + "\n" + CARD_BACK) >= 2);
    assertEquals(0, CardPlausibilityChecker.countDistinctMarkers(PROSE),
        "prose must not accidentally contain a card marker");
    assertEquals(0, CardPlausibilityChecker.countDistinctMarkers("Smiling face outdoors"));
  }
  @Test void substantialProseWithZeroFieldMatchesIsClearlyNotIdCard(){
    assertTrue(CHECKER.isClearlyNotIdCard(PROSE, PROSE, false, 0.2, false));
  }
  @Test void oneFieldMatchSavesSubstantialText(){
    assertFalse(CHECKER.isClearlyNotIdCard(PROSE, PROSE, true, 0.2, false),
        "a matching student ID means the text could be a misread card");
    assertFalse(CHECKER.isClearlyNotIdCard(PROSE, PROSE, false, 0.8, false),
        "a matching name means the text could be a misread card");
    assertFalse(CHECKER.isClearlyNotIdCard(PROSE, PROSE, false, 0.2, true),
        "a matching university means the text could be a misread card");
  }
  @Test void markersSaveSubstantialText(){
    assertFalse(CHECKER.isClearlyNotIdCard(CARD_FRONT, CARD_BACK, false, 0.0, false),
        "a card that matches no field is still recognisably a card");
  }
  @Test void shortTextIsNeverClearlyNotIdCard(){
    assertFalse(CHECKER.isClearlyNotIdCard("Smiling face outdoors", "", false, 0.0, false),
        "a selfie with a few words is weak evidence, not proof");
    assertFalse(CHECKER.isClearlyNotIdCard("", "", false, 0.0, false));
  }
}
