package com.unilink.identity.service;
import org.springframework.stereotype.Component;
import java.util.*;

/** Automated ID-card matching (D-180, owner brief 2026-11-25 §3). Pure
 *  logic — no I/O, no Spring — so it is unit-testable without Tesseract.
 *  The matcher is deliberately conservative: AUTO_VERIFIED needs a strong
 *  student-ID match, name similarity ≥ 0.75 and a university match, and
 *  VERIFICATION_FAILED needs corroboration (at least two hard failures on
 *  substantial OCR text). Everything borderline goes to MANUAL_REVIEW —
 *  an uncertain card is never falsely verified and never falsely rejected. */
@Component
public class IdCardMatcher {

  public record Subject(String fullName, String studentId, String universityName,
                        String departmentName, String departmentShortName, String address) {}
  public enum Outcome { AUTO_VERIFIED, VERIFICATION_FAILED, MANUAL_REVIEW }
  public enum DepartmentResult { MATCHED, NEUTRAL, MISMATCH }
  public record Decision(Outcome outcome, String reason, double compositeScore,
                         boolean idMatched, double nameSimilarity, boolean universityMatched,
                         DepartmentResult department) {}

  public static final String REASON_ID = "Student ID number could not be matched with the uploaded ID card.";
  public static final String REASON_NAME = "Your name could not be reliably matched. Please upload a clearer image.";
  public static final String REASON_UNIVERSITY = "University name does not match the uploaded ID card.";
  public static final String REASON_DEPARTMENT = "Department information could not be verified.";
  public static final String REASON_ADDRESS = "University address could not be matched.";
  public static final String REASON_UNCLEAR = "The ID card image is unclear. Please upload a sharper image.";
  public static final String REASON_INCONCLUSIVE = "We could not verify your ID card automatically. An administrator will review your application.";

  private static final double NAME_AUTO_THRESHOLD = 0.75;
  private static final double NAME_HARD_FAIL = 0.50;
  private static final double MIN_CONFIDENCE = 50.0;
  private static final double UNCLEAR_CONFIDENCE = 35.0;
  private static final int MIN_OCR_TEXT = 10;
  private static final int SUBSTANTIAL_OCR_TEXT = 40;

