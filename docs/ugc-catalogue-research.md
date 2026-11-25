# UGC Catalogue Research — Recognised Universities in Bangladesh

**Access date:** 2026-11-25 (Asia/Dhaka)
**Researcher:** Muse (research subagent, UniLink project)
**Purpose:** Machine-usable seed catalogue of UGC-recognised universities for UniLink
(University catalogue / UGC catalogue workstream, decisions from D-190).

---

## 1. Sources (all read directly in this session)

| # | Source | URL | What was read | Page status on 2026-11-25 |
|---|--------|-----|---------------|---------------------------|
| S1 | University Grants Commission of Bangladesh — **List of Public Universities** | http://www.ugc-universities.gov.bd/public-universities | Full table, SL 1–61 | Fetched live, crawled 2026-11-25 14:43 UTC |
| S2 | University Grants Commission of Bangladesh — **List of Private Universities** | http://www.ugc-universities.gov.bd/private-universities | Full table, SL 1–117, including UGC footnotes/remarks | Fetched live; page copy crawled 2026-10-23 01:21 UTC (see freshness note below) |
| S3 | University Grants Commission of Bangladesh — **List of International Universities** | http://www.ugc-universities.gov.bd/international-universities | Full table, SL 1–3 | Fetched live, crawled 2026-11-25 14:34 UTC |
| S4 | UGC main site | https://www.ugc.gov.bd | Attempted | **Unreachable in this session** — fetch returned "page had no extractable content" / no HTTP status. See §2. |

