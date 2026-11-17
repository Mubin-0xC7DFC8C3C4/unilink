# Phase 1 Addendum — SRS and pic.docx Verification

**Date:** 2026-11-17
**Applies to:** Phase 1 report `01.md` (2026-11-17)
**Sources received after Phase 1:** `UniLink_SRS__1.docx` (1.3 MB, Microsoft Word 2007+) and `pic.docx` (195 KB, image-only, 2 images). Both copied unmodified to `docs/requirements/source/UniLink_SRS__1.docx` and `docs/design/source/pic.docx`. Extracted images are under `docs/requirements/source/srs/` (9 SRS diagrams) and `docs/design/source/pic/` (`image1.png`, `image2.png`).
**Method:** `python-docx` extraction of all 211 paragraphs and 29 tables in the SRS; scripted comparison of every requirement ID and the start of every requirement text against `SRS_TRACEABILITY_TEMPLATE.md`; visual inspection of both `pic.docx` images. No application code was changed by this addendum. The build coordinator (spawned 2026-11-17 under the Master Execution Prompt) was sent these findings separately; D-072 to D-075 below are reserved for this addendum — the coordinator's new decisions must start at D-076.

---

## 1. Result in one line

**The template is verified against the SRS: 116 / 116 requirement IDs match exactly, with no missing, extra, or mis-transcribed IDs, and no wording mismatch in the template's abridged text. All 31 test cases TC-01 to TC-31 match. The design tokens in `DESIGN.md` are consistent with the two `pic.docx` screenshots, and the gaps recorded in D-040 are confirmed real.**

D-064 is therefore closed. D-071 is partially closed: the SRS text confirms the log's readings on the points checked in §3, but the genuinely ambiguous items (phone uniqueness, unverified "public information", visibility wording, contribution points) remain OPEN / PROPOSED as recorded in §4.

---

## 2. Requirements verification

| Check | Result |
|---|---|
| SRS requirement rows extracted | 116 |
| Template requirement rows | 116 |
| Unique IDs in SRS | 116 — FR-ADM 14, FR-AUTH 13, FR-EVT 9, FR-PROF 9, FR-MSG 8, FR-COM 7, NFR-SEC 7, FR-MOD 6, NFR-USA 6, FR-REP 4, FR-TEAM 4, NFR-PER 4, NFR-REL 4, FR-CNT 3, NFR-FLT 3, NFR-PRV 3, FR-SEC 2, FR-SRC 2, FR-BMK 1, FR-CAL 1, FR-NOT 1, NFR-AVL 1, NFR-CMP 1, NFR-LOC 1, NFR-MNT 1, NFR-SCL 1 |
| In SRS but not in template | None |
| In template but not in SRS | None |
| Wording mismatches (template abridged text vs SRS full text, first 30 characters) | None |
| SRS test-case rows extracted | 31 — TC-01 to TC-31, no gaps |
| TC ordering in SRS §5.3 | TC-01–TC-19, then TC-23–TC-31, then TC-20–TC-22 — confirms D-061. TC-14 and TC-15 expected results end with a stray semicolon and no further result — confirms D-061. |

### 2.1 Wording confirmed verbatim (spot-checked in full, script-checked for all 116)