  private static final Set<String> NAME_PREFIXES = Set.of("md", "mohammad", "mohammed", "mst", "mrs", "mr", "ms", "dr");
  private static final Set<String> UNIVERSITY_STOPWORDS =
      Set.of("university", "of", "the", "and", "institute", "college", "school", "united", "national", "bangladesh");
  /** Distinctive university token → its abbreviations. Only used when the
   *  token itself is absent, so a card that prints "DU" still matches the
   *  University of Dhaka. City names are deliberately NOT conflict tokens
   *  on their own — "Dhaka" is also an address. */
  private static final Map<String, Set<String>> UNIVERSITY_ABBREVIATIONS = Map.ofEntries(
      Map.entry("metropolitan", Set.of("mu")),
      Map.entry("dhaka", Set.of("du")),
      Map.entry("rajshahi", Set.of("ru", "ruet")),
      Map.entry("chittagong", Set.of("cu", "cuet")),
      Map.entry("chattogram", Set.of("cu", "cuet")),
      Map.entry("khulna", Set.of("kuet")),
      Map.entry("sylhet", Set.of("sust")),
      Map.entry("jahangirnagar", Set.of("ju")),
      Map.entry("daffodil", Set.of("diu")),
      Map.entry("brac", Set.of("brac")),
      Map.entry("north", Set.of("nsu")),
      Map.entry("american", Set.of("aiub")),
      Map.entry("east", Set.of("ewu")),
      Map.entry("independent", Set.of("iub")),
      Map.entry("military", Set.of("mist")),
      Map.entry("liberal", Set.of("ulab")),
      Map.entry("engineering", Set.of("buet", "kuet", "ruet", "cuet")),
      Map.entry("technology", Set.of("buet", "kuet", "ruet", "cuet")));
  /** Abbreviation → university key, for conflict detection: a card that
   *  prints another university's abbreviation while the registered
   *  university's tokens are absent is a different university's card. */
  private static final Map<String, String> ABBREVIATION_TO_UNIVERSITY = new HashMap<>();
  static {
    UNIVERSITY_ABBREVIATIONS.forEach((token, abbrs) -> abbrs.forEach(a -> ABBREVIATION_TO_UNIVERSITY.put(a, token)));
    ABBREVIATION_TO_UNIVERSITY.put("buet", "buet");
  }
  /** Department acronym → significant words of its expanded name. */
  private static final Map<String, Set<String>> DEPARTMENT_ALIASES = Map.ofEntries(
      Map.entry("cse", Set.of("computer", "science")),
      Map.entry("eee", Set.of("electrical", "electronic")),
      Map.entry("ece", Set.of("electronics", "communication")),
      Map.entry("ce", Set.of("civil")),
      Map.entry("me", Set.of("mechanical")),
      Map.entry("ipe", Set.of("industrial", "production")),
      Map.entry("bba", Set.of("business", "administration")),
      Map.entry("mba", Set.of("business", "administration")),
      Map.entry("llb", Set.of("law")),
      Map.entry("english", Set.of("english")),
      Map.entry("economics", Set.of("economics")),
      Map.entry("physics", Set.of("physics")),
      Map.entry("chemistry", Set.of("chemistry")),
      Map.entry("mathematics", Set.of("mathematics")),
      Map.entry("pharmacy", Set.of("pharmacy")),
      Map.entry("accounting", Set.of("accounting")),
      Map.entry("finance", Set.of("finance")),
      Map.entry("marketing", Set.of("marketing")),
      Map.entry("management", Set.of("management")));
  private static final Set<String> DEPARTMENT_STOPWORDS =
      Set.of("and", "of", "the", "engineering", "technology", "department", "studies", "programme", "program", "bachelor", "bsc", "honours", "honors");
  /** City / area tokens used only to detect an address conflict (both sides
   *  name different places). Absence of any address evidence is neutral. */
  private static final Set<String> ADDRESS_GAZETTEER = Set.of(
      "dhaka", "chattogram", "chittagong", "sylhet", "khulna", "rajshahi", "barishal", "barisal",
      "cumilla", "comilla", "mymensingh", "rangpur", "narayanganj", "gazipur", "uttara", "mirpur",
      "dhanmondi", "bashundhara", "tejgaon", "mohammadpur", "banani", "gulshan", "farmgate", "savar",
      "jashore", "dinajpur", "bogura", "bogra", "tangail", "faridpur", "noakhali", "feni", "chandpur", "cox");

  private static final char[] BENGALI_DIGITS = {'০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'};