**About the UGC web presence.** The UGC's university lists are published on its
dedicated universities portal, `ugc-universities.gov.bd` (branded "University Grants
Commission of Bangladesh"), not on the main `ugc.gov.bd` homepage. All three lists
above were read directly from that official portal in this session. The Ministry of
Education (`moedu.gov.bd`) was therefore not needed as a fallback. No Wikipedia page
was used as a source for any name in this report.

**Freshness note (private list).** The private-universities page copy available in
this session was crawled 2026-10-23, i.e. 33 days before the access date, whereas the
public and international pages were crawled today (2026-11-25). The private count
(117) is corroborated by contemporaneous press reporting of UGC data (Prothom Alo,
"117 private universities in the country"; Daily Observer, UGC website last updated
1 October 2026, reporting 59 public / 116 private at that date — the live UGC pages
read today show 61 public / 117 private, consistent with new approvals since
1 October 2026). Re-check the private list before seeding production data if more
than a few weeks have passed.

## 2. Counts (exactly as listed by the UGC)

| Category | UGC-listed count |
|----------|-----------------:|
| Public universities | **61** |
| Private universities | **117** |
| **Total, public + private** | **178** |
| International universities (separate UGC category, §6) | 3 |
| **Total across all three UGC lists** | **181** |

## 3. What the UGC lists do and do not contain

- **Given for every university:** official English name (transcribed exactly as
  listed, including capitalisation quirks such as "Patuakhali Science **And**
  Technology University" and "Aviation **And** Aerospace University, Bangladesh")
  and official website (as listed; a few entries have no website).
- **Location / district:** the UGC lists give **no location column**. Location is
  therefore recorded as "not stated by UGC" below, except where a place name is
  part of the listed name itself (e.g. "Rajshahi Science & Technology University
  (RSTU), **Natore**"; "Bangladesh Army International University of Science &
  Technology (BAIUST), **Comilla**"). No location has been inferred from memory
  or from any other source.
- **Abbreviations / short names:** recorded **only** where the abbreviation appears
  inside the UGC-listed name itself, e.g. "Chittagong University of Engineering &
  Technology **(CUET)**", "State University of Bangladesh **(SUB)**". No
  abbreviation has been invented. Where the UGC name contains no abbreviation, the
  column shows "—".
- **Ordering:** the UGC's own serial (SL) order is preserved in the tables below;
  it is not alphabetical.

## 4. Machine-readable list? (CSV / JSON / API)

**No.** The UGC publishes these lists as **plain HTML tables only**. Neither list
page offers a CSV/JSON download, and no public API endpoint is linked or
advertised on any of the three pages. The tables in §5–§6 of this file are a
manual transcription of those HTML tables (names, websites and UGC notes only),
made for UniLink's seed data. Any future refresh must re-read the HTML pages;
there is nothing to poll programmatically except the pages themselves.

## 5. Exclusions and flagged entries — read before seeding

The tables below transcribe **all** UGC-listed entries (61 + 117) so the record is
complete and auditable. For UniLink's seed catalogue, however, the following
private entries should **not** be offered as live, joinable universities, for the
reasons the UGC itself states on the list page. They are marked `EXCLUDE` in the
Status column.

### 5.1 Excluded from the recommended UniLink seed set (9 private universities)

| UGC SL | University | UGC's own reason (paraphrased; full Bengali/English notes are on the source page) |
|--------|------------|--------------------------------------------------------------------------------------|
| 92 | Rupayan A.K.M Shamsuzzoha University | "Academic programs have not yet started" |
| 98 | Shah Makhdum Management University, Rajshahi | "Academic programs have not yet started" |
| 103 | Microland University of Science and Technology | "Academic Programs have not yet started" |
| 111 | Justice Abu Zafar Siddiquie Science and Technology University, Kustia | "Academic Program has not started yet" |
| 102 | University of Skill Enrichment and Technology | All academic and administrative activities **closed from 29/09/2026** |
| 114 | The University of Comilla | **Shut down in 2006; not given permission to commence academic activities again** (Supreme Court order noted; UGC sought further direction from the Ministry; no permission to resume) |
| 115 | Queens University | Approved 6/9/2015 conditional on fulfilling all conditions within one year; **deadline expired** |
| 116 | America Bangladesh University | **Closed by government SRO dated 22/10/2006**; no approved campus/address; no valid authority; UGC states its activities have no legal basis under Private University Act 2010 s.12(1) |
| 113 | IBAIS University | **No approved address** (its Dhanmondi address was deleted from the UGC website after the High Court stay was vacated); no website listed |

**Recommended active seed set: 61 public + (117 − 9) 108 private = 169 universities.**

### 5.2 Flagged but retained in the seed set (listed by UGC with warnings/restrictions)

| UGC SL | University | UGC note |
|--------|------------|----------|
| 12 | The People's University of Bangladesh | `*` — no Chancellor-appointed Treasurer since establishment; no Pro-VC appointed. |
| 30 | Southern University Bangladesh | `(1)` — star withdrawn per Ministry of Education instruction; multiple court cases pending; an earlier UGC order suspending normal admissions was withdrawn 09/09/2019. |
| 41 | Victoria University of Bangladesh | `*` — unapproved building/campus. |
| 52 | European University of Bangladesh | `***` — Board of Trustees ownership dispute and court cases ongoing; admitted students beyond UGC-approved seats; operating from a campus built on less land than required after 12 years. |
| 78 | Global University Bangladesh | UGC directed **no new admissions from January 2026** until further instruction; students already enrolled continue. Seed it, but do not accept new-student registrations against it until UGC lifts the direction. |
| 112 | Grameen University | Listed, but **no website** given by UGC. |
| 117 | Eco University of Science and Technology (EUST) | Listed, but **no website** given by UGC. |

### 5.3 Public universities — no exclusions

No public university is flagged, suspended or closed on the UGC public list.
Five recently listed public universities have no website yet on the UGC page
(SL 54 Naogaon University, 55 Meherpur University, 58 Thakurgaon University,
60 Narayanganj Science and Technology University, 61 Shariatpur Agricultural
University); they are included in full — a missing website is not a reason to
exclude a recognised public university.

### 5.4 What was NOT included

- **International universities** (3) are a separate UGC category, listed in §6 for
  completeness. They are **not** counted in the 178 total and are **not** part of
  the recommended UniLink seed set unless the owner explicitly approves them
  (they do not admit through the normal Bangladeshi university route).
- **Foreign branch campuses / study centres** (e.g. UCSI University Malaysia
  study centre) are not on any UGC university list and are not included.
- **Darul Ihsan University** and any other institution whose UGC approval was
  revoked/closed and which no longer appears on the UGC lists do not appear here,
  because they are not on the source lists. Nothing was excluded on the basis of
  memory; exclusion is driven only by the UGC notes quoted in §5.1.
- **No names were added from Wikipedia or from memory.** Every name below comes
  from S1, S2 or S3 as read in this session.

---

## 6. Complete catalogue — Public universities (61)

Source: S1, http://www.ugc-universities.gov.bd/public-universities (accessed 2026-11-25).
"Location" is "not stated by UGC" unless a place name is part of the listed name.
"Abbr." is given only where it appears in the UGC-listed name.

| SL | Official English name (exactly as listed by UGC) | Category | Location (per UGC) | Abbr. (only if in UGC name) | Website (as listed by UGC) | Status |
|----|--------------------------------------------------|----------|--------------------|------------------------------|----------------------------|--------|
| 1 | University of Dhaka | Public | not stated by UGC | — | www.du.ac.bd | Listed |
| 2 | University of Rajshahi | Public | not stated by UGC | — | www.ru.ac.bd | Listed |
| 3 | Bangladesh Agricultural University | Public | not stated by UGC | — | www.bau.edu.bd | Listed |
| 4 | Bangladesh University of Engineering & Technology | Public | not stated by UGC | — | www.buet.ac.bd | Listed |
| 5 | University of Chittagong | Public | not stated by UGC | — | www.cu.ac.bd | Listed |
| 6 | Jahangirnagar University | Public | not stated by UGC | — | www.juniv.edu | Listed |
| 7 | Islamic University, Bangladesh | Public | not stated by UGC | — | www.iu.ac.bd | Listed |
| 8 | Shahjalal University of Science & Technology | Public | not stated by UGC | — | www.sust.edu | Listed |
| 9 | Khulna University | Public | not stated by UGC | — | www.ku.ac.bd | Listed |
| 10 | National University | Public | not stated by UGC | — | www.nu.edu.bd | Listed |
| 11 | Bangladesh Open University | Public | not stated by UGC | — | www.bou.ac.bd | Listed |
| 12 | Bangladesh Medical University | Public | not stated by UGC | — | www.bmu.ac.bd | Listed |
| 13 | Gazipur Agricultural University | Public | not stated by UGC | — | gau.edu.bd | Listed |
| 14 | Hajee Mohammad Danesh Science & Technology University | Public | not stated by UGC | — | www.hstu.ac.bd | Listed |
| 15 | Mawlana Bhashani Science & Technology University | Public | not stated by UGC | — | www.mbstu.ac.bd | Listed |
| 16 | Patuakhali Science And Technology University | Public | not stated by UGC | — | www.pstu.ac.bd | Listed |
| 17 | Sher-e-Bangla Agricultural University | Public | not stated by UGC | — | www.sau.edu.bd | Listed |
| 18 | Chittagong University of Engineering & Technology (CUET) | Public | not stated by UGC | CUET | www.cuet.ac.bd | Listed |
| 19 | Rajshahi University of Engineering & Technology (RUET) | Public | not stated by UGC | RUET | www.ruet.ac.bd | Listed |
| 20 | Khulna University of Engineering & Technology (KUET) | Public | not stated by UGC | KUET | www.kuet.ac.bd | Listed |
| 21 | Dhaka University of Engineering & Technology (DUET) | Public | not stated by UGC | DUET | www.duet.ac.bd | Listed |
| 22 | Noakhali Science & Technology University | Public | not stated by UGC | — | www.nstu.edu.bd | Listed |
| 23 | Jagannath University | Public | not stated by UGC | — | www.jnu.ac.bd | Listed |
| 24 | Comilla University | Public | not stated by UGC | — | www.cou.ac.bd | Listed |
| 25 | Jatiya Kabi Kazi Nazrul Islam University | Public | not stated by UGC | — | www.jkkniu.edu.bd | Listed |
| 26 | Chittagong Veterinary and Animal Sciences University | Public | not stated by UGC | — | www.cvasu.ac.bd | Listed |
| 27 | Sylhet Agricultural University | Public | not stated by UGC | — | www.sau.ac.bd | Listed |
| 28 | Jessore University of Science & Technology | Public | not stated by UGC | — | www.just.edu.bd | Listed |
| 29 | Pabna University of Science and Technology | Public | not stated by UGC | — | www.pust.ac.bd | Listed |
| 30 | Begum Rokeya University, Rangpur | Public | Rangpur (in name) | — | www.brur.ac.bd | Listed |
| 31 | Bangladesh University of Professionals | Public | not stated by UGC | — | www.bup.edu.bd | Listed |
| 32 | Gopalganj Science & Technology University | Public | not stated by UGC | — | www.gstu.edu.bd | Listed |
| 33 | Bangladesh University of Textiles | Public | not stated by UGC | — | www.butex.edu.bd | Listed |
| 34 | University of Barishal | Public | not stated by UGC | — | www.bu.ac.bd | Listed |
| 35 | Rangamati Science and Technology University | Public | not stated by UGC | — | www.rmstu.edu.bd | Listed |
| 36 | Bangladesh Maritime University | Public | not stated by UGC | — | bmu.edu.bd | Listed |
| 37 | Islamic Arabic University | Public | not stated by UGC | — | www.iau.edu.bd | Listed |
| 38 | Chittagong Medical University | Public | not stated by UGC | — | www.cmu.edu.bd | Listed |
| 39 | Rajshahi Medical University | Public | not stated by UGC | — | www.rmu.edu.bd | Listed |
| 40 | Rabindra University, Bangladesh | Public | not stated by UGC | — | www.rub.ac.bd | Listed |
| 41 | University of Frontier Technology, Bangladesh | Public | not stated by UGC | — | www.uftb.ac.bd | Listed |
| 42 | Netrokona University | Public | not stated by UGC | — | neu.ac.bd | Listed |
| 43 | Khulna Agricultural University | Public | not stated by UGC | — | www.kau.edu.bd | Listed |
| 44 | Jamalpur Science & Technology University | Public | not stated by UGC | — | www.jstu.ac.bd | Listed |
| 45 | Sylhet Medical University, Sylhet | Public | Sylhet (in name) | — | www.bsfmmu.edu.bd | Listed |
| 46 | Aviation And Aerospace University, Bangladesh | Public | not stated by UGC | — | www.aaub.edu.bd | Listed |
| 47 | Chandpur Science and Technology University | Public | not stated by UGC | — | www.cstu.ac.bd | Listed |
| 48 | Kishoreganj University | Public | not stated by UGC | — | bsmru.ac.bd | Listed |
| 49 | Hobiganj Agricultural University | Public | not stated by UGC | — | www.hau.ac.bd | Listed |
| 50 | Khulna Medical University, Khulna | Public | Khulna (in name) | — | www.shmu.ac.bd | Listed |
| 51 | Kurigram Agricultural University | Public | not stated by UGC | — | www.kuriau.edu.bd | Listed |
| 52 | Sunamganj Science and Technology University | Public | not stated by UGC | — | www.sstu.ac.bd | Listed |
| 53 | Pirojpur Science & Technology University | Public | not stated by UGC | — | www.prstu.ac.bd | Listed |
| 54 | Naogaon University | Public | not stated by UGC | — | — (none listed) | Listed |
| 55 | Meherpur University | Public | not stated by UGC | — | — (none listed) | Listed |
| 56 | University of Bogura | Public | not stated by UGC | — | www.ub.ac.bd | Listed |
| 57 | Dhaka Central University | Public | not stated by UGC | — | dcu.ac.bd | Listed |
| 58 | Thakurgaon University | Public | not stated by UGC | — | — (none listed) | Listed |
| 59 | Lakshmipur Science & Technology University | Public | not stated by UGC | — | — (none listed) | Listed |
| 60 | Narayanganj Science and Technology University | Public | not stated by UGC | — | — (none listed) | Listed |
| 61 | Shariatpur Agricultural University | Public | not stated by UGC | — | — (none listed) | Listed |

**Public total: 61.**

---

## 7. Complete catalogue — Private universities (117)

Source: S2, http://www.ugc-universities.gov.bd/private-universities (page copy crawled
2026-10-23; accessed 2026-11-25). UGC's own footnotes are summarised in the Status
column; the full Bengali/English notes are on the source page. `EXCLUDE` = do not
seed as a live university — see §5.1.

| SL | Official English name (exactly as listed by UGC) | Category | Location (per UGC) | Abbr. (only if in UGC name) | Website (as listed by UGC) | Status |
|----|--------------------------------------------------|----------|--------------------|------------------------------|----------------------------|--------|
| 1 | North South University | Private | not stated by UGC | — | www.northsouth.edu | Listed |
| 2 | University of Science & Technology Chittagong | Private | not stated by UGC | — | www.ustc.edu.bd | Listed |
| 3 | Independent University, Bangladesh | Private | not stated by UGC | — | www.iub.edu.bd | Listed |
| 4 | Central Women's University | Private | not stated by UGC | — | www.cwu.edu.bd | Listed |
| 5 | International University of Business Agriculture & Technology | Private | not stated by UGC | — | www.iubat.edu | Listed |
| 6 | International Islamic University Chittagong | Private | not stated by UGC | — | www.iiuc.ac.bd | Listed |
| 7 | Ahsanullah University of Science and Technology | Private | not stated by UGC | — | www.aust.edu | Listed |
| 8 | American International University-Bangladesh | Private | not stated by UGC | — | www.aiub.ac.bd | Listed |
| 9 | East West University | Private | not stated by UGC | — | www.ewubd.edu | Listed |
| 10 | University of Asia Pacific | Private | not stated by UGC | — | www.uap-bd.edu | Listed |
| 11 | Gono Bishwabidyalay | Private | not stated by UGC | — | www.gonouniversity.edu.bd | Listed |
| 12 | The People's University of Bangladesh * | Private | not stated by UGC | — | www.pub.ac.bd | FLAGGED — see §5.2 |
| 13 | Asian University of Bangladesh | Private | not stated by UGC | — | www.aub.ac.bd | Listed |
| 14 | Dhaka International University | Private | not stated by UGC | — | www.diu.ac | Listed |
| 15 | Manarat International University | Private | not stated by UGC | — | www.manarat.ac.bd | Listed |
| 16 | BRAC University | Private | not stated by UGC | — | www.bracu.ac.bd | Listed |
| 17 | Bangladesh University | Private | not stated by UGC | — | www.bu.edu.bd | Listed |
| 18 | Leading University | Private | not stated by UGC | — | www.lus.ac.bd | Listed |
| 19 | BGC Trust University Bangladesh | Private | not stated by UGC | — | www.bgctub.ac.bd | Listed |
| 20 | Sylhet International University | Private | not stated by UGC | — | www.siu.edu.bd | Listed |
| 21 | University of Development Alternative | Private | not stated by UGC | — | www.uoda.edu.bd | Listed |
| 22 | Premier University | Private | not stated by UGC | — | www.puc.ac.bd | Listed |
| 23 | Southeast University | Private | not stated by UGC | — | www.seu.edu.bd | Listed |
| 24 | Daffodil International University | Private | not stated by UGC | — | www.daffodilvarsity.edu.bd | Listed |
| 25 | Stamford University Bangladesh | Private | not stated by UGC | — | www.stamforduniversity.edu.bd | Listed |
| 26 | State University of Bangladesh (SUB) | Private | not stated by UGC | SUB | https://www.sub.ac.bd | Listed |
| 27 | City University | Private | not stated by UGC | — | www.cityuniversity.ac.bd | Listed |
| 28 | Prime University | Private | not stated by UGC | — | www.primeuniversity.edu.bd | Listed |
| 29 | Northern University Bangladesh | Private | not stated by UGC | — | www.nub.ac.bd | Listed |
| 30 | Southern University Bangladesh (1) | Private | not stated by UGC | — | www.southern.edu.bd | FLAGGED — see §5.2 |
| 31 | Green University of Bangladesh | Private | not stated by UGC | — | www.green.edu.bd | Listed |
| 32 | Pundra University of Science & Technology | Private | not stated by UGC | — | www.pundrauniversity.ac.bd | Listed |
| 33 | World University of Bangladesh | Private | not stated by UGC | — | www.wub.edu.bd | Listed |
| 34 | Shanto-Mariam University of Creative Technology | Private | not stated by UGC | — | www.smuct.ac.bd | Listed |
| 35 | The Millennium University | Private | not stated by UGC | — | www.themillenniumuniversity.edu.bd | Listed |
| 36 | Eastern University | Private | not stated by UGC | — | www.easternuni.edu.bd | Listed |
| 37 | Metropolitan University | Private | not stated by UGC | — | www.metrouni.edu.bd | Listed |
| 38 | Uttara University | Private | not stated by UGC | — | www.uttarauniversity.edu.bd | Listed |
| 39 | United International University | Private | not stated by UGC | — | www.uiu.ac.bd | Listed |
| 40 | University of South Asia | Private | not stated by UGC | — | www.southasiauni.ac.bd | Listed |
| 41 | Victoria University of Bangladesh * | Private | not stated by UGC | — | www.vub.edu.bd | FLAGGED — see §5.2 |
| 42 | Bangladesh University of Business & Technology (BUBT) | Private | not stated by UGC | BUBT | www.bubt.edu.bd | Listed |
| 43 | Presidency University | Private | not stated by UGC | — | www.presidency.edu.bd, www.pu.edu.bd | Listed |
| 44 | University of Information Technology & Sciences | Private | not stated by UGC | — | www.uits.edu.bd | Listed |
| 45 | Primeasia University | Private | not stated by UGC | — | www.primeasia.edu.bd | Listed |
| 46 | Royal University of Dhaka | Private | not stated by UGC | — | www.royal.edu.bd | Listed |
| 47 | University of Liberal Arts Bangladesh | Private | not stated by UGC | — | www.ulab.edu.bd | Listed |
| 48 | Atish Dipankar University of Science & Technology | Private | not stated by UGC | — | www.adust.edu.bd | Listed |
| 49 | Bangladesh Islami University | Private | not stated by UGC | — | www.biu.ac.bd | Listed |
| 50 | ASA University Bangladesh | Private | not stated by UGC | — | www.asaub.edu.bd | Listed |
| 51 | East Delta University | Private | not stated by UGC | — | www.eastdelta.edu.bd | Listed |
| 52 | European University of Bangladesh *** | Private | not stated by UGC | — | — (none listed) | FLAGGED — see §5.2 |
| 53 | Varendra University | Private | not stated by UGC | — | www.vu.edu.bd | Listed |
| 54 | Hamdard University Bangladesh | Private | not stated by UGC | — | www.hamdarduniversity.edu.bd | Listed |
| 55 | BGMEA University of Fashion & Technology(BUFT) (2) | Private | not stated by UGC | BUFT | www.buft.edu.bd | Listed |
| 56 | North East University Bangladesh | Private | not stated by UGC | — | www.neub.edu.bd | Listed |
| 57 | First Capital University of Bangladesh | Private | not stated by UGC | — | www.fcub.edu.bd | Listed |
| 58 | Ishakha International University, Bangladesh | Private | not stated by UGC | — | www.ishakha.edu.bd | Listed |
| 59 | Z.H Sikder University of Science & Technology | Private | not stated by UGC | — | www.zhsust.edu.bd | Listed |
| 60 | Exim Bank Agricultural University, Bangladesh | Private | not stated by UGC | — | www.ebaub.edu.bd | Listed |

| 61 | North Western University | Private | not stated by UGC | — | www.nwu.ac.bd | Listed |
| 62 | Khwaja Yunus Ali University | Private | not stated by UGC | — | www.kyau.edu.bd | Listed |
| 63 | Sonargaon University | Private | not stated by UGC | — | www.su.edu.bd | Listed |
| 64 | Feni University | Private | not stated by UGC | — | www.feniuniversity.ac.bd | Listed |
| 65 | Britannia University | Private | not stated by UGC | — | www.britannia.edu.bd | Listed |
| 66 | Port City International University | Private | not stated by UGC | — | www.portcity.edu.bd | Listed |
| 67 | Bangladesh University of Health Sciences | Private | not stated by UGC | — | www.buhs.ac.bd | Listed |
| 68 | Chittagong Independent University | Private | not stated by UGC | — | www.ciu.edu.bd | Listed |
| 69 | Notre Dame University Bangladesh | Private | not stated by UGC | — | www.ndub.edu.bd | Listed |
| 70 | Times University, Bangladesh | Private | not stated by UGC | — | http://www.timesuniversitybd.com | Listed |
| 71 | North Bengal International University | Private | not stated by UGC | — | www.nbiu.edu.bd | Listed |
| 72 | Fareast International University | Private | not stated by UGC | — | www.fiu.edu.bd | Listed |
| 73 | Rajshahi Science & Technology University (RSTU), Natore | Private | Natore (in name) | RSTU | www.rstu.edu.bd | Listed |
| 74 | Brahmaputra International University | Private | not stated by UGC | — | www.briu.ac.bd | Listed |
| 75 | Cox's Bazar International University | Private | not stated by UGC | — | www.cbiu.ac.bd | Listed |
| 76 | R. P. Shaha University | Private | not stated by UGC | — | www.rpsu.edu.bd | Listed |
| 77 | German University Bangladesh | Private | not stated by UGC | — | www.gub.edu.bd | Listed |
| 78 | Global University Bangladesh | Private | not stated by UGC | — | www.globaluniversity.edu.bd | FLAGGED — no new admissions from Jan 2026, see §5.2 |
| 79 | CCN University of Science & Technology | Private | not stated by UGC | — | www.ccnust.ac.bd | Listed |
| 80 | Bangladesh Army University of Science and Technology (BAUST), Saidpur | Private | Saidpur (in name) | BAUST | www.baust.edu.bd | Listed |
| 81 | Bangladesh Army University of Engineering and Technology (BAUET), Qadirabad | Private | Qadirabad (in name) | BAUET | www.bauet.ac.bd | Listed |
| 82 | Bangladesh Army International University of Science & Technology(BAIUST) ,Comilla | Private | Comilla (in name) | BAIUST | www.baiust.ac.bd | Listed |
| 83 | University of Scholars | Private | not stated by UGC | — | www.ius.edu.bd | Listed |
| 84 | Canadian University of Bangladesh | Private | not stated by UGC | — | www.cub.edu.bd | Listed |
| 85 | N.P.I University of Bangladesh | Private | not stated by UGC | — | www.npiub.edu.bd | Listed |
| 86 | Northern University of Business & Technology, Khulna | Private | Khulna (in name) | — | www.nubtkhulna.ac.bd | Listed |
| 87 | Rabindra Maitree University, Kushtia | Private | Kushtia (in name) | — | www.rmu.ac.bd | Listed |
| 88 | University of Creative Technology, Chittagong | Private | Chittagong (in name) | — | www.uctc.edu.bd | Listed |
| 89 | Central University of Science and Technology | Private | not stated by UGC | — | www.cust.ac.bd | Listed |
| 90 | Tagore University of Creative Arts, Uttara, Dhaka, Bangladesh | Private | Uttara, Dhaka (in name) | — | www.tuca.edu.bd | Listed |
| 91 | University of Global Village | Private | not stated by UGC | — | www.ugv.edu.bd | Listed |
| 92 | Rupayan A.K.M Shamsuzzoha University | Private | not stated by UGC | — | — (none listed) | **EXCLUDE — programs not yet started (§5.1)** |
| 93 | Anwer Khan Modern University | Private | not stated by UGC | — | www.akmu.edu.bd | Listed |
| 94 | ZNRF University of Management Sciences | Private | not stated by UGC | — | www.zums.edu.bd | Listed |
| 95 | Ahsania Mission University of Science and Technology | Private | not stated by UGC | — | www.amust.ac.bd | Listed |
| 96 | Khulna Khan Bahadur Ahsanullah University | Private | not stated by UGC | — | www.kkbau.ac.bd | Listed |
| 97 | Bandarban University | Private | not stated by UGC | — | www.bubban.ac.bd | Listed |
| 98 | Shah Makhdum Management University, Rajshahi | Private | Rajshahi (in name) | — | — (none listed) | **EXCLUDE — programs not yet started (§5.1)** |
| 99 | Trust University, Barishal | Private | Barishal (in name) | — | www.trustuniversity.edu.bd | Listed |
| 100 | International Standard University | Private | not stated by UGC | — | www.isu.ac.bd | Listed |
| 101 | University of Brahmanbaria | Private | not stated by UGC | — | www.uob.edu.bd | Listed |
| 102 | University of Skill Enrichment and Technology | Private | not stated by UGC | — | www.uset.ac.bd | **EXCLUDE — all activities closed from 29/09/2026 (§5.1)** |
| 103 | Microland University of Science and Technology | Private | not stated by UGC | — | — (none listed) | **EXCLUDE — programs not yet started (§5.1)** |
| 104 | R.T.M Al-Kabir Technical University | Private | not stated by UGC | — | www.rtm-aktu.edu.bd | Listed |
| 105 | Dr. Momtaz Begum University of Science and Technology (MUST) | Private | not stated by UGC | MUST | www.must.ac.bd | Listed |
| 106 | Chattogram BGMEA University of Fashion and Technology | Private | not stated by UGC | — | www.cbuft.edu.bd | Listed |
| 107 | Bangladesh Army University of Science and Technology, Khulna | Private | Khulna (in name) | — | www.baustkhulna.ac.bd | Listed |
| 108 | Teesta University, Rangpur | Private | Rangpur (in name) | — | www.teestauniversity.ac.bd | Listed |
| 109 | International Islami University of Science and Technology Bangladesh (IIUSTB) | Private | not stated by UGC | IIUSTB | www.iiustb.ac.bd | Listed |
| 110 | Lalon University of Science and Arts | Private | not stated by UGC | — | https://lusa.ac.bd | Listed |
| 111 | Justice Abu Zafar Siddiquie Science and Technology University, Kustia | Private | Kustia (in name) | — | — (none listed) | **EXCLUDE — program not started yet (§5.1)** |
| 112 | Grameen University | Private | not stated by UGC | — | — (none listed) | Listed (no website given by UGC) |
| 113 | IBAIS University *** | Private | not stated by UGC | — | — (none listed) | **EXCLUDE — no approved address (§5.1)** |
| 114 | The University of Comilla *** | Private | not stated by UGC | — | — (none listed) | **EXCLUDE — shut down 2006, no permission to resume (§5.1)** |
| 115 | Queens University ** | Private | not stated by UGC | — | — (none listed) | **EXCLUDE — conditional approval lapsed (§5.1)** |
| 116 | America Bangladesh University *** | Private | not stated by UGC | — | — (none listed) | **EXCLUDE — closed by govt. SRO 22/10/2006, no legal basis (§5.1)** |
| 117 | Eco University of Science and Technology (EUST) | Private | not stated by UGC | — | — (none listed) | Listed (no website given by UGC) |

**Private total: 117 listed; 108 recommended for the active UniLink seed set (117 − 9 excluded).**

---

## 8. International universities (separate UGC category — NOT counted above)

Source: S3, http://www.ugc-universities.gov.bd/international-universities (accessed 2026-11-25).
Listed for completeness only; not part of the 178 total and not recommended for the
UniLink seed set without an explicit owner decision.

| SL | Official English name (exactly as listed by UGC) | Category | Location (per UGC) | Abbr. (only if in UGC name) | Website (as listed by UGC) |
|----|--------------------------------------------------|----------|--------------------|------------------------------|----------------------------|
| 1 | Islamic University of Technology (IUT), Gazipur | International | Gazipur (in name) | IUT | www.iutoic-dhaka.edu |
| 2 | Asian University for Women | International | not stated by UGC | — | www.auw.edu.bd |
| 3 | South Asian University | International | not stated by UGC | — | www.southasianuniversity.org |

**International total: 3.**

---

## 9. Method, limitations and verification notes

1. **Method.** Each UGC list page was fetched and read in full in this session
   (public: 61 rows; private: 117 rows plus footnotes/remarks; international:
   3 rows). Names, websites and UGC notes were transcribed exactly as listed.
   The `ugc.gov.bd` homepage itself could not be fetched in this session (no
   extractable content); the lists live on the UGC's dedicated portal
   `ugc-universities.gov.bd`, which is the authoritative source used here.
2. **No Wikipedia, no memory.** No university name, abbreviation or location in
   this file comes from Wikipedia or from the researcher's prior knowledge.
   Abbreviations appear only where the UGC name itself contains them; locations
   appear only where the UGC name itself contains a place name.
3. **Counts reconcile.** Public 61 + private 117 = 178; plus 3 international =
   181 across all UGC lists. The public page was crawled the same day as this
   research (2026-11-25); the private page copy was crawled 2026-10-23 — see the
   freshness note in §1.
4. **Names change over time.** Several public universities on the current list
   carry names that differ from older secondary lists (e.g. SL 12 "Bangladesh
   Medical University", SL 13 "Gazipur Agricultural University", SL 32 "Gopalganj
   Science & Technology University", SL 41 "University of Frontier Technology,
   Bangladesh", SL 48 "Kishoreganj University", SL 53 "Pirojpur Science &
   Technology University"). This file records the names **as the UGC lists them
   today**; older names from other sources were not substituted.
5. **Status markers.** The `*`, `**`, `***`, `(1)` and `(2)` markers in the
   private table are the UGC's own markers, reproduced from the source page; their
   meanings are summarised in §5.1–§5.2 (full text, largely in Bengali, is on the
   source page).
6. **What still needs a human/owner decision.** (a) Whether to seed the 7
   flagged-but-listed private universities (§5.2) as normal entries; (b) whether
   to include the 3 international universities; (c) department data — the UGC
   lists give no department information, so university-specific departments must
   come from each university's own verified sources, not from this file.
7. **Suggested re-check cadence.** Re-read the three UGC pages at least quarterly
   (new universities are approved regularly; counts moved from 59 public /
   116 private on 1 Oct 2026 to 61 / 117 by 25 Nov 2026) and before any
   production seed refresh.

---

*End of report. Sources S1–S3 read 2026-11-25; S4 (ugc.gov.bd homepage) unreachable in this session.*
