package com.unilink.integration.ocr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** I-12 (FR-AUTH-06): the OCR scorer compares the university name as well
 *  as the student's name and student ID. */
class OcrUniversityScoreTest {
  // Text as Tesseract might return it from a Dhaka University ID card.
  private static final String CARD = "University of Dhaka\nStudent: Rahim Uddin\nID: CSE-2021-047\nDepartment of Computer Science";

  @Test void universityNameCountsTowardScore() {
    // Name (2 tokens) + student id (1) + university distinctive token "dhaka" (1) = 4/4.
    assertEquals(1.0, TesseractOcrAdapter.score(CARD, "Rahim Uddin", "CSE-2021-047", "University of Dhaka"));
    // Without the university name the same card scores 3/3 — also 1.0 here,
    // so use a card missing the name to see the difference below.
  }

  @Test void missingUniversityTokenLowersScore() {
    // Card has name + id but no "dhaka": 3 hits / 4 total (dhaka missing).
    String noUni = "Student: Rahim Uddin\nID: CSE-2021-047\nDepartment of Computer Science";
    assertEquals(0.75, TesseractOcrAdapter.score(noUni, "Rahim Uddin", "CSE-2021-047", "University of Dhaka"));
    // And the old 3-arg score on the same text is 1.0 — the university
    // component is what catches the mismatch.
    assertEquals(1.0, TesseractOcrAdapter.score(noUni, "Rahim Uddin", "CSE-2021-047"));
  }

  @Test void genericUniversityWordsDoNotCount() {
    // A card that merely says "University" must not earn university points:
    // "university" is a stopword. When the claimed name yields no
    // distinctive token at all, the component is skipped entirely and the
    // score is the name+id score (3/3), not diluted.
    String generic = "University\nStudent: Rahim Uddin\nID: CSE-2021-047";
    assertEquals(1.0, TesseractOcrAdapter.score(generic, "Rahim Uddin", "CSE-2021-047", "University"));
    // Claimed "University of Dhaka": "dhaka" is the distinctive token and
    // this card does not contain it, so it scores 3/4 — the generic word
    // contributes nothing either way; the miss is the distinctive token.
    assertEquals(0.75, TesseractOcrAdapter.score(generic, "Rahim Uddin", "CSE-2021-047", "University of Dhaka"));
    // A card that does say Dhaka matches fully.
    assertEquals(1.0, TesseractOcrAdapter.score("University of Dhaka\nStudent: Rahim Uddin\nID: CSE-2021-047", "Rahim Uddin", "CSE-2021-047", "University of Dhaka"));
  }

  @Test void wrongUniversityIsCaught() {
    // Card is a Metropolitan University card; claimed university is Dhaka.
    String other = "Metropolitan University\nStudent: Rahim Uddin\nID: CSE-2021-047";
    // Name 2/2 + id 1/1 + university 0/1 ("dhaka" missing; "metropolitan"
    // is not a token of "University of Dhaka") = 3/4.
    assertEquals(0.75, TesseractOcrAdapter.score(other, "Rahim Uddin", "CSE-2021-047", "University of Dhaka"));
    // But the same card matches its own university fully.
    assertEquals(1.0, TesseractOcrAdapter.score(other, "Rahim Uddin", "CSE-2021-047", "Metropolitan University"));
  }

  @Test void nullUniversityKeepsOldBehaviour() {
    assertEquals(1.0, TesseractOcrAdapter.score(CARD, "Rahim Uddin", "CSE-2021-047", null));
    assertEquals(0.0, TesseractOcrAdapter.score("", "Rahim Uddin", "CSE-2021-047", "University of Dhaka"));
    assertEquals(0.0, TesseractOcrAdapter.score(null, "Rahim Uddin", "CSE-2021-047", "University of Dhaka"));
  }
}
