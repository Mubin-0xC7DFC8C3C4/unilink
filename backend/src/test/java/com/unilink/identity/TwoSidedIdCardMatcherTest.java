package com.unilink.identity;

import com.unilink.identity.service.IdCardMatcher;
import com.unilink.identity.service.IdCardMatcher.Decision;
import com.unilink.identity.service.IdCardMatcher.Outcome;
import com.unilink.integration.ocr.OcrProvider;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

/** Both-sides OCR matching (D-191): the front carries the auto-verify
 *  gates, the back is consistency evidence (university, address,
 *  readability) plus the same-side-twice, batch and validity signals.
 *  A weak name similarity never overrides a conflicting student ID —
 *  the ID match is a hard gate for AUTO_VERIFIED. Fixtures are synthetic;
 *  no real card data is used. */
class TwoSidedIdCardMatcherTest {
  private static final IdCardMatcher.Subject SUBJECT = new IdCardMatcher.Subject(
      "Tanvir Mahmud", "232-115-310", "Metropolitan University",
      "Computer Science and Engineering", "CSE", "Bateshwar, Sylhet-3104");
  private static final String FRONT = """
      METROPOLITAN UNIVERSITY
      STUDENT IDENTITY CARD
      Name: Tanvir Mahmud
      ID: 232-115-310
      Programme: B.Sc.(Hons) in CSE - 59th Batch
      """;
  private static final String BACK = """
      METROPOLITAN UNIVERSITY
      If found kindly return to:
      Bateshwar, Sylhet-3104
      Hotline +88 01313-050044
      Email info@metrouni.edu.bd
      Web www.metrouni.edu.bd
      """;
  private static final String DU_BACK = """
      UNIVERSITY OF DHAKA
      If found kindly return to:
      Dhaka-1000
      DU
      """;

  private Decision twoSided(String front, String back, String validity, String batch){
    return new IdCardMatcher().matchTwoSided(front, 92.0, back, 90.0, validity, batch, SUBJECT);
  }

