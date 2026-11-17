# Phase 3 — Worker C decisions (P1, file upload security)

Worker C, 2026-11-17. Entries D-104–D-107. These are written here (not in
`docs/requirements/REQUIREMENTS_DECISION_LOG.md`) because workers run
concurrently; the coordinator merges them into the log.

### D-104 EXIF stripping is dependency-free and covers every image upload path (ADOPTED)
- EXIF is stripped by a new dependency-free parser, `storage/ExifStripper`
  (JDK only, no new Maven dependency): JPEG drops APP1 segments whose payload
  starts with `Exif\0\0`; PNG drops `eXIf` chunks; WebP drops `EXIF` chunks,
  repairs the RIFF size and clears the VP8X EXIF-present flag. Pixel data is
  copied byte-for-byte, so OCR and rendering are unaffected.
- Applied on ALL server-side upload paths that accept images: sign-up ID card
  and profile photo (`AuthService.register`), resubmitted ID card
  (`AuthService.resubmit`), profile photo and cover
  (`ProfileService.uploadPhoto/uploadCover`), post attachments
  (`PostService.addPostAttachment`), chat attachments
  (`MessagingService.sendMessageWithAttachment`). PDFs pass through unchanged
  (no EXIF segment exists in PDF).
- Size is validated on the ORIGINAL bytes, then stripping runs, then the
  stripped bytes are stored; the stored `size_bytes` is the stripped length.
- A structurally malformed image is returned unchanged (fail-open): it already
  passed magic-byte sniffing, no decoder can read EXIF from an unparseable
  container, and rejecting it would change previously-accepted uploads.
- Residual: non-EXIF APP1 segments (e.g. XMP) and PNG `iTXt`/`zTXt` text chunks
  are kept — they can in principle carry location/creator text. Dropping all
  APP1 segments and all PNG text chunks is a follow-up, not done here, because
  D-029a specifies EXIF removal and XMP may carry copyright the owner wants.
- Already-stored images are NOT re-processed: only new uploads are stripped.
  ID images age out anyway under D-053 (7/30 days); a backfill for old profile
  photos/covers/attachments is a separate job if the owner wants it.
- The event-poster path in D-029a (5 MB) has no upload endpoint in the code, so
  there is nothing to strip there yet; when a poster upload is built it must
  route through `UploadValidator` + `ExifStripper` like the other paths.

### D-105 Per-post attachment cap is 5; per-message 1 by design; no per-room cumulative cap (ADOPTED)
- Max **5 attachments per post**, enforced server-side in
  `PostService.addPostAttachment` (count checked BEFORE storing, so a rejected
  6th file leaves no orphaned object); over the limit returns 400
  (`ATTACHMENT_LIMIT`). The number 5 matches the SRS's "a specific limit"
  (D-029a) and the report auto-hide threshold's order of magnitude; it is a
  constant (`UploadValidator.MAX_ATTACHMENTS_PER_POST`) so it is one edit to change.
- Per-message: exactly **1 attachment per message**, enforced structurally —
  there is no API that adds a second file to a message
  (`sendMessageWithAttachment` creates one message + one attachment). No
  separate numeric per-message cap is needed.
- Per-room/per-chat cumulative cap: **none**. A cumulative cap would break
  long-lived department/university rooms, which legitimately accumulate files
  over a term. Abuse is bounded by the per-file 10 MB cap, verified-membership
  checks on every send/download, and the per-message limit above. A per-user
  daily upload quota is noted as a follow-up, not built here.
- Known race: the count check and the store are not atomic, so two concurrent
  5th uploads could both pass and leave 6 attachments. Consequence is one
  extra file, not a security breach; fixing it would need a DB lock or
  unique-constraint trick disproportionate to the risk.

### D-106 Upload hardening: sniffing, file names, traversal, download headers (ADOPTED)
- Magic-byte sniffing governs; declared content type and file extension are
  never trusted. Tightened in this work: JPEG now requires a real marker code
  as the 4th byte (`FF D8 FF 00` is rejected); PNG requires the full 8-byte
  signature (a 4-byte `PNG` prefix is rejected); PDF is an attachment type,
  never an image. GIF/SVG/text remain rejected everywhere.
- `UploadValidator.sanitizeFileName` is applied to every client-supplied file
  name before storage and before it is echoed in a `Content-Disposition`
  header: directories are stripped (no traversal, no absolute path), quotes,
  backslashes and control characters are replaced (no header injection),
  leading dots are removed, length is capped at 200 chars, null/blank becomes
  `file`. The stored `file_name` is the sanitised name, not the raw upload name.
- `LocalStorageAdapter.size()` had no traversal guard (unlike
  store/open/exists/delete) and could leak an arbitrary file's size/existence
  via an absolute or escaping key; it now applies the same root-normalisation
  check and returns -1 outside the root. `store()` now also rejects a bad
  namespace and defaults a null file name to `file`.
- ID-card downloads (`GET /api/admin/verification/{id}/id-image`) used to
  always answer `image/jpeg` even for a PNG/WebP ID card; the content type is
  now sniffed from the stored bytes (same pattern `ProfileService.readImage`
  already used).
- `AuthService.register`/`resubmit` used to store ID cards and photos with a
  hardcoded `"image/jpeg"` content type regardless of the real type; the
  sniffed type is now stored.

### D-107 Attachment lifecycle: owner-only delete, soft-deleted posts stay sealed (ADOPTED)
- New endpoint `DELETE /api/posts/{id}/attachments/{attachmentId}` (owner
  only): the row and the stored object are both removed, so a deleted
  attachment leaves no orphaned file. A non-owner, or a wrong post id, gets
  404 (never a hint the attachment exists). There was previously no
  attachment-delete path at all; message attachments have none either, and
  need none — messages cannot be deleted, so their attachments have no
  lifecycle of their own.
- Profile photo/cover replacement is already owner-only by construction (the
  controller passes the session user's id; the key deleted is read from that
  user's own row) and already stores the new object BEFORE deleting the old
  one, so a failed store never loses the current photo. No change to the
  ordering was needed; the replacement now also strips EXIF (D-104) and
  sanitises the file name (D-106).
- `deletePost`/`deleteEvent` only soft-delete (status `REMOVED`); their
  attachment files are deliberately KEPT so a moderation RESTORE can bring
  the post back — but while the post is not `ACTIVE` its attachments are no
  longer listable or downloadable (previously they were: `getPost` does not
  check status, so a "deleted" post's files stayed reachable). This closes a
  real gap found in review.
- `AuthService.resubmit` does NOT delete the previous ID-card object when a
  new one is stored: the rejected attempt's `VerificationRequest` row still
  references it, and D-053 keeps rejected ID images for 30 days to support
  appeal. The `purgeIdImages` job deletes it when retention expires. This is
  intentional, not an orphan.