  /** Bengali digits → Latin digits, lower-cased. */
  static String latinDigits(String s){
    if (s == null) return "";
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++){
      char c = s.charAt(i);
      int d = -1;
      for (int j = 0; j < BENGALI_DIGITS.length; j++) if (c == BENGALI_DIGITS[j]){ d = j; break; }
      out.append(d >= 0 ? (char) ('0' + d) : Character.toLowerCase(c));
    }
    return out.toString();
  }
  /** Words of the text: lower-cased, Bengali digits converted, split on
   *  anything that is not a letter or digit. */
  static List<String> tokens(String text){
    if (text == null) return List.of();
    String[] parts = latinDigits(text).split("[^a-z0-9]+");
    List<String> out = new ArrayList<>();
    for (String p : parts) if (!p.isBlank()) out.add(p);
    return out;
  }
  /** Student ID with spaces, hyphens and other separators removed. */
  static String normalizeId(String id){
    if (id == null) return "";
    return latinDigits(id).replaceAll("[^a-z0-9]", "");
  }
  /** Levenshtein similarity in [0,1]: 1 is identical. */
  static double similarity(String a, String b){
    if (a == null || b == null) return 0.0;
    if (a.equals(b)) return 1.0;
    int n = a.length(), m = b.length();
    if (n == 0 || m == 0) return 0.0;
    int[] prev = new int[m + 1], cur = new int[m + 1];
    for (int j = 0; j <= m; j++) prev[j] = j;
    for (int i = 1; i <= n; i++){
      cur[0] = i;
      for (int j = 1; j <= m; j++)
        cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
      int[] tmp = prev; prev = cur; cur = tmp;
    }
    return 1.0 - (double) prev[m] / Math.max(n, m);
  }

  public Decision match(String ocrText, Double confidence, Subject subject){
    String text = ocrText == null ? "" : ocrText;
    String stripped = text.replaceAll("\\s+", "");
    if (stripped.length() < MIN_OCR_TEXT)
      return new Decision(Outcome.MANUAL_REVIEW, REASON_UNCLEAR, 0.0, false, 0.0, false, DepartmentResult.NEUTRAL);
    if (confidence != null && confidence < UNCLEAR_CONFIDENCE)
      return new Decision(Outcome.MANUAL_REVIEW, REASON_UNCLEAR, 0.0, false, 0.0, false, DepartmentResult.NEUTRAL);

    List<String> ocrTokens = tokens(text);
    Set<String> ocrSet = new HashSet<>(ocrTokens);

    boolean idMatched = matchId(text, subject.studentId());
    double nameSim = matchName(ocrTokens, subject.fullName());
    boolean uniMatched = matchUniversity(ocrSet, subject.universityName());
    boolean uniConflict = universityConflict(ocrSet, subject.universityName());
    DepartmentResult dept = matchDepartment(ocrSet, subject.departmentName(), subject.departmentShortName());
    boolean addressConflict = addressConflict(ocrSet, subject.address());
    boolean confidenceOk = confidence == null || confidence >= MIN_CONFIDENCE;

    double composite = (idMatched ? 0.4 : 0.0) + 0.3 * nameSim + (uniMatched ? 0.2 : 0.0)
        + (dept == DepartmentResult.MATCHED ? 0.1 : dept == DepartmentResult.NEUTRAL ? 0.05 : 0.0);

    if (idMatched && nameSim >= NAME_AUTO_THRESHOLD && uniMatched && dept != DepartmentResult.MISMATCH
        && !addressConflict && confidenceOk)
      return new Decision(Outcome.AUTO_VERIFIED, null, composite, true, nameSim, true, dept);

    boolean substantial = stripped.length() >= SUBSTANTIAL_OCR_TEXT;
    boolean idFail = !idMatched, nameFail = nameSim < NAME_HARD_FAIL, uniFail = uniConflict;
    int hardFails = (idFail ? 1 : 0) + (nameFail ? 1 : 0) + (uniFail ? 1 : 0);
    if (substantial && hardFails >= 2){
      String reason = idFail ? REASON_ID : nameFail ? REASON_NAME : REASON_UNIVERSITY;
      return new Decision(Outcome.VERIFICATION_FAILED, reason, composite, idMatched, nameSim, uniMatched, dept);
    }
    String reason;
    if (!idMatched) reason = REASON_ID;
    else if (nameSim < NAME_AUTO_THRESHOLD) reason = REASON_NAME;
    else if (!uniMatched) reason = REASON_UNIVERSITY;
    else if (dept == DepartmentResult.MISMATCH) reason = REASON_DEPARTMENT;
    else if (addressConflict) reason = REASON_ADDRESS;
    else reason = REASON_INCONCLUSIVE;
    return new Decision(Outcome.MANUAL_REVIEW, reason, composite, idMatched, nameSim, uniMatched, dept);
  }

  /** The normalised ID must appear on a single OCR line: stripping
   *  separators from the whole text could merge digits from unrelated
   *  fields into a false match. A longer ID that merely contains the
   *  registered ID as a prefix (extra digit) does not match — the
   *  characters around the match must not be digits. */
  static boolean matchId(String ocrText, String studentId){
    String id = normalizeId(studentId);
    if (id.length() < 5) return false;
    for (String line : ocrText.split("\\R")){
      String norm = normalizeId(line);
      int at = norm.indexOf(id);
      while (at >= 0){
        boolean beforeOk = at == 0 || !Character.isDigit(norm.charAt(at - 1));
        int end = at + id.length();
        boolean afterOk = end >= norm.length() || !Character.isDigit(norm.charAt(end));
        if (beforeOk && afterOk) return true;
        at = norm.indexOf(id, at + 1);
      }
    }
    return false;
  }

  /** Average, over the registration name's tokens, of the best similarity
   *  against any OCR token. Common prefixes (Md., Mohammad, Mst.) are
   *  ignored on the registration side. */
  static double matchName(List<String> ocrTokens, String fullName){
    List<String> nameTokens = tokens(fullName).stream()
        .filter(t -> t.length() >= 2 && !NAME_PREFIXES.contains(t)).toList();
    if (nameTokens.isEmpty() || ocrTokens.isEmpty()) return 0.0;
    double sum = 0.0;
    for (String nt : nameTokens){
      double best = 0.0;
      for (String ot : ocrTokens) best = Math.max(best, similarity(nt, ot));
      sum += best;
    }
    return sum / nameTokens.size();
  }

  /** Distinctive tokens of the university name (stopwords excluded), or a
   *  known abbreviation of one of those tokens. A university with two or
   *  more distinctive tokens needs two of them (or one abbreviation) —
   *  "engineering" alone must not match BUET when the card is from
   *  another engineering university. */
  static boolean matchUniversity(Set<String> ocrTokens, String universityName){
    List<String> tokens = distinctiveUniversityTokens(universityName);
    if (tokens.isEmpty()) return false;
    for (String t : tokens)
      for (String abbr : UNIVERSITY_ABBREVIATIONS.getOrDefault(t, Set.of()))
        if (ocrTokens.contains(abbr)) return true;
    long hits = tokens.stream().filter(ocrTokens::contains).count();
    return tokens.size() == 1 ? hits >= 1 : hits >= 2;
  }
  static List<String> distinctiveUniversityTokens(String universityName){
    return tokens(universityName).stream()
        .filter(t -> t.length() >= 3 && !UNIVERSITY_STOPWORDS.contains(t)).toList();
  }
  /** Another university's abbreviation is printed while none of the
   *  registered university's tokens or abbreviations appear. */
  static boolean universityConflict(Set<String> ocrTokens, String universityName){
    List<String> mine = distinctiveUniversityTokens(universityName);
    if (matchUniversity(ocrTokens, universityName)) return false;
    Set<String> myAbbrs = new HashSet<>();
    for (String t : mine) myAbbrs.addAll(UNIVERSITY_ABBREVIATIONS.getOrDefault(t, Set.of()));
    for (String token : ocrTokens){
      String other = ABBREVIATION_TO_UNIVERSITY.get(token);
      if (other != null && !mine.contains(other) && !myAbbrs.contains(token)) return true;
    }
    return false;
  }

  /** Department name as stored ("Computer Science and Engineering — MU"):
   *  the " — <university>" suffix is display-only and is stripped. */
  static DepartmentResult matchDepartment(Set<String> ocrTokens, String departmentName, String departmentShortName){
    String base = departmentName == null ? "" : departmentName;
    int cut = base.indexOf(" — ");
    if (cut >= 0) base = base.substring(0, cut);
    String acronym = (departmentShortName == null ? "" : departmentShortName.trim().toLowerCase());
    Set<String> expanded = new HashSet<>();
    if (!acronym.isBlank() && DEPARTMENT_ALIASES.containsKey(acronym)) expanded.addAll(DEPARTMENT_ALIASES.get(acronym));
    for (String t : tokens(base)) if (t.length() >= 3 && !DEPARTMENT_STOPWORDS.contains(t)) expanded.add(t);
    if (!acronym.isBlank() && ocrTokens.contains(acronym)) return DepartmentResult.MATCHED;
    if (!expanded.isEmpty()){
      long hits = expanded.stream().filter(ocrTokens::contains).count();
      if (hits >= 2 || (expanded.size() == 1 && hits == 1)) return DepartmentResult.MATCHED;
    }
    // A different known department is clearly present: its acronym, or
    // at least two of its expanded words, with no match for ours. Only
    // when the registered department is actually known — an unknown
    // department cannot mismatch.
    if (!acronym.isBlank() || !expanded.isEmpty())
      for (Map.Entry<String, Set<String>> e : DEPARTMENT_ALIASES.entrySet()){
        if (e.getKey().equals(acronym)) continue;
        if (ocrTokens.contains(e.getKey())) return DepartmentResult.MISMATCH;
        long hits = e.getValue().stream().filter(ocrTokens::contains).count();
        if (hits >= 2) return DepartmentResult.MISMATCH;
      }
    return DepartmentResult.NEUTRAL;
  }

  /** Both sides name a place from the gazetteer, and the places differ. */
  static boolean addressConflict(Set<String> ocrTokens, String address){
    Set<String> regPlaces = new HashSet<>(tokens(address));
    regPlaces.retainAll(ADDRESS_GAZETTEER);
    Set<String> ocrPlaces = new HashSet<>(ocrTokens);
    ocrPlaces.retainAll(ADDRESS_GAZETTEER);
    if (regPlaces.isEmpty() || ocrPlaces.isEmpty()) return false;
    return Collections.disjoint(regPlaces, ocrPlaces);
  }
}
