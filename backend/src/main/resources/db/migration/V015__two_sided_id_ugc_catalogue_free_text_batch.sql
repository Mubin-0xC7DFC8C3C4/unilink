-- V015: two-sided ID verification (D-190/D-191), UGC catalogue category (D-192),
-- free-text department (D-193) and free-text batch/session (D-194).
-- ADDITIVE ONLY: every column is NULLABLE, nothing is dropped, renamed or
-- overwritten, and no existing row is changed except the two seeded
-- universities' category (below). Legacy rows keep department_id / batch_id
-- and resolve their labels through the catalog tables.
-- TiDB rules (D-188): ONE schema change per statement. Never combine an
-- ADD COLUMN with an ADD INDEX on the new column in a single ALTER
-- (TiDB Error 1072). No indexes are added here at all.
-- The UGC university seed (INSERT IGNORE, official names + category) is
-- appended at the end of this file once the UGC research is complete.

-- D-192: UGC category for the university catalogue: "PUBLIC" or "PRIVATE".
-- Null for legacy rows until classified.
ALTER TABLE universities ADD COLUMN category VARCHAR(16) NULL;

-- D-193: free-text department label. Mutually exclusive with department_id:
-- exactly one is set per user.
ALTER TABLE users ADD COLUMN department_label VARCHAR(255) NULL;

-- D-194: free-text batch/session, stored verbatim ("44", "2022-23", "Fall 2024").
-- New registrations set this and leave batch_id NULL.
ALTER TABLE users ADD COLUMN batch_label VARCHAR(64) NULL;

-- D-193: department_id becomes nullable. Free-text-department users have no
-- catalog row; the FK to departments is preserved (MODIFY keeps constraints).
ALTER TABLE users MODIFY COLUMN department_id BIGINT UNSIGNED NULL;

-- D-190: the verification request carries the back image key, so the review
-- unit is front + back of the same card.
ALTER TABLE verification_requests ADD COLUMN id_card_back_key VARCHAR(512) NULL;

-- D-191: OCR text of the back of the card (university, address, validity),
-- admin-only evidence alongside ocr_text.
ALTER TABLE verification_requests ADD COLUMN ocr_text_back MEDIUMTEXT NULL;

-- D-192: classify the two seeded universities. Both are UGC-recognized:
-- Metropolitan University is a private university; the University of Dhaka
-- is a public university (UGC public/private lists, accessed 2026-11-25).
UPDATE universities SET category = 'PRIVATE' WHERE name = 'Metropolitan University';
UPDATE universities SET category = 'PUBLIC' WHERE name = 'University of Dhaka';

