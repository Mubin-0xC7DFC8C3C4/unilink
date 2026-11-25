package com.unilink.identity;

import com.unilink.identity.service.IdCardMatcher;
import com.unilink.identity.service.IdCardMatcher.Decision;
import com.unilink.identity.service.IdCardMatcher.Outcome;
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
}
