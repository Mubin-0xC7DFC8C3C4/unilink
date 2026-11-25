# Department Catalogue Research — Canonical Disciplines + Aliases

**Access date:** 2026-11-25 (Asia/Dhaka)
**Researcher:** Muse (implementation subagent, UniLink project)
**Purpose:** Canonical `department_catalog` seed + alias list for UniLink
(Department catalogue workstream, decision D-198).

---

## 1. Sources (all read directly in this session)

| # | Source | URL | What was read | Page status on 2026-11-25 |
|---|--------|-----|---------------|---------------------------|
| S1 | Metropolitan University — **School of Science and Technology** (official site) | https://metrouni.edu.bd/sites/school-of-science-technology/school-of-science-technology | School description naming its two departments | Fetched live via search index, crawled ~10 hours before access |
| S2 | University of Dhaka — **Faculty of Engineering and Technology** (official site) | https://du.ac.bd/faculty/facengg and https://du.ac.bd/facultyVariousInfo/facengg?type=description_en | Faculty description + department list (5 departments) | Fetched live via search index, crawled ~1 day before access |
| S3 | Owner brief 2026-11-25, Priority 2 §B (decision D-198) | — (project instruction, not a web source) | Illustrative list of recognised disciplines to seed as canonical catalogue rows | Received 2026-11-25 |
| S4 | UGC university lists (see `docs/ugc-catalogue-research.md`) | http://www.ugc-universities.gov.bd/public-universities and /private-universities | University names/categories only | Read 2026-11-25 |

**What each source is used for.** S1 and S2 are the only sources used to
verify a university→department **link** (see §4). S3 is the source of the
canonical discipline **names** (§3): the brief lists the recognised
disciplines to seed, and the catalogue rows are those names, not claims
about any particular university offering them. S4 gives university names
only — the UGC lists contain **no department information at all**, so no
link can be verified from S4.

**What was NOT used as a source for any link.** Wikipedia, ranking sites
(eduRank, Ulkaa), admissions-advice sites and social-media posts were
returned by search but were **not** used to verify anything: D-198
requires each link to come from the university's own official source.
Several of those secondary pages list further MU programmes (e.g. Software
Engineering, Business Administration, Economics, English, Law); because
they are not MU's own pages, no link row is seeded for them and they are
recorded as NOT verified in §5.

## 2. What the catalogue is and is not

