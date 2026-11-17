# Phase 3 — Worker C report section (P1, file upload security)

Worker C, 2026-11-17. Decisions D-104–D-107 are in
`docs/phase-reports/07-decisions-c.md`. No migration was created (none needed).
No Flyway migration was rewritten. No event kind/type storage, no WARN action
(D-088 deferred items untouched). Nothing was committed (coordinator commits).

## 1. Completed items (with file:line evidence)

Line numbers are from the working tree at the time of this work; use `git diff`
against HEAD `e6ff354` to locate them if the tree has moved.

### 1.1 EXIF stripping, dependency-free, on every image upload path (D-104)
- NEW `backend/src/main/java/com/unilink/storage/ExifStripper.java` — pure-JDK
  parser, **no new Maven dependency** (`pom.xml` untouched). JPEG drops APP1
  segments starting `Exif\0\0`; PNG drops `eXIf` chunks; WebP drops `EXIF`
  chunks, repairs the RIFF size and clears the VP8X EXIF flag. Pixel data is
  copied byte-for-byte.
- Wired into all five image upload paths (validate original bytes first, strip,
  store stripped bytes):
  - `identity/service/AuthService.java` — `register` (ID card + profile photo)
    and `resubmit` (ID card).
  - `profile/ProfileService.java` — `uploadPhoto`, `uploadCover`.
  - `content/PostService.java` — `addPostAttachment`.
  - `messaging/MessagingService.java` — `sendMessageWithAttachment`.
- Tests prove the GPS payload is gone from stored bytes on every path:
  `storage/ExifStripperTest.java` (9 tests, crafted GPS-bearing JPEG/PNG/WebP)
  and `integration/UploadSecurityTest.java` (15 tests, end-to-end on H2).

### 1.2 Per-post attachment cap 5, server-side, 400 over the limit (D-104/D-105)
- `content/PostService.java` `addPostAttachment`: counts existing attachments
  via the new `identity/repo/AttachmentRepository.java`
  `countByOwnerTypeAndOwnerId`, throws 400 `ATTACHMENT_LIMIT` at 5, checked
  BEFORE storing so a rejected 6th file leaves no orphan.
- Per-message limit is 1 by API design (no call adds a second file to a
  message); no per-room cumulative cap (see D-105 for the reasoning).
- Test: `UploadSecurityTest.postAttachmentCountIsCappedAtFive`.

### 1.3 Upload validation hardening (D-106)
- `storage/UploadValidator.java`:
  - `sniffContentType`: JPEG now requires a real marker code as the 4th byte
    (`FF D8 FF 00` rejected); PNG requires the full 8-byte signature; declared
    type/extension still never trusted.
  - `validateImage` / `validateAttachment` now RETURN the sniffed type (was
    `void`); all call sites use it, so the stored type is the sniffed type.
  - `validateAttachment` error message no longer hardcodes "10 MB".
  - NEW `sanitizeFileName` (static): strips directories, quotes, backslashes,
    control chars; caps at 200 chars; null/blank → `file`.
  - NEW constant `MAX_ATTACHMENTS_PER_POST = 5`.
- `storage/LocalStorageAdapter.java`: `size()` had no traversal guard — now
  applies the same root-normalisation check as open/exists/delete (returns -1
  outside the root). `store()` rejects a bad namespace and defaults a null
  file name to `file`.
- `identity/service/AuthService.java`: register/resubmit no longer store a
  hardcoded `"image/jpeg"` content type; the sniffed type is stored.
- `admin/api/AdminController.java`: `id-image` used to always answer
  `image/jpeg` even for PNG/WebP ID cards; it now sniffs the stored bytes
  (constructor gained an `UploadValidator` parameter; no test constructs the
  controller directly, so nothing else changed).
- `content/api/PostController.java` and
  `messaging/api/MessagingRestController.java`: `Content-Disposition`
  filenames now go through `sanitizeFileName` (the old code only stripped
  `"`, leaving CR/LF header injection open).

### 1.4 Ownership / lifecycle fixes (D-107)
- NEW `DELETE /api/posts/{id}/attachments/{attachmentId}`
  (`PostController.java`, `PostService.deletePostAttachment`): owner-only
  (404 for anyone else or a wrong post id), deletes the DB row AND the stored
  object. There was previously no attachment-delete path.
