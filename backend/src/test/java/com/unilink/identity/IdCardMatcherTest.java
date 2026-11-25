package com.unilink.identity;

import com.unilink.identity.service.IdCardMatcher;
import com.unilink.identity.service.IdCardMatcher.Decision;
import com.unilink.identity.service.IdCardMatcher.Outcome;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** OCR matching rules (D-180, owner brief 2026-11-25 §3): ID normalisation,
 *  name similarity, university / department / address matching, and the
 *  AUTO_VERIFIED / VERIFICATION_FAILED / MANUAL_REVIEW decision matrix. */
class IdCardMatcherTest {
  private static final IdCardMatcher.Subject SUBJECT = new IdCardMatcher.Subject(
      "Tanvir Mahmud", "232-115-310", "Metropolitan University",
      "Computer Science and Engineering — MU", "CSE", "House 12, Road 5, Dhanmondi, Dhaka");
  private static final String CARD = """
      METROPOLITAN UNIVERSITY
      STUDENT IDENTITY CARD
      Name: Tanvir Mahmud
      ID: 232-115-310
      Department: Computer Science and Engineering
      Address: Dhanmondi, Dhaka
      """;

  @Test void fullMatchAutoVerifies(){
    Decision d = new IdCardMatcher().match(CARD, 92.0, SUBJECT);
    assertEquals(Outcome.AUTO_VERIFIED, d.outcome());
    assertTrue(d.idMatched());
    assertEquals(1.0, d.nameSimilarity(), 0.001);
    assertTrue(d.universityMatched());
    assertTrue(d.compositeScore() > 0.9);
  }
  @Test void idSeparatorsAndBengaliDigitsNormalise(){
    String bengali = CARD.replace("232-115-310", "২৩২-১১৫-৩১০");
    assertTrue(new IdCardMatcher().match(bengali, 90.0, SUBJECT).idMatched());
    String spaced = CARD.replace("232-115-310", "232 115 310");
    assertEquals(Outcome.AUTO_VERIFIED, new IdCardMatcher().match(spaced, 90.0, SUBJECT).outcome());
  }
  @Test void longerIdDoesNotMatchByPrefix(){
    String other = CARD.replace("232-115-310", "2321153109");
    Decision d = new IdCardMatcher().match(other, 90.0, SUBJECT);
    assertFalse(d.idMatched());
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome(), "one digit off is a possible misread, not a proven mismatch");
  }
  @Test void idMustStayOnOneLine(){
    String split = CARD.replace("232-115-310", "232-115\n310");
    assertFalse(new IdCardMatcher().match(split, 90.0, SUBJECT).idMatched(),
      "digits split across lines must not merge into a match");
  }
  @Test void namePrefixesIgnoredAndFuzzyMatchPasses(){
    var prefix = new IdCardMatcher.Subject("Md. Tanvir Mahmud", "232-115-310", "Metropolitan University",
        "Computer Science and Engineering — MU", "CSE", "Dhanmondi, Dhaka");
    assertEquals(1.0, new IdCardMatcher().match(CARD, 90.0, prefix).nameSimilarity(), 0.001);
    String typo = CARD.replace("Tanvir Mahmud", "Tanvir Mahmood");
    Decision d = new IdCardMatcher().match(typo, 90.0, SUBJECT);
    assertTrue(d.nameSimilarity() >= 0.75, "one-letter OCR difference stays above the threshold");
    assertEquals(Outcome.AUTO_VERIFIED, d.outcome());
  }
  @Test void completelyDifferentNameFails(){
    String other = CARD.replace("Tanvir Mahmud", "Sakib Hasan").replace("232-115-310", "999-888-777");
    Decision d = new IdCardMatcher().match(other, 90.0, SUBJECT);
    assertEquals(Outcome.VERIFICATION_FAILED, d.outcome());
    assertEquals(IdCardMatcher.REASON_ID, d.reason(), "ID is the first failing field");
  }
  @Test void nameSimilarityAtThresholdGoesToReview(){
    // "Mahmud" vs "Ahmed" scores 0.667 and "Tanvir" vs "Karim" 0.333 —
    // the average lands exactly on the 0.50 hard-fail boundary, which is
    // borderline by design: a human reviews instead of a rejection.
    String other = CARD.replace("Tanvir Mahmud", "Karim Uddin Ahmed").replace("232-115-310", "999-888-777");
    Decision d = new IdCardMatcher().match(other, 90.0, SUBJECT);
    assertEquals(0.5, d.nameSimilarity(), 0.001);
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
  }
  @Test void universityAbbreviationMatches(){
    String abbr = CARD.replace("METROPOLITAN UNIVERSITY", "MU").replace("Metropolitan", "MU");
    Decision d = new IdCardMatcher().match(abbr, 90.0, SUBJECT);
    assertTrue(d.universityMatched());
    assertEquals(Outcome.AUTO_VERIFIED, d.outcome());
  }
  @Test void otherUniversityAbbreviationIsAConflict(){
    String other = CARD.replace("METROPOLITAN UNIVERSITY", "UNIVERSITY OF DHAKA")
        .replace("Metropolitan", "Dhaka");
    String withAbbr = other + "\nDU\n";
    Decision d = new IdCardMatcher().match(withAbbr, 90.0, SUBJECT);
    assertFalse(d.universityMatched());
    // ID + name match, only the university conflicts: borderline, human decides.
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
  }
  @Test void universityAbsentIsInconclusiveNotFailure(){
    String noUni = CARD.replace("METROPOLITAN UNIVERSITY\n", "").replace("Metropolitan", "Metro");
    Decision d = new IdCardMatcher().match(noUni, 90.0, SUBJECT);
    assertFalse(d.universityMatched());
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
    assertEquals(IdCardMatcher.REASON_UNIVERSITY, d.reason());
  }
  @Test void departmentAcronymMatchesAndMismatchIsReview(){
    String eee = CARD.replace("Computer Science and Engineering", "Electrical and Electronic Engineering (EEE)");
    Decision d = new IdCardMatcher().match(eee, 90.0, SUBJECT);
    assertEquals(IdCardMatcher.DepartmentResult.MISMATCH, d.department());
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
    assertEquals(IdCardMatcher.REASON_DEPARTMENT, d.reason());
    String noDept = CARD.replace("Department: Computer Science and Engineering\n", "");
    assertEquals(IdCardMatcher.DepartmentResult.NEUTRAL,
        new IdCardMatcher().match(noDept, 90.0, SUBJECT).department());
    assertEquals(Outcome.AUTO_VERIFIED, new IdCardMatcher().match(noDept, 90.0, SUBJECT).outcome(),
        "a card without department evidence still verifies on ID + name + university");
  }
  @Test void addressConflictDowngradesToReview(){
    String sylhet = CARD.replace("Dhanmondi, Dhaka", "Zindabazar, Sylhet");
    Decision d = new IdCardMatcher().match(sylhet, 90.0, SUBJECT);
    assertEquals(Outcome.MANUAL_REVIEW, d.outcome());
    assertEquals(IdCardMatcher.REASON_ADDRESS, d.reason());
  }
  @Test void emptyTextAndLowConfidenceAreUnclear(){
    Decision empty = new IdCardMatcher().match("  \n ", 90.0, SUBJECT);
    assertEquals(Outcome.MANUAL_REVIEW, empty.outcome());
    assertEquals(IdCardMatcher.REASON_UNCLEAR, empty.reason());
    Decision lowConf = new IdCardMatcher().match(CARD, 30.0, SUBJECT);
    assertEquals(Outcome.MANUAL_REVIEW, lowConf.outcome());
    assertEquals(IdCardMatcher.REASON_UNCLEAR, lowConf.reason());
    Decision midConf = new IdCardMatcher().match(CARD, 60.0, SUBJECT);
    assertEquals(Outcome.AUTO_VERIFIED, midConf.outcome(), "confidence 60 clears the auto-verify bar");
  }
  @Test void wrongCardWithConfidentTextFails(){
    String wrong = """
        UNIVERSITY OF DHAKA
        STUDENT IDENTITY CARD
        Name: Karim Uddin Ahmed
        ID: 999-888-777
        Department: Physics
        DU
        """;
    Decision d = new IdCardMatcher().match(wrong, 92.0, SUBJECT);
    assertEquals(Outcome.VERIFICATION_FAILED, d.outcome());
  }
}