  @Test void bothSidesValidAutoVerifies(){
    Decision d = twoSided(FRONT, BACK, null, "59");
    assertEquals(Outcome.AUTO_VERIFIED, d.outcome());
    assertTrue(d.idMatched());
    assertFalse(d.addressConflict(), "front and back agree with the registered address");
  }
  @Test void backAddressInDifferentCityStillAutoVerifies(){
    // D-196: the registered address is the Sylhet campus, the back of
    // the card names Dhaka — an address mismatch is not an identity
    // mismatch, so the card still auto-verifies and the conflict is
    // recorded as admin-only evidence.
    String dhakaBack = BACK.replace("Bateshwar, Sylhet-3104", "Dhaka-1000");
    Decision d = twoSided(FRONT, dhakaBack, null, "59");
    assertEquals(Outcome.AUTO_VERIFIED, d.outcome());
    assertTrue(d.addressConflict(), "the gazetteer conflict is still computed as admin evidence");
  }
  @Test void backMissingIsReviewNeverAutoVerify(){
    Decision d = twoSided(FRONT, "", null, "59");
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
    assertEquals(IdCardMatcher.REASON_BACK_UNREADABLE, d.reason());
    Decision nullBack = new IdCardMatcher().matchTwoSided(FRONT, 92.0, null, null, null, "59", SUBJECT);
    assertEquals(Outcome.MANUAL_REVIEW, nullBack.outcome());
    assertEquals(IdCardMatcher.REASON_BACK_UNREADABLE, nullBack.reason());
  }
  @Test void backUnreadableIsReview(){
    Decision d = twoSided(FRONT, "xx", null, "59");
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
    assertEquals(IdCardMatcher.REASON_BACK_UNREADABLE, d.reason());
  }
  @Test void backWrongUniversityIsReview(){
    Decision d = twoSided(FRONT, DU_BACK, null, "59");
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
    assertEquals(IdCardMatcher.REASON_BACK_UNIVERSITY, d.reason());
  }
  @Test void sameImageAsBothSidesIsReview(){
    // The back is the front's text: it contains the student ID and the
    // name, so it cannot be the back of the card.
    Decision d = twoSided(FRONT, FRONT, null, "59");
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
    assertEquals(IdCardMatcher.REASON_SAME_SIDE, d.reason());
  }
  @Test void batchConflictIsReview(){
    Decision d = twoSided(FRONT, BACK, null, "44");
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
    assertEquals(IdCardMatcher.REASON_BATCH, d.reason());
  }
  @Test void batchAbsentFromCardIsNeutral(){
    String noProgramme = FRONT.replace("Programme: B.Sc.(Hons) in CSE - 59th Batch\n", "");
    Decision d = twoSided(noProgramme, BACK, null, "44");
    assertEquals(Outcome.AUTO_VERIFIED, d.outcome(), "a card that prints no batch must not reject the entered value");
  }
  @Test void batchFormatsMatchAfterNormalisation(){
    assertEquals(Outcome.AUTO_VERIFIED, twoSided(FRONT, BACK, null, "59th Batch").outcome());
    assertEquals(Outcome.AUTO_VERIFIED, twoSided(FRONT, BACK, null, " 59 ").outcome());
    String sessionFront = FRONT.replace("59th Batch", "Session 2022-23");
    Decision d = new IdCardMatcher().matchTwoSided(sessionFront, 92.0, BACK, 90.0, null, "2022-23", SUBJECT);
    assertEquals(Outcome.AUTO_VERIFIED, d.outcome());
  }
  @Test void expiredValidityIsReview(){
    Decision d = twoSided(FRONT, BACK, "July'20 - June'24", "59");
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
    assertEquals(IdCardMatcher.REASON_EXPIRED, d.reason());
  }
  @Test void unexpiredValidityPasses(){
    assertEquals(Outcome.AUTO_VERIFIED, twoSided(FRONT, BACK, "July'23 - June'27", "59").outcome());
  }
  @Test void unparseableValidityIsNeutral(){
    assertEquals(Outcome.AUTO_VERIFIED, twoSided(FRONT, BACK, "garbled", "59").outcome());
    assertEquals(Outcome.AUTO_VERIFIED, twoSided(FRONT, BACK, null, "59").outcome());
  }
  @Test void frontFailedStaysFailedDespiteValidBack(){
    // Wrong ID + a name far below the 0.50 hard-fail threshold (the
    // "Sakib Hasan" pair from the single-side tests): two hard failures.
    String wrong = FRONT.replace("232-115-310", "999-888-777").replace("Tanvir Mahmud", "Sakib Hasan");
    Decision d = twoSided(wrong, BACK, null, "59");
    assertEquals(Outcome.VERIFICATION_FAILED, d.outcome(), "the back cannot rescue a front that contradicts the registration");
  }
  @Test void weakNameNeverOverridesConflictingId(){
    // Right name, wrong ID: one hard failure. The front degrades to
    // MANUAL_REVIEW, and the back must not promote it to AUTO_VERIFIED.
    String wrongId = FRONT.replace("232-115-310", "232-115-999");
    Decision d = twoSided(wrongId, BACK, null, "59");
    assertFalse(d.idMatched());
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
  }
  @Test void extractBatchReadsProgrammeAndSession(){
    assertEquals("59", IdCardMatcher.extractBatch(FRONT));
    assertEquals("2022-23", IdCardMatcher.extractBatch("Programme: BBA\nSession 2022-23\n"));
    assertEquals("59", IdCardMatcher.extractBatch("Batch 59\n"));
    assertNull(IdCardMatcher.extractBatch("No batch printed here\n"));
    assertNull(IdCardMatcher.extractBatch(null));
  }
  @Test void compareBatchNormalisesOrdinalsAndFormats(){
    assertEquals(IdCardMatcher.BatchResult.MATCHED, IdCardMatcher.compareBatch("59", "59th Batch"));
    assertEquals(IdCardMatcher.BatchResult.MATCHED, IdCardMatcher.compareBatch("59", "59"));
    assertEquals(IdCardMatcher.BatchResult.MATCHED, IdCardMatcher.compareBatch("2022-23", "2022-23"));
    assertEquals(IdCardMatcher.BatchResult.CONFLICT, IdCardMatcher.compareBatch("59", "44"));
    assertEquals(IdCardMatcher.BatchResult.CONFLICT, IdCardMatcher.compareBatch("59", "Fall 2024"));
    assertEquals(IdCardMatcher.BatchResult.NEUTRAL, IdCardMatcher.compareBatch(null, "44"));
    assertEquals(IdCardMatcher.BatchResult.NEUTRAL, IdCardMatcher.compareBatch("59", null));
    assertEquals(IdCardMatcher.BatchResult.NEUTRAL, IdCardMatcher.compareBatch("59", ""));
  }
  @Test void parseValidityEndAndExpiry(){
    assertEquals(LocalDate.of(2027, 6, 30), IdCardMatcher.parseValidityEnd("July'23 - June'27"));
    assertEquals(LocalDate.of(2027, 6, 30), IdCardMatcher.parseValidityEnd("Valiaity: July'23 - June'27"));
    assertNull(IdCardMatcher.parseValidityEnd("garbled"));
    assertNull(IdCardMatcher.parseValidityEnd(null));
    assertTrue(IdCardMatcher.isExpired("July'20 - June'24", LocalDate.of(2026, 11, 25)));
    assertFalse(IdCardMatcher.isExpired("July'23 - June'27", LocalDate.of(2026, 11, 25)));
    assertFalse(IdCardMatcher.isExpired("July'23 - June'27", LocalDate.of(2027, 6, 30)));
    assertTrue(IdCardMatcher.isExpired("July'23 - June'27", LocalDate.of(2027, 7, 1)));
    assertFalse(IdCardMatcher.isExpired(null, LocalDate.of(2026, 11, 25)));
  }