- `department_catalog` holds **canonical disciplines** (e.g. "Computer
  Science and Engineering") with a `short_name` and 1–3 aliases each.
  A catalogue row is a discipline name, not a claim that any particular
  university teaches it.
- `departments` holds **verified links**: one row per (university,
  catalog) pair, and only where the university's own official source
  confirms that university offers that department (§4).
- `department_aliases` holds alternate spellings/abbreviations. An alias
  is globally unique, so e.g. "CSE" maps to exactly one canonical row.
- Provenance columns on `department_catalog` (`source_url`,
  `verified_at`, `verified_by`) exist for a future admin to record a
  confirming official source per row. **They are left NULL by the V016
  seed**: the canonical names come from the brief's list (S3), which is
  not a URL, and no per-discipline official source page was confirmed in
  this session. Filling them with a guessed URL would be fabrication.
  The honest per-entry source record is this document, not the columns.

## 3. Canonical disciplines seeded by V016 (32 rows)

Source: the brief's recognised-discipline list (S3), with standard
programme naming. Two rows already exist from V001 (CSE, EEE); V016
adds the other 30. Short names and aliases are the common
abbreviations students type when searching (these are what make "CSE"
match "Computer Science and Engineering" in the sign-up search).

| # | Canonical name | short_name | Aliases |
|---|----------------|------------|---------|
| 1 | Computer Science and Engineering | CSE | Computer Science; Computer Science & Engineering |
| 2 | Software Engineering | SWE | Software Engg; Software Eng. |
| 3 | Electrical and Electronic Engineering | EEE | Electrical Engineering; Electronic Engineering |
| 4 | Civil Engineering | Civil | Civil Engg |
| 5 | Mechanical Engineering | Mechanical | Mech Engg; ME |
| 6 | Industrial and Production Engineering | IPE | Industrial Engineering; Production Engineering |
| 7 | Architecture | Architecture | Arch |
| 8 | Pharmacy | Pharmacy | Pharm |
| 9 | Biotechnology | Biotech | Biotechnology Engg |
| 10 | Genetic Engineering | Genetic Engg | Genetic Eng. |
| 11 | Microbiology | Microbiology | Microbio |
| 12 | Chemistry | Chemistry | Chem |
| 13 | Physics | Physics | Phy |
| 14 | Mathematics | Maths | Math |
| 15 | Statistics | Statistics | Stats; Stat |
| 16 | Economics | Economics | Econ |
| 17 | Business Administration | BBA | Business Admin |
| 18 | Accounting | Accounting | Acc |
| 19 | Finance | Finance | Fin |
| 20 | Marketing | Marketing | Mkt |
| 21 | Management | Management | Mgmt |
| 22 | English | English | Eng |
| 23 | Law | Law | LLB |
| 24 | Sociology | Sociology | Soc |
| 25 | Political Science | Poli Sci | Political Sci; PS |
| 26 | Psychology | Psychology | Psych |
| 27 | Journalism & Media Studies | Journalism | Media Studies; JMS |
| 28 | Public Administration | Pub Admin | Public Admin; PA |
| 29 | Environmental Science | Env Science | Environmental Sci; ES |
| 30 | Agriculture | Agriculture | Agri |
| 31 | Fisheries | Fisheries | Fish |
| 32 | Veterinary Science | Vet Science | Veterinary Sci; DVM |

**Notes on the list.**
- The brief's list is "~35" disciplines including "MBBS/BDS/Nursing
  where the university source confirms them". No university source
  confirming a medical discipline was read in this session, so **MBBS,
  BDS and Nursing are NOT seeded** (see §5). The seeded total is
  therefore 32, not 35. An admin can add them later through
  `POST /admin/master-data/department-catalog` once a confirming
  official source exists.
- "Journalism & Media Studies" is seeded under that exact brief wording.
  One secondary source describes MU's Journalism and Media Studies as
  "(proposed)"; it is not an MU official page, so no MU link is seeded.
- Aliases are spellings/abbreviations only. No alias is shared between
  two canonical rows (enforced by the unique key on
  `department_aliases.alias`).

## 4. Verified university→department links (4 rows, all pre-existing)

These are the **only** link rows in the database after V016. All four
were seeded by V001 and are re-asserted (idempotently, `INSERT IGNORE`)
by V016 with explicit per-link `WHERE` clauses — the V001 cross-join
seed pattern is **not** repeated, so no new link is invented.

| University | Department | Verified from (university's own official source) |
|------------|------------|--------------------------------------------------|
| Metropolitan University (MU) | Computer Science and Engineering (CSE) | S1: MU's own School of Science and Technology page states the school runs "two departments, Computer Science and Engineering (CSE) and Electrical and Electronic Engineering (EEE)", with BSc in CSE offered. |
| Metropolitan University (MU) | Electrical and Electronic Engineering (EEE) | S1, same page: EEE is the school's second department, BSc in EEE offered. |
| University of Dhaka (DU) | Computer Science and Engineering (CSE) | S2: DU's own Faculty of Engineering and Technology pages list the Department of Computer Science and Engineering among the faculty's 5 departments, with BSc/MSc programmes. |
| University of Dhaka (DU) | Electrical and Electronic Engineering (EEE) | S2, same pages: the Department of Electrical and Electronic Engineering is listed among the faculty's 5 departments; its history (renamed to EEE in November 2014) is on DU's own faculty page. |

## 5. NOT verified — no link row seeded

The following were **not** verified from the university's own official
source in this session, so no `departments` link row exists for them.
Students at these universities use the typed-department fallback (D-193),
unchanged. This list is explicit so that a later reader does not mistake
"catalogue row exists" for "university offers it".

- **Every university other than MU and DU, for every department.** 167
  of 169 seeded universities have zero verified department links. No
  official programme/department page for any of them was read in this
  session.
- **MU — all departments other than CSE and EEE** (including Software
  Engineering, Business Administration, Economics, English, Law,
  Journalism and Media Studies): secondary pages list some of these, but
  no MU official page confirming them was read in this session. The MU
  official page read (S1) covers only the School of Science and
  Technology's two departments.
- **DU — all departments other than CSE and EEE** (including Applied
  Chemistry & Chemical Engineering, Nuclear Engineering, Robotics and
  Mechatronics Engineering, and every department outside the Faculty of
  Engineering and Technology): DU's own faculty page (S2) does list
  these, but only CSE/EEE were in scope for the V001 seed and only
  those two link rows are carried forward. The other DU departments are
  real per DU's own site and are the natural next links for an admin to
  add via the master-data flow — they were **not** added by V016 because
  this slice seeds no new link rows beyond re-asserting the four
  pre-existing ones.
- **MBBS, BDS, Nursing (all universities):** not seeded as catalogue
  rows at all (§3). No university source confirming them was read.
- **No link was inferred from a university's name, a ranking site, an
  admissions-advice site, Wikipedia, or prior knowledge.** If a link is
  not in §4, it is not verified.

## 6. Method, limitations and re-check cadence

1. **Method.** Two official university pages (S1, S2) were read via
   search in this session and are quoted in §4. The canonical names
   come from the owner's brief (S3). Nothing else was treated as a
   source.
2. **Honesty boundary.** A catalogue row (§3) is a discipline name from
   the brief; it asserts nothing about any university. Only §4 asserts
   university→department facts, and only for the four rows whose
   official source was read.
3. **What still needs a human/owner decision.** (a) Whether to grow the
   verified link table university by university from each university's
   own official programme/department page (the admin master-data flow
   now supports this without a migration); (b) whether to add
   MBBS/BDS/Nursing once a confirming university source exists;
   (c) per-row provenance (`source_url`/`verified_at`/`verified_by`)
   to be filled by the admin who verifies each future link.
4. **Re-check cadence.** Re-read the official programme pages before
   adding any new link row; university programme lists change (new
   departments open, names change). The catalogue itself needs no
   refresh cadence — new disciplines are added by Platform Admin, not
   by re-running research.

---

*End of report. Sources S1–S2 read 2026-11-25; S3 is the owner brief
(D-198); S4 is docs/ugc-catalogue-research.md.*