- **FR-AUTH-01:** sign-up collects full name, phone, email, university, department, batch, address, student ID number, student ID card image **and profile photo**. The template's abridgement truncates after "ba…" — the full SRS list is the authority; the build must collect address and profile photo at sign-up, not only the fields in the template's truncated text.
- **FR-AUTH-02:** unique email **and unique student ID per university** — confirmed. No phone-uniqueness requirement in the SRS (see §4, D-067).
- **FR-AUTH-04:** OTP to "the student's phone **or** email", 5-minute expiry, max 5 attempts — confirmed. The "or" is the source of the D-011 ambiguity; the SRS does not say the OTP is phone-only.
- **FR-AUTH-07:** Pending Review after OTP **and** email verification; Verified only after admin / moderator approval — confirms D-010.
- **FR-AUTH-08:** resubmit up to 3 times in 24 hours — confirms D-012.
- **FR-COM-01 / FR-COM-05:** wording is garbled exactly as D-063 recorded ("in a spaces derived from the profile"; "Feed shall show all visibility events, posts and discussions") — no new wording was found in the SRS to resolve them. D-021 remains the interpretation.
- **FR-REP-02:** 5 different verified students → auto-hide, notify owner, add to admin moderation queue with high priority — confirms D-034 / D-069 threshold and scope.
- **FR-REP-04:** auto-hidden 3 times within 30 days → flagged for admin review, may be restricted from posting — confirms D-034.
- **FR-SEC-01:** sectors "including Software Engineering, Data Science, AI/ML, Cyber Security, Web Development, Competitive Programming **etc.**" — the list is open-ended ("etc."), not a closed list. This answers Phase 1 Q7 in part: seed the six named sectors; the Platform Admin manages the full list via FR-ADM-10.
- **FR-ADM-01:** separate Admin Portal login, email + password + mandatory two-factor (OTP) — the trailing semicolon in the SRS is a punctuation defect, not a missing clause.
- **FR-ADM-08:** appeals against hiding **or suspension** — confirms D-019: a suspended user must be able to appeal, which is why the appeal link (no session) is required.
- **FR-MOD-02 / FR-MOD-05:** moderator sees only own university; suspension is recommend-only, Platform Admin confirms — confirms D-032 / D-036.
- **NFR-USA-06 before NFR-USA-05, NFR-SEC-07 before NFR-SEC-06** — confirmed in the SRS tables, exactly as D-060 recorded. IDs are kept as written.
- **FR-EVT-07** in the SRS is "The sidebar shall list Upcoming Events sorted by nearest deadline" (the template's "FR-EVT-0 then 7" split-run note in D-060 is a .docx formatting artefact; the canonical ID is FR-EVT-07).

### 2.2 Technology conflicts — confirmed in the SRS text

- **§1.4:** back-end Java 17+ Spring Boot (MVC, REST), Spring WebSockets STOMP / SockJS, Spring @Scheduled / Async, MySQL / MariaDB, SMTP + SMS + OCR, Admin Portal Spring Security 2FA. Front-end HTML5 / CSS3 / JavaScript / Bootstrap, optional Alpine.js / Vue.
- **§2.4 Operating Environment:** "Framework: Laravel" — a single line, contradicting §1.4 and the Definitions table (Spring Boot, STOMP, SockJS). D-001 is confirmed: Spring Boot is the SRS's substantive choice; Laravel is a documentation error in §2.4.
- **§1.5 Definitions:** "Spring Boot … framework used to create microservices" — a definition only; it does not require microservices. D-005 is confirmed.

### 2.3 Scope — confirmed in SRS §1.2

- In scope includes "Verification badge, **optional** contribution points". No FR defines contribution points. D-029b is confirmed: contribution points are **not built** in v1.0; only the verification badge is.
- Out of scope includes: native mobile app for the Admin Portal; native Android / iOS apps; biometric / face-matching verification; online payments, ticketing or event registration inside UniLink; video / voice calls; ERP / result-system integration; job portal and recruiter accounts; faculty, staff and alumni accounts; monetization and advertising; legal dispute handling and law-enforcement liaison.
- SRS §5.6 Risks confirms: "email domain check" as a mitigation (D-018 — a mitigation, not an FR, so hint-only is the correct reading); "deleting ID images after verification" (D-053 — the SRS states deletion, the log's 7 / 30-day retention is the operational interpretation, see §4); "Alternative OTP channel and retry" for gateway outage (D-011 / NFR-FLT-02).
- SRS §2.5 Constraints confirms: admin accounts cannot self-register, created only by a Platform Admin; academic identity fields can be changed only by repeating verification (D-017); OCR accuracy depends on photo quality.
- SRS §2.7 confirms unverified students "can complete verification, edit own profile and **read public information**; cannot post, comment or message" — "public information" is undefined in the SRS, so D-016 (read-only National feed) remains a PROPOSED interpretation, not an SRS-verified fact.
- SRS §4.1 / §4.2 contain only figure placeholders ("Fig 4.1: Use Case Diagram", etc.) and 9 embedded images; the ER diagram is an image, not extractable text. D-062 (ER gaps) cannot be closed from text extraction alone — the Architecture Overview §5 table list remains the build baseline, as D-062 already states. The 9 SRS images are preserved under `docs/requirements/source/srs/` for the coordinator to inspect visually if needed.

### 2.4 No new requirements found

No requirement, test case, or scope item was found in the SRS that is absent from the template, the decision log, or the master guide. No requirement in the template was found to be invented. The Phase 1 "verified requirements summary" is now verified, subject only to §4.

---

## 3. Design verification — pic.docx

`pic.docx` contains no extractable text; it is two screenshots, viewed directly.

### Image 1 — dark "Academic Overview" (1842 × 860)

- Header: "UniConnect / Metropolitan University" logo tile, Home / Results / Routine, search field, theme toggle, notification bell with count 3, avatar "MU". **This is the old UniConnect branding — it must NOT be copied. The product name is UniLink.**
- Eyebrow "SPRING 2026 · METROPOLITAN UNIVERSITY", display heading "Academic Overview", lead text "Tahmid Anwar Mubin · ID: 232-115-310 · Semester 7 (Computer Science & Engineering) · 14 credits enrolled · Tuesday, 17 November 2026."
- Stat strip (3 cells, hairline dividers): Cumulative CGPA 3.35 / This Term 14 / Unread Notices 8.
- Ruled lists: "Today" (Software Engineering 10:00–11:30 AM, SMP-CS-430 · Theory, S-221 · M. Akter, "In progress"; Technical Writing 1:00–2:30 PM, "Upcoming") and "Coming up" (Tue 17 Nov Software Engineering; Tue 17 Nov Technical Writing; Wed 18 Nov Algorithms). Pill buttons "Curriculum & Grades ↗", "Full routine ↗", "Weekly view ↗".
- Colours, type scale, spacing and motifs match `DESIGN.md`'s dark tokens and component descriptions. No accent colour, no error / warning / success colour, no focus ring is visible. The font is a clean grotesque sans-serif; the exact family **cannot be read from the image** — font family remains UNCONFIRMED (D-040).

### Image 2 — light "Account" settings modal (1399 × 857)

- Modal over a dimmed, blurred dark page (the Academic Overview behind it). Left nav: Search, Account (selected), Appearance, Behavior, Notifications, Customize, Connected Apps, Data Controls. Right pane: "Account", Tahmid Anwar Mubin, student@metrouni.edu.bd, "Manage Account", "Sign out", Registrar Student ID 232-115-310 · Batch 45 (Sec A) with an outlined "✓ Verified" pill, Department "Computer Science & Engineering", App → Language "English", Enrollment "Semester 7 · Spring 2026, B.Sc. in Computer Science & Engineering", footer "Browser-local storage · Auto-saved on this device" with "Export Backup".
- Colours, modal radius, grouped setting cards, hairline dividers and pill buttons match `DESIGN.md`'s light tokens. The "Verified" pill works as outlined text + check glyph with no colour — confirms the D-040 approach.
- **Two things in this screenshot must NOT be copied:** (1) the "UniConnect" product name / branding; (2) the "Browser-local storage · Auto-saved on this device" model — UniLink stores authoritative state server-side in MySQL with server sessions (D-013); localStorage is not a source of truth. The screenshot is a visual reference only, not a data model.

### Design conclusion

`DESIGN.md` is verified as a faithful transcription of the two screenshots, within the limits it already states: several values are "approx" (measured from screenshots), the scrim is the blurred page behind the modal (not a designed token), and font family, accent, status colours and focus ring are absent from the reference. D-040 remains OPEN. The `text-muted` contrast warnings in `DESIGN.md` (3.35:1 light, 3.85:1 dark) are consistent with the muted eyebrow / caption text visible in both screenshots. The linter claim in `DESIGN.md` (0 errors, 2 warnings) was not re-run in this addendum — re-run it in the design-system phase and record the output.

---

## 4. Decision-log updates from this verification

New entries D-072 to D-075 were added to the project copy at `docs/requirements/REQUIREMENTS_DECISION_LOG.md`. The coordinator's new decisions must start at D-076 to avoid a collision.

- **D-072 — SRS verification completed 2026-11-17 (ADOPTED).** SRS received and checked: 116 / 116 IDs, 31 / 31 TCs, no wording mismatches in the template's abridged text. D-064 is closed. The template is now the verified traceability baseline; the SRS is the authority where the template abridges (notably FR-AUTH-01: address and profile photo at sign-up).
- **D-073 — pic.docx verification completed 2026-11-17 (ADOPTED).** Both screenshots viewed; `DESIGN.md` is a faithful transcription within its stated limits. D-040 remains OPEN (font, accent, status colours, focus ring, scrim). UniConnect branding and the browser-local-storage model in the screenshots must NOT be copied.
- **D-074 — Sector list is open-ended (ADOPTED).** FR-SEC-01 says "including … Competitive Programming etc." Seed the six named sectors; the Platform Admin manages the full list via FR-ADM-10. This closes Phase 1 Q7 in part — no further sector list is needed from the owner before Phase 4.
- **D-075 — Ambiguities that survive SRS verification (OPEN).** The SRS does not resolve: (a) phone uniqueness — the SRS requires unique email and unique student ID per university only (FR-AUTH-02); D-067 soft-uniqueness default stands as the build decision; (b) what "public information" means for unverified students (SRS §2.7) — D-016 read-only National feed stands as the PROPOSED interpretation; (c) FR-COM-01 / FR-COM-05 garbled wording — D-021 stands; (d) contribution points — "optional" in scope, no FR — D-029b not-built stands; (e) ID-image deletion timing — SRS §5.6 says "deleting ID images after verification" without a period; D-053 (7 days after approval, 30 days after rejection) stands as the operational interpretation. None of these blocks the build; each is recorded as an assumption in the traceability matrix.

D-071 is updated in the log: verification, visibility, moderator scope and deletion policies are now checked against the SRS text and confirmed as readings of it, except for the items in D-075, which remain assumptions.

---

## 5. What this changes for the build

1. **Nothing in the approved architecture changes.** D-001 (Spring Boot, not Laravel) and D-002 (React + Vite, not Bootstrap) are now SRS-verified, not just log readings.
2. **Sign-up must collect address and profile photo** (FR-AUTH-01, full SRS text) — the coordinator must not build sign-up from the template's truncated text alone.
3. **Seed sectors:** Software Engineering, Data Science, AI/ML, Cyber Security, Web Development, Competitive Programming.
4. **Design:** build to `DESIGN.md` as verified; do not copy UniConnect branding or browser-local storage; do not invent accent / status / focus colours.
5. **Traceability:** `docs/traceability.md` remains `Not started` throughout (no code is claimed complete by this addendum). The SRS verification itself is evidence for the matrix's "Requirement (SRS text)" column, not for any implementation status.

---

## 6. Phase 1 status after this addendum

**Completed:** SRS verification (116 / 116, 31 / 31), pic.docx verification (2 images viewed), decision-log entries D-072–D-075, this addendum, source files preserved under `docs/requirements/source/` and `docs/design/source/`.
**Partially completed:** none new — the Phase 1 addendum step in `01.md` §9.1 is now complete except for the design-linter re-run, which is deferred to the design-system phase.
**Blocked / open:** D-040 (design gaps), D-067 (phone uniqueness — build decision made, owner may still overrule), D-075 ambiguities. None blocks the build under the Master Execution Prompt.