  // ---- D-197: plausibility — blank images, unrelated text, weak evidence ----
  private static OcrProvider.SideResult side(String text, Double confidence, OcrProvider.QualitySignals quality){
    return new OcrProvider.SideResult(text, confidence, "DONE", quality);
  }
  /** A solid-white / solid-colour image: zero pixel variation, every
   *  pixel at the modal grey value. */
  private static final OcrProvider.QualitySignals BLANK =
      new OcrProvider.QualitySignals(true, 0.0, 1.0);
  /** A real photo or card: varied pixels, no dominant grey value. */
  private static final OcrProvider.QualitySignals REAL_PHOTO =
      new OcrProvider.QualitySignals(true, 45.0, 0.4);
  /** Substantial prose: long, no card markers, no registered field. */
  private static final String PROSE_FRONT = """
      The quick brown fox jumps over the lazy dog while the river flows
      gently through the valley below the hills, and the morning light
      warms the quiet fields where the travellers rest.
      """;
  private static final String PROSE_BACK = """
      A long road winds past the old mill, the orchard and the meadow,
      where the wind moves softly through the tall grass at dusk.
      """;
  /** A different university's layout (ID No, "Department of …",
   *  Session) for a Dhaka student — the plausibility check must be
   *  university-agnostic, so this layout verifies exactly like MU's. */
  private static final IdCardMatcher.Subject DU_SUBJECT = new IdCardMatcher.Subject(
      "Ayesha Rahman", "12345678", "University of Dhaka",
      "Computer Science and Engineering", "CSE", "Dhaka-1000");
  private static final String DU_FRONT = """
      UNIVERSITY OF DHAKA
      STUDENT ID CARD
      Name: Ayesha Rahman
      ID No: 12345678
      Department of Computer Science and Engineering
      Session: 2022-23
      """;
  private static final String DU_CARD_BACK = """
      UNIVERSITY OF DHAKA
      If found kindly return to:
      Dhaka-1000
      Validity: July'23 - June'27
      """;

