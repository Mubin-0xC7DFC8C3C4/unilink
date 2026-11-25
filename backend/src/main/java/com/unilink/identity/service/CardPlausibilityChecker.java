package com.unilink.identity.service;
import com.unilink.integration.ocr.OcrProvider.QualitySignals;
import java.util.Set;
/** Content-based plausibility of an uploaded ID card (D-197). Pure logic
 *  — no I/O, no Spring — so it is unit-testable without Tesseract.
 *  It answers one question the byte checks (magic bytes, 5 MB, same-image
 *  equality) cannot: does this look like an ID card at all? A random
 *  photo, landscape or selfie used to land in MANUAL_REVIEW and wait for
 *  an admin; clearly unrelated uploads are now VERIFICATION_FAILED with
 *  an actionable reason, and the student can resubmit.
 *
 *  The check is deliberately conservative and two-tiered:
 *  (1) image quality — a near-uniform decoded image (solid white /
 *  solid colour) on either side, or an undecodable image that also
 *  yielded no readable text (corrupt file), is blank/corrupt evidence;
 *  (2) OCR-text structure plus a university-agnostic card-marker lexicon
 *  over the front+back text — substantial text (≥ 40 chars) with zero
 *  card markers and zero field matches is clearly not an ID card.
 *  Everything weak or ambiguous stays MANUAL_REVIEW: the check never
 *  auto-fails on uncertainty (a blurry-but-real card, a selfie with a
 *  few words, a card the lexicon does not recognise). No university
 *  name, city or single-university layout is hardcoded, and no layout
 *  template is assumed.
 *
 *  Ordering inside {@link IdCardMatcher#matchTwoSided}: the quality rule
 *  runs before field matching (it needs no field facts); the marker rule
 *  needs the front decision's field matches, so the matcher applies it
 *  to that decision — it can only turn a FAILED / MANUAL_REVIEW into
 *  {@link IdCardMatcher#REASON_NOT_ID_CARD}, never override an
 *  AUTO_VERIFIED (which by definition has field matches). The existing
 *  hard-fail rule is untouched: a genuine card from another university
 *  still fails on its own rule, because it carries card markers. */
public class CardPlausibilityChecker {

  /** A near-uniform image: pixel stdDev below this AND more than this
   *  share of pixels near the modal grey value. Both must hold — a
   *  real card photo has a white background (high near-mode share) but
   *  far too much contrast for the stdDev bar, and a dark photo has a
   *  low stdDev but no dominant grey value. */
  static final double BLANK_MAX_STDDEV = 8.0;
  static final double BLANK_MIN_UNIFORM_SHARE = 0.95;
  /** Substantial OCR text: long enough that "no card markers and no
   *  field matches" is evidence, not an unreadable image. Mirrors the
   *  matcher's SUBSTANTIAL_OCR_TEXT. */
  static final int SUBSTANTIAL_TEXT = 40;
  /** A digit run of this length is card-like evidence (student IDs,
   *  phone numbers, years). Only structure evidence — never a gate. */
  static final int DIGIT_RUN = 3;

  /** University-agnostic words printed on a student ID card. Matched on
   *  whole tokens, so "card" never matches inside another word and the
   *  generic word "id" only counts when it appears as its own token
   *  ("ID: 232-115-310"). At least {@link #MIN_MARKERS} distinct
   *  markers are required for plausibility. */
  static final Set<String> CARD_MARKERS = Set.of(
      "student", "identity", "card", "id", "name",
      "university", "institute", "college",
      "department", "programme", "program",
      "batch", "session", "validity", "address");
  static final int MIN_MARKERS = 2;

  /** OCR-text structure, derived from the TSV-reconstructed text (the
   *  adapter's parseTsv preserves line breaks and word boundaries):
   *  word count, distinct non-blank line count, whether any digit run
   *  of {@link #DIGIT_RUN}+ appears, and the stripped character count. */
  public record TextStructure(int wordCount, int lineCount, boolean hasDigitRun, int charCount) {}
  public static TextStructure structure(String text){
    String t = text == null ? "" : text;
    int words = t.isBlank() ? 0 : t.trim().split("\\s+").length;
    int lines = 0;
    for (String line : t.split("\\R")) if (!line.isBlank()) lines++;
    boolean digitRun = java.util.regex.Pattern.compile("\\d{" + DIGIT_RUN + ",}").matcher(t).find();
    return new TextStructure(words, lines, digitRun, t.replaceAll("\\s+", "").length());
  }
  /** Distinct card-marker lexicon entries present in the text (whole
   *  tokens, lower-cased). Visible for testing. */
  public static int countDistinctMarkers(String text){
    Set<String> found = new java.util.HashSet<>();
    for (String token : IdCardMatcher.tokens(text)) if (CARD_MARKERS.contains(token)) found.add(token);
    return found.size();
  }

  /** The quality rule for one side: true when the image is blank or
   *  corrupt evidence. A measured near-uniform image is blank on its
   *  own; an undecodable image is blank only when it also yielded no
   *  readable text — ImageIO cannot decode WebP, which Tesseract reads
   *  fine, so undecodable alone must never fail a genuine WebP card.
   *  A null or unknown (no-signal) quality is neutral, never blank. */
  public boolean isBlankImage(QualitySignals quality, String text){
    if (quality == null || !quality.hasSignal()) return false;
    if (quality.decodable()
        && quality.pixelStdDev() < BLANK_MAX_STDDEV
        && quality.nearUniformShare() > BLANK_MIN_UNIFORM_SHARE) return true;
    if (!quality.decodable() && structure(text).charCount() < 10) return true;
    return false;
  }

  /** The marker rule: substantial front+back OCR text with zero card
   *  markers and zero field matches (no student-ID match, name
   *  similarity below the matcher's hard-fail threshold, no university
   *  match) is clearly not an ID card. Short text is weak evidence, so
   *  a selfie with a few words never fires this rule; text carrying
   *  even one marker, or matching even one field, never fires it either. */
  public boolean isClearlyNotIdCard(String frontText, String backText,
                                    boolean idMatched, double nameSimilarity, boolean universityMatched){
    String combined = (frontText == null ? "" : frontText) + "\n" + (backText == null ? "" : backText);
    if (structure(combined).charCount() < SUBSTANTIAL_TEXT) return false;
    if (countDistinctMarkers(combined) > 0) return false;
    boolean zeroFieldMatches = !idMatched && nameSimilarity < IdCardMatcher.NAME_HARD_FAIL && !universityMatched;
    return zeroFieldMatches;
  }
}