- `PostService.listPostAttachments` / `openPostAttachment`: a post whose status
  is not `ACTIVE` now 404s (previously a soft-deleted `REMOVED` post's files
  stayed downloadable, because `getPost` does not check status). Files are
  deliberately KEPT on soft-delete so moderation RESTORE still works.
- Profile photo/cover replacement was verified sound and left in place: the
  controller passes the session user's id, the key deleted is read from that
  user's own row, and the new object is stored BEFORE the old one is deleted.
- `AuthService.resubmit` deliberately does NOT delete the previous ID-card
  object: the rejected attempt's request row still references it and D-053
  keeps rejected images 30 days; `purgeIdImages` deletes it on expiry.
- Review result for paths already sound (no change made, evidence):
  - `LocalStorageAdapter.store/open/exists/delete`: root-normalisation guard
    present on all four (delete's guard was added in Phase 2 verification).
  - `PostService.addPostAttachment`: owner-only via `findByIdAndAuthorId`.
  - `MessagingService` attachments: room membership checked on
    list/download; `sendMessageWithAttachment` goes through `sendMessage`,
    which checks membership, verified status and closed rooms.
  - `AdminController.idImage`: moderator scope enforced via
    `verificationIdCardKey` (404 out of scope), `no-store`.
  - `ScheduledJobs.purgeUnconfirmed` / `purgeIdImages`: delete only keys read
    from the rows they purge; `purgeIdImages` nulls the key after deleting.
  - `AccountDeletionService`: deletes only keys collected from the caller's
    own user row and latest verification request.
  - Size caps already enforced per D-029a (photo 2 MB, cover 5 MB, ID 5 MB,
    attachment 10 MB) plus the Spring multipart 10 MB file / 11 MB request
    caps in `application.yml`; boundary tests added (exact cap passes,
    cap+1 fails).

## 2. Deferred items (with reasons)
- XMP / PNG text-chunk stripping: only EXIF is removed (D-104). XMP APP1 and
  PNG `iTXt`/`zTXt` chunks can carry location/creator text; dropping them is
  a follow-up, not done here, because D-029a specifies EXIF and XMP may carry
  copyright the owner wants.
- Backfill for already-stored images: only new uploads are stripped. ID images
  age out under D-053; a backfill job for old photos/covers/attachments was
  not built (needs an owner decision on scope).
- Event-poster upload: no endpoint exists, so there is no EXIF surface to fix.
  When a poster upload is built it must use `UploadValidator` + `ExifStripper`.
- Per-user daily upload quota: noted in D-105 as a follow-up, not built.
- The count-check/store race (two concurrent 5th uploads can both pass): one
  extra file, not a breach; a DB lock was disproportionate (D-105).

## 3. Security notes
- The one real gap found and fixed: soft-deleted posts' attachments stayed
  downloadable (§1.4). Everything else reviewed was sound or fixed as listed.
- Two latent issues worth knowing, neither changed here because they are
  outside this brief: (a) `PostService.getPost` returns ANY post to ANY
  verified student by id regardless of feed scope — attachment downloads
  inherit that rule (an unverified student is still correctly blocked from
  non-NATIONAL attachments, tested); feed scoping belongs to whoever owns
  `getPost`. (b) `GlobalExceptionHandler`'s 413 message says "10 MB limit"
  even for a 2 MB photo rejected by the multipart cap — cosmetic.
- No secrets, credentials or PII were added anywhere. No new dependency.
  `ExifStripper` is pure JDK; the only system packages touched during this
  work were the toolchain restore described in §5 (test-time only, not shipped).

## 4. Tests added / changed
- NEW `backend/src/test/java/com/unilink/storage/ExifStripperTest.java` — 9 tests.
- NEW `backend/src/test/java/com/unilink/integration/UploadSecurityTest.java` — 15 tests
  (H2 `@SpringBootTest`, real services + real local storage).
- EXTENDED `backend/src/test/java/com/unilink/storage/UploadValidatorTest.java` — 12 tests total.
- EXTENDED `backend/src/test/java/com/unilink/storage/LocalStorageAdapterTest.java` — 5 tests total.
- Every fix has a proving test: wrong owner (add/delete/download), wrong type
  (GIF/text/PDF-as-photo), oversized (photo/cover/attachment), boundary sizes
  (exact cap passes), traversal filename (photo + attachment), EXIF present
  (JPEG/PNG/WebP on photo/cover/ID-card/register/resubmit/post/chat), over-limit
  attachments (6th → 400, no orphan), deleted-post attachments blocked,
  replacement deletes the old file, one-attachment-per-message.

## 5. Test results
- Toolchain (restored after a VM replacement wiped it mid-task — see below):
  Java 21.0.13, Maven 3.8.7, `~/.m2` cache intact, offline mode
  (`-o -s /home/hatch/.m2/settings.xml`). `mvn clean` cannot run offline
  (maven-clean-plugin 3.3.2 is not in the local cache), so `target/` was
  removed manually before the full runs.
- Targeted re-run after the final fix: **41/41 green, BUILD SUCCESS**
  (`ExifStripperTest` 9, `UploadValidatorTest` 12, `LocalStorageAdapterTest` 5,
  `UploadSecurityTest` 15). Log: `~/workspace/mvn-worker-c-retest.log`.
- Full backend suite, first run (2026-11-17 ~17:44–17:56 +06): **210 tests, 202 pass,
  5 failures, 3 errors**. My tests: 41/41 pass except one ExifStripper error
  (off-by-one in the standalone-marker branch, fixed; see the targeted re-run
  above). The other 7 failures were in other workers' in-progress code, not
  mine: `OcrUniversityScoreTest` 5/5 (OCR university-name scoring — expected
  1.0, got 0.75), `Phase3WorkerDTest` 1 error (MySQL-only `DATE_SUB` SQL in
  `AdminService.analytics` under H2), `WorkerDControllerGuardsTest` 1 error
  (Mockito cannot mock `NotificationService`).
- Full backend suite, second run (2026-11-17 ~18:00 +06, after the fix, log
  `~/workspace/mvn-worker-c-full2.log`): **211 tests, 0 failures, 0 errors,
  BUILD SUCCESS**. The other workers had fixed their 7 failures in the
  meantime (and one new test appeared, 210 → 211). So the final state is a
  fully green suite including all 41 of Worker C's tests.
- **Not verified: no live MySQL.** The sandbox blocks Java→MySQL (unchanged);
  all verification is H2 unit/integration tests. No screen or workflow ran
  against a live backend. No Flyway run.

## 6. Work outside my brief that I had to touch to get a green tree
The shared checkout held other workers' in-progress, uncompilable code when
this work started, and the tree was in flux throughout (files appearing and
being rewritten mid-run, a VM replacement mid-task). To be able to compile and
test at all I made three minimal repairs to other workers' files — each is
obviously correct, preserves the other worker's intent, and must be reviewed
by the coordinator (do not silently attribute them to Worker C's scope):
- `notification/NotificationEventListener.java`: a worker had added new
  `onVerified`/`onRejected` methods (I-11 decision email) without removing the
  old ones — duplicate methods, whole-compilation Lombok cascade. Deleted the
  old duplicates, kept the new versions verbatim.
- `admin/AdminDtos.java`: a worker's new `IdentityChangeRequestDto` declared
  `newDepartmentId` as `String`; the entity field is `Long`. Changed the
  record component to `Long`.
- `admin/AdminAuthService.java`: `isRecoveryCodeExpired` was package-private
  while another worker's test calls it from another package. Made it `public
  static` (one word).
- `integration/Phase3WorkerDTest.java`: at one point it called service
  signatures that did not exist (9-arg `createPost`, etc.) and did not compile;
  Worker D later rewrote it to the real signatures. I temporarily moved it
  aside once to run the suite; the copy in `/tmp` was lost in the VM restart,
  but Worker D had re-saved a newer version at the original path, so no work
  of theirs was lost. No repair of mine was needed in the end.
- Toolchain restore (test-time only, NOT a runtime dependency — per the
  coordinator's note): the VM replacement wiped Java/Maven. My `sudo apt-get
  install openjdk-21-jdk maven` NEVER succeeded (package lists empty;
  `apt-get update` was blocked by the platform's own os-intent replay apt-get,
  pid 805, restoring previously installed packages). The working toolchain
  came from that platform replay (files under `/usr/lib/jvm`,
  `/usr/share/maven`); I use it via explicit `JAVA_HOME`/`PATH` because the
  `/usr/bin` symlinks are not on PATH. Nothing was installed by me, no app
  dependency was added, and the EXIF implementation uses only the JDK.