  @Test void blankFrontImageFailsWithBlankReason(){
    // The quality rule fires before field matching, even though the OCR
    // text itself would verify — the image is not a card photo.
    Decision d = new IdCardMatcher().matchTwoSided(
        side(FRONT, 92.0, BLANK), side(BACK, 90.0, REAL_PHOTO), null, "59", SUBJECT);
    assertEquals(Outcome.VERIFICATION_FAILED, d.outcome());
    assertEquals(IdCardMatcher.REASON_BLANK_IMAGE, d.reason());
  }
  @Test void blankBackImageFailsWithBlankReason(){
    Decision d = new IdCardMatcher().matchTwoSided(
        side(FRONT, 92.0, REAL_PHOTO), side(BACK, 90.0, BLANK), null, "59", SUBJECT);
    assertEquals(Outcome.VERIFICATION_FAILED, d.outcome());
    assertEquals(IdCardMatcher.REASON_BLANK_IMAGE, d.reason());
  }
  @Test void unrelatedProseFailsWithNotIdCardReason(){
    Decision d = new IdCardMatcher().matchTwoSided(
        side(PROSE_FRONT, 92.0, REAL_PHOTO), side(PROSE_BACK, 90.0, REAL_PHOTO), null, "59", SUBJECT);
    assertEquals(Outcome.VERIFICATION_FAILED, d.outcome());
    assertEquals(IdCardMatcher.REASON_NOT_ID_CARD, d.reason());
  }
  @Test void selfieFewWordsGoesToManualReview(){
    // Weak evidence — a few words, no markers — is never auto-failed.
    Decision d = new IdCardMatcher().matchTwoSided(
        side("Smiling face outdoors", 80.0, REAL_PHOTO),
        side("", null, OcrProvider.QualitySignals.unknown()), null, "59", SUBJECT);
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
    assertNotEquals(IdCardMatcher.REASON_NOT_ID_CARD, d.reason());
    assertNotEquals(IdCardMatcher.REASON_BLANK_IMAGE, d.reason());
  }
  @Test void validDuStyleCardPassesPlausibilityAndAutoVerifies(){
    Decision d = new IdCardMatcher().matchTwoSided(DU_FRONT, 92.0, DU_CARD_BACK, 90.0,
        "July'23 - June'27", "2022-23", DU_SUBJECT);
    assertEquals(Outcome.AUTO_VERIFIED, d.outcome(),
        "a valid card in a different university's layout must verify");
  }
  @Test void genuineOtherUniversityCardStillFailsViaExistingRule(){
    // A real card from another university carries card markers, so the
    // plausibility check must not claim it — the existing hard-fail
    // rule rejects it with its own reason.
    Decision d = twoSided(DU_FRONT, DU_CARD_BACK, "July'23 - June'27", "2022-23");
    assertEquals(Outcome.VERIFICATION_FAILED, d.outcome());
    assertEquals(IdCardMatcher.REASON_ID, d.reason(),
        "the existing rule's reason, not REASON_NOT_ID_CARD");
  }
  @Test void blurryButRealCardGoesToManualReview(){
    // A real card that reads poorly (low confidence) is weak evidence:
    // it waits for an admin and is never failed as blank or not-a-card,
    // even with realistic (non-blank) image signals on both sides.
    Decision d = new IdCardMatcher().matchTwoSided(
        side(FRONT, 30.0, REAL_PHOTO), side(BACK, 28.0, REAL_PHOTO), null, "59", SUBJECT);
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
    assertEquals(IdCardMatcher.REASON_UNCLEAR, d.reason());
  }
}