-- D-192: UGC university seed (169 rows: 61 public + 108 private).
-- Source: UGC lists at ugc-universities.gov.bd (public + private),
-- accessed 2026-11-25; full transcription + exclusions in
-- docs/ugc-catalogue-research.md (§5–§7). INSERT IGNORE (D-167):
-- re-runnable; MU/DU already exist and are skipped by uk_universities_name.
-- Names are the UGC-listed names verbatim (footnote markers removed);
-- short_name comes from the UGC name's own parenthetical abbreviation.
-- Global University Bangladesh is seeded INACTIVE: UGC directed no new
-- admissions from January 2026 — existing students keep their association,
-- but it is not offered at signup (register rejects inactive universities).
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Dhaka', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Rajshahi', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh Agricultural University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh University of Engineering & Technology', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Chittagong', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Jahangirnagar University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Islamic University, Bangladesh', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Shahjalal University of Science & Technology', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Khulna University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('National University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh Open University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh Medical University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Gazipur Agricultural University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Hajee Mohammad Danesh Science & Technology University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Mawlana Bhashani Science & Technology University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Patuakhali Science And Technology University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Sher-e-Bangla Agricultural University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Chittagong University of Engineering & Technology (CUET)', 'CUET', 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Rajshahi University of Engineering & Technology (RUET)', 'RUET', 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Khulna University of Engineering & Technology (KUET)', 'KUET', 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Dhaka University of Engineering & Technology (DUET)', 'DUET', 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Noakhali Science & Technology University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Jagannath University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Comilla University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Jatiya Kabi Kazi Nazrul Islam University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Chittagong Veterinary and Animal Sciences University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Sylhet Agricultural University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Jessore University of Science & Technology', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Pabna University of Science and Technology', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Begum Rokeya University, Rangpur', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh University of Professionals', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Gopalganj Science & Technology University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh University of Textiles', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Barishal', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Rangamati Science and Technology University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh Maritime University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Islamic Arabic University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Chittagong Medical University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Rajshahi Medical University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Rabindra University, Bangladesh', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Frontier Technology, Bangladesh', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Netrokona University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Khulna Agricultural University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Jamalpur Science & Technology University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Sylhet Medical University, Sylhet', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Aviation And Aerospace University, Bangladesh', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Chandpur Science and Technology University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Kishoreganj University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Hobiganj Agricultural University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Khulna Medical University, Khulna', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Kurigram Agricultural University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Sunamganj Science and Technology University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Pirojpur Science & Technology University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Naogaon University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Meherpur University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Bogura', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Dhaka Central University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Thakurgaon University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Lakshmipur Science & Technology University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Narayanganj Science and Technology University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Shariatpur Agricultural University', NULL, 'PUBLIC', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('North South University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Science & Technology Chittagong', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Independent University, Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Central Women''s University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('International University of Business Agriculture & Technology', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('International Islamic University Chittagong', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Ahsanullah University of Science and Technology', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('American International University-Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('East West University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Asia Pacific', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Gono Bishwabidyalay', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('The People''s University of Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Asian University of Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Dhaka International University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Manarat International University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('BRAC University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Leading University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('BGC Trust University Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Sylhet International University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Development Alternative', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Premier University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Southeast University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Daffodil International University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Stamford University Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('State University of Bangladesh (SUB)', 'SUB', 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('City University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Prime University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Northern University Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Southern University Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Green University of Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Pundra University of Science & Technology', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('World University of Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Shanto-Mariam University of Creative Technology', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('The Millennium University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Eastern University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Metropolitan University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Uttara University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('United International University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of South Asia', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Victoria University of Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh University of Business & Technology (BUBT)', 'BUBT', 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Presidency University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Information Technology & Sciences', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Primeasia University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Royal University of Dhaka', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Liberal Arts Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Atish Dipankar University of Science & Technology', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh Islami University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('ASA University Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('East Delta University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('European University of Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Varendra University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Hamdard University Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('BGMEA University of Fashion & Technology(BUFT)', 'BUFT', 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('North East University Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('First Capital University of Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Ishakha International University, Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Z.H Sikder University of Science & Technology', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Exim Bank Agricultural University, Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('North Western University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Khwaja Yunus Ali University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Sonargaon University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Feni University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Britannia University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Port City International University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh University of Health Sciences', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Chittagong Independent University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Notre Dame University Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Times University, Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('North Bengal International University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Fareast International University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Rajshahi Science & Technology University (RSTU), Natore', 'RSTU', 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Brahmaputra International University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Cox''s Bazar International University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('R. P. Shaha University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('German University Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Global University Bangladesh', NULL, 'PRIVATE', 0);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('CCN University of Science & Technology', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh Army University of Science and Technology (BAUST), Saidpur', 'BAUST', 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh Army University of Engineering and Technology (BAUET), Qadirabad', 'BAUET', 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh Army International University of Science & Technology(BAIUST) ,Comilla', 'BAIUST', 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Scholars', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Canadian University of Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('N.P.I University of Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Northern University of Business & Technology, Khulna', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Rabindra Maitree University, Kushtia', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Creative Technology, Chittagong', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Central University of Science and Technology', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Tagore University of Creative Arts, Uttara, Dhaka, Bangladesh', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Global Village', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Anwer Khan Modern University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('ZNRF University of Management Sciences', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Ahsania Mission University of Science and Technology', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Khulna Khan Bahadur Ahsanullah University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bandarban University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Trust University, Barishal', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('International Standard University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('University of Brahmanbaria', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('R.T.M Al-Kabir Technical University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Dr. Momtaz Begum University of Science and Technology (MUST)', 'MUST', 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Chattogram BGMEA University of Fashion and Technology', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Bangladesh Army University of Science and Technology, Khulna', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Teesta University, Rangpur', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('International Islami University of Science and Technology Bangladesh (IIUSTB)', 'IIUSTB', 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Lalon University of Science and Arts', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Grameen University', NULL, 'PRIVATE', 1);
INSERT IGNORE INTO universities (name, short_name, category, active) VALUES ('Eco University of Science and Technology (EUST)', NULL, 'PRIVATE', 1);
