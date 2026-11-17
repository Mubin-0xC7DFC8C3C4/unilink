# Phase 3, Section E — Sole Platform Admin recovery runbook (DESIGN ONLY)

Status: design document, 2026-11-17. No production code, no endpoint, no
config change, no live MySQL verification. This runbook is a procedure for
people to follow with the tools that already exist; it adds no new code
path and bypasses no authentication control in the application.

## 1. The problem

The sole Platform Admin has lost **both** second factors:

- the authenticator app (TOTP secret), and
- all 10 recovery codes.

What still works in this scenario, and what does not:

- The admin **still knows their password**. Password login succeeds, but
  the application will not issue an MFA-verified session.
- There is **no in-code recovery path** (Phase 2 verification report
  `06-phase2-verification.md`, Notes; D-068 recovery OPEN). The only
  existing credential-reset path,
  `POST /api/admin/moderators/{id}/reset-credentials`
  (`AdminService.resetModeratorCredentials`), requires an authenticated,
  MFA-verified Platform Admin as the caller — so the locked-out sole
  admin cannot invoke it on themselves, and no other admin exists to
  invoke it for them.
- Email OTP cannot help: `AdminAuthService.verifyEmailOtp` refuses a
  Platform Admin outright (`"Email OTP alone never satisfies 2FA for a
  Platform Admin (D-068)"`, D-078). A moderator's email-OTP fallback
  never applies to this role.
- Re-enrolment is blocked from inside the application:
  `AdminAuthService.enrolTotp` throws `ALREADY_ENROLLED` while
  `totpEnrolled` is true, and the only way to clear that flag in code
  is the reset above.
- TOTP guessing is throttled (`admin-totp:<adminId>`, 5 failures →
  15-minute lock, 429). The break-glass below must **not** be used to
  evade that throttle: if the lock is active, wait it out first.

Scope of this runbook: **MFA loss only, password still known.** Full
credential loss (password also lost or possibly compromised) is a
different, rarer scenario — see §9. If the password is also unknown,
stop: the main procedure cannot restore access.

## 2. Recommendation

**Recommended: (a) break-glass via a deployment-owner-controlled
database/console action, with dual control and full audit.**

The operator performs, by direct database action, exactly the MFA
part of what `resetModeratorCredentials` already does in code —
clear the stale TOTP secret, clear the dead recovery codes, set
`totp_enrolled = false`, invalidate the admin's sessions — and then
the admin re-enrols through the normal, already-tested login flow
(§6). No password is reset in the main path, because none needs to
be: the admin still knows it, and resetting it from a console would
require manufacturing an Argon2id hash by hand and distributing a
temporary password — new risks for zero benefit (§9 covers the
exception).

Why (a) over the alternatives (compared in full in §8):

- **No standing secret.** Nothing privileged exists before the
  incident, so there is nothing to steal, lose, forget to rotate, or
  discover has silently expired. The capability (database write
  access) already exists implicitly — anyone with it could already do
  this; the runbook formalises it with approval and audit instead of
  pretending it does not exist.
- **Reuses tested behaviour.** The post-reset state is identical to
  the state the moderator-reset path produces, and the re-enrolment
  flow (`NEEDS_ENROLMENT` → `POST /api/admin/auth/totp/enrol` →
  fresh recovery codes) is exercised by existing tests.
- **Works with zero prior preparation** beyond naming two people (§3).
  A sealed credential that was never created, or was created and then
  lost, helps nobody at 2 a.m.
- **Smallest blast radius.** The action touches one admin's MFA rows
  only. It does not create an account, change any password, touch any
  student data, or alter any other admin.

Its honest weakness: dual control and audit are **procedural, not
technical**. A deployment owner with sole database access can
technically act alone; the controls make misuse attributable and
visible, they cannot physically prevent it. That is accepted here
because the alternative (a standing privileged credential) has a
strictly worse failure mode — silent compromise with no expiry — and
because database access already implies full control of the system.
§7 states what remains true when there is no second trusted person.

## 3. Roles and preconditions (set up at deploy time)

Name these people **before** an incident, in the deployment's private
operations record (not in the code repository, not in any chat log).
The runbook cannot be executed safely if the names are decided during
the incident.

| Role | Who | Holds |
|---|---|---|
| **Requester** | The locked-out Platform Admin | Knows the account password; proves identity out of band (§4). |
| **Executor** | Deployment Owner | Database / console access to the production MySQL instance and the application host. Executes §5. Must not be the Requester acting alone — see dual control below. |
| **Authoriser** | A named senior person independent of the Requester (e.g. head of IT, registrar's nominee, organisation director) | No database access required. Verifies the Requester's identity and countersigns the recovery. Must be a different person from the Requester. |

**Dual control rule.** No single person may be both Requester and
Authoriser, and the Executor may not execute without the Authoriser's
signed approval for that specific incident. The usual shape is:
Requester asks → Authoriser verifies identity and approves →
Executor executes while the Requester is present to re-enrol
immediately. Where the Deployment Owner *is* the locked-out admin
(common in a small deployment), the Executor step is still performed
by them, but only after the Authoriser's approval — the control then
rests on the approval plus the audit trail, and that limitation must
be recorded in the incident record (§4).

**Preconditions — all must hold before §5:**

1. The two role-holders above are named and contactable, with
   pre-registered contact details (phone, email) on file.
2. There is exactly one active Platform Admin, and it is the
   Requester's account. (If a second Platform Admin exists, **stop**:
   use the normal in-application reset — the peer Platform Admin
   calls `POST /api/admin/moderators/{id}/reset-credentials` and
   hands over the temporary password once. This runbook is only for
   the sole-admin case.)
3. The Requester knows the account password. If not, see §9.
4. The Requester is available **now** to re-enrol in the same sitting
   (§6). The reset opens a password-only enrolment window (§5,
   warning), so it must never be executed "in advance" while the
   Requester is away.
5. The Executor has working database access and a backup taken before
   the change (a routine pre-change backup, not a restore — nothing
   is being deleted except the dead MFA rows).
6. Prevention policy (§10): at deploy time the organisation must
   already have been told that a sole Platform Admin is a known
   single point of failure. After every recovery, a second Platform
   Admin is created (§6, step 6) so this runbook is not needed again.

## 4. Trigger and identity verification

1. **Request.** The Requester contacts the Authoriser (and the
   Executor) through a pre-registered channel — phone call or in
   person, not only email — stating: account email, what was lost
   (authenticator device, recovery codes), and when it was lost.
   The request is written down as an incident record with a ticket
   reference, date/time, and the names of all three roles. The
   incident record lives **outside** the UniLink database (deployment
   owner's operations log / ticket system).
2. **Identity check (Authoriser).** The Authoriser verifies the
   Requester is who they claim to be, using at least two of:
   pre-registered phone number (call back on the number on file, do
   not trust an incoming call), pre-registered email, government or
   university photo ID shown on a video call or in person, or
   confirmation by the Requester's line manager / HR. A request
   arriving only by email, or from an unregistered address, is
   refused.
3. **Approval.** The Authoriser signs the incident record (written
   approval: signed note, ticket comment from their own account, or
   countersigned email — not a forwarded message). The approval
   names the target account email, the Executor, and the ticket
   reference. **An approval is valid for 24 hours and for one
   execution only.** If §5 is not executed within 24 hours, the
   approval lapses and the Authoriser must re-approve.
4. **No-approval, no-execution.** The Executor refuses to act on a
   verbal-only approval, on an approval from the Requester
   themselves, or on an expired approval.

## 5. Break-glass procedure (Executor)

Execute against the production database, in one sitting, with the
Requester present (on a call or in person) to re-enrol immediately
afterwards. `<admin_id>` is the `id` of the locked-out admin's row in
`admin_accounts`; `<email>` is their account email.

**Step 0 — Confirm the target (read-only).**

```sql
SELECT id, email, role, totp_enrolled, active
FROM admin_accounts WHERE email = '<email>';
```

Proceed only if exactly one row returns, `role = 'PLATFORM_ADMIN'`,
`totp_enrolled = 1`, `active = 1`. If `totp_enrolled = 0` the account
is not in the locked-out state this runbook covers — stop and
investigate (the admin may simply need the normal enrolment flow).
Confirm there is no other active `PLATFORM_ADMIN` row:

```sql
SELECT id, email FROM admin_accounts
WHERE role = 'PLATFORM_ADMIN' AND active = 1;
```

**Step 1 — Clear the dead MFA state (one transaction).**
This mirrors `AdminService.resetModeratorCredentials` minus the
password change (the password is not lost, so it is not touched),
and `SessionService.invalidateAllSessions` (which deletes the
admin's Spring Session rows by principal name — here the admin's
email — from `SPRING_SESSION`; `SPRING_SESSION_ATTRIBUTES` rows
follow via `ON DELETE CASCADE`, V007).

```sql
START TRANSACTION;
DELETE FROM admin_totp_secrets   WHERE admin_id = <admin_id>;
DELETE FROM admin_recovery_codes WHERE admin_id = <admin_id>;
UPDATE admin_accounts SET totp_enrolled = 0 WHERE id = <admin_id>;
DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = '<email>';
COMMIT;
```

Do **not** delete the `admin_accounts` row, do not touch any other
table, and do not change the password hash. Deleting the row would
orphan the audit history (`audit_logs.actor_admin_id` references
`admin_accounts(id) ON DELETE RESTRICT`, V006) and would not help
anyway: `AdminBootstrap` only creates a first admin when **no**
admin accounts exist at all (D-068), so it cannot recreate one
account among others.

**Step 2 — Write the audit row (same transaction discipline, last).**
The application normally writes audit rows through
`AuditService.write`, which chains them: each row stores the previous
row's `row_hash` as `prev_hash`, and
`row_hash = SHA-256 hex of
prev_hash + "|" + action + "|" + target_type + "|" + target_id + "|"
+ actor_admin_id + "|" + reason + "|" + created_at-string`
(`Hashing.sha256`, lowercase hex of the UTF-8 bytes). A console
action cannot call that service, so the Executor inserts the row by
hand, reading `prev_hash` at insert time with a single
`INSERT ... SELECT` so the chain is captured as late as possible.
`actor_admin_id` is the recovered admin's own id (the foreign key
requires an existing `admin_accounts` row or NULL; the actor here is
the account being recovered, and the real people are named in
`metadata`). Never put a secret, a password, or a TOTP value in any
audit field.

```sql
INSERT INTO audit_logs
  (actor_admin_id, action, target_type, target_id, reason, metadata, ip, prev_hash, row_hash, created_at)
SELECT
  <admin_id>,
  'ADMIN_BREAK_GLASS_RESET',
  'ADMIN',
  <admin_id>,
  'Break-glass MFA reset: sole Platform Admin lost authenticator + recovery codes',
  JSON_OBJECT(
    'ticket', '<ticket-reference>',
    'requester', '<requester name / account email>',
    'approver', '<authoriser name>',
    'executor', '<executor name>',
    'method', 'db-console',
    'passwordReset', false),
  '<executor host/IP>',
  COALESCE((SELECT row_hash FROM audit_logs ORDER BY id DESC LIMIT 1), 'GENESIS'),
  LOWER(HEX(SHA2(CONCAT(
    COALESCE((SELECT row_hash FROM audit_logs ORDER BY id DESC LIMIT 1), 'GENESIS'),
    '|ADMIN_BREAK_GLASS_RESET|ADMIN|', <admin_id>, '|', <admin_id>,
    '|Break-glass MFA reset: sole Platform Admin lost authenticator + recovery codes|',
    DATE_FORMAT(NOW(3), '%Y-%m-%dT%H:%i:%s.%f')), 256))),
  NOW(3);
```

Notes on this statement: `SHA2(..., 256)` in MySQL returns the same
lowercase hex as `Hashing.sha256`. The `created_at` string used in
the hash is the `DATE_FORMAT(NOW(3), ...)` value; the Executor must
copy that exact string into the incident record so the hash can be
recomputed forensically later. (`AuditService.verifyChain` checks
only the `prev_hash` linkage, not a recomputation of `row_hash`, so
a linkage check alone does not prove the hash input — the recorded
string is what makes the row independently verifiable.) The
`audit_logs` table is append-only (D-037: `BEFORE UPDATE`/`DELETE`
triggers reject changes, and the runtime database user has
`INSERT, SELECT` only), so this insert is possible with existing
least-privilege credentials and can never be used to rewrite history.

Race note: if another audit row is written by the running
application between the Executor's `SELECT` of `prev_hash` and this
insert, the chain breaks at this row. Reduce the risk by executing
during a quiet period and by inserting the audit row **last**,
immediately after Step 1. After §6, verify the chain (§6, step 5);
if it is broken at this row, record the break, its cause, and the
affected row id in the incident record — do not attempt to repair it
by updating the row (the trigger forbids it, correctly).

**Step 3 — Verify the reset took effect (read-only).**

```sql
SELECT totp_enrolled FROM admin_accounts WHERE id = <admin_id>;
-- expect 0
SELECT COUNT(*) FROM admin_totp_secrets   WHERE admin_id = <admin_id>;
-- expect 0
SELECT COUNT(*) FROM admin_recovery_codes WHERE admin_id = <admin_id> AND used = 0;
-- expect 0
SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = '<email>';
-- expect 0
```

If any check fails, stop, keep the incident record open, and
investigate before the Requester tries to log in.

> **Warning — the enrolment window.** From the moment Step 1
> commits until the Requester completes §6, the account is in
> `NEEDS_ENROLMENT`: anyone who knows the password can enrol a
> fresh authenticator and take the account. That is why §3
> precondition 4 exists (Requester present, re-enrol immediately)
> and why the Executor stays with the Requester until the
> `ADMIN_TOTP_ENROLLED` audit row is confirmed (§6, step 4). Never
> execute this procedure "to be used later".

## 6. Re-enrolment (Requester, immediately, same sitting)

The recovered admin re-enrols through the ordinary login flow —
no special path, no bypass:

1. `POST /api/admin/auth/login` with the (unchanged) email and
   password. `AdminAuthService.login` sees `totpEnrolled = false`,
   generates a fresh TOTP secret (AES-256-GCM encrypted at rest),
   and returns `stage: "NEEDS_ENROLMENT"` with an `otpauthUrl`.
   (Audit: `ADMIN_LOGIN_PASSWORD_OK`.)
2. Scan the `otpauthUrl` into the authenticator app on the
   Requester's **new** device. Do not restore it from a backup of
   the lost device unless that backup is known-good and private.
3. `POST /api/admin/auth/totp/enrol {code}` with a code from the new
   authenticator. Enrolment only activates after the code verifies
   (`AdminAuthService.enrolTotp`). The response returns the **10 new
   single-use recovery codes, displayed once**. (Audit:
   `ADMIN_TOTP_ENROLLED`; the old codes were already deleted by
   Step 1, and enrolment deletes any remainder.)
4. **Store the new recovery codes immediately, separately from the
   authenticator device** — e.g. the organisation's password manager
   and one printed copy in a sealed envelope held by the Authoriser
   or in the office safe. A recovery code stored on the same phone
   as the authenticator is lost in the same event. Confirm the
   codes are recorded before logging out: there is no way to display
   them again.
5. `POST /api/admin/auth/totp/verify {code}` with a fresh TOTP code
   to confirm the MFA-verified session works. (Audit:
   `ADMIN_LOGIN_OK` with `mfa: "totp"`.)
6. **Create a second Platform Admin now** (Admin Portal →
   moderators → create, role `PLATFORM_ADMIN`, via the normal
   creation path) and have that person enrol TOTP at first login.
   This is the prevention that makes this runbook unnecessary next
   time (§10). Do not defer it.
7. Executor confirms in the incident record: the
   `ADMIN_BREAK_GLASS_RESET` row id, the `ADMIN_TOTP_ENROLLED` row
   id, and the time enrolment completed. Target: enrolment complete
   within **1 hour** of Step 1; hard limit **24 hours** — if
   enrolment is not complete within 24 hours, treat it as an open
   security incident (the password-only window is still open) and
   escalate to the Authoriser; the account must not be left in
   `NEEDS_ENROLMENT` indefinitely.

## 7. Audit requirements (what is logged, and where)

| Record | Where | Contents |
|---|---|---|
| Incident record (request + identity evidence + approval + execution notes + verification results) | Deployment owner's operations log / ticket system, **outside** the UniLink database | Ticket reference, timestamps, all three names, how identity was verified (§4), approval, the exact `created_at` string used in the audit-row hash, the audit row id, enrolment completion time. Retained at least 1 year (same bar as D-037). |
| `ADMIN_BREAK_GLASS_RESET` row | `audit_logs` (hash-chained, append-only) | `actor_admin_id` = recovered admin id, `target_type` `ADMIN`, `target_id` = admin id, reason as in §5, `metadata` JSON with ticket / requester / approver / executor / `method: "db-console"` / `passwordReset: false`, Executor host/IP. No secrets. |
| Login / enrolment rows | `audit_logs`, written by the application | `ADMIN_LOGIN_PASSWORD_OK`, `ADMIN_TOTP_ENROLLED`, `ADMIN_LOGIN_OK` (`mfa: "totp"`) — the in-application proof that recovery completed. |
| Application log | Application host log | The `AUDIT action=...` lines `AuditService` already emits for the rows above. Nothing extra is logged from the console steps. |

**Post-recovery verification (Executor, within 1 business day):**
run the audit-chain check (`AuditService.verifyChain` — it returns
the first broken row id, or null) and export the audit CSV
(`GET /api/admin/audit-logs/export`) covering the incident window;
file the export with the incident record. Confirm the three expected
rows exist and that no other admin-credential rows appeared during
the window. Any chain break at the break-glass row is recorded with
its cause (§5, race note) and is not silently repaired.

## 8. Revocation / closure (after use)

The break-glass path is a procedure, not a standing credential, so
there is nothing to "disable" afterwards — but closure is still a
checklist, and the incident stays open until every item passes:

1. Enrolment confirmed (§6, steps 4–5) and the new recovery codes
   stored in two separate places.
2. Old TOTP secret and old recovery codes confirmed gone (§5,
   Step 3 re-run — all counts 0; enrolment also deletes any
   remainder).
3. All pre-reset sessions confirmed invalidated (§5, Step 3;
   any session created before the reset is dead).
4. Audit chain verified and CSV export filed (§7).
5. Second Platform Admin created and TOTP-enrolled (§6, step 6).
   If this cannot be done the same day, the incident record states
   why and a dated follow-up is opened — the deployment remains a
   sole-admin deployment until it is done.
6. Any database/console credentials that were shared, displayed, or
   used from an unfamiliar machine during the procedure are rotated.
7. The approval is spent: it cannot authorise a second execution.
   A further incident needs a fresh request and approval.
8. Incident record closed with: request / approval / execution /
   enrolment timestamps, row ids, participants, and the chain-check
   result. The closed record is the evidence that the recovery was
   authorised, executed once, and completed.

## 9. What happens when the password is also lost

The main procedure cannot help: login requires the password, and no
in-application admin password reset exists for a caller who cannot
authenticate. In that case, and only in that case, the Executor may
add a password reset to the same break-glass, under the same
approval and audit, by setting a fresh Argon2id hash:

- Generate a temporary password (same shape as the in-code reset:
  12 random hex characters + `"A1"`, `AdminService`
  `resetModeratorCredentials`).
- Compute its Argon2id hash **offline, with the production
  parameters (D-014: 19 MiB memory, 2 iterations, 1 lane)** using a
  standard Argon2 tool, and **verify the hash against the temporary
  password with the same tool before running any `UPDATE`**. Store
  it in `admin_accounts.password_hash` in the DelegatingPasswordEncoder
  form the application writes (prefix included, as produced by the
  application's own encoder).
- Deliver the temporary password to the Requester out of band
  (verbally or via the organisation's approved secure channel),
  exactly once; it is never written in the incident record, the
  ticket, email, chat, or any log.
- Add `"passwordReset": true` to the break-glass audit row's
  metadata.
- The Requester logs in with the temporary password, re-enrols (§6),
  and then — at the first opportunity once a second Platform Admin
  exists or an admin password-change endpoint is available — sets a
  new password of their own choosing. Until then the temporary
  password remains valid, so treat it as compromised after first
  use and complete the rotation promptly; record it as an open
  follow-up in the incident record.

This is an escalation, not the default: it adds a temporary-password
distribution risk and a hand-computed hash step, so it is used only
when the password is genuinely lost or possibly compromised (e.g.
the lost device also held the password).

## 10. Prevention (so this runbook is never needed)

- **Maintain at least two active Platform Admins at all times.**
  The normal moderator-reset path works peer-to-peer and needs no
  break-glass. Creating the second admin is step 6 of every
  recovery and a deploy-time requirement, not a nice-to-have.
- **Recovery codes stored separately from the authenticator.**
  The loss that triggers this runbook is losing *both* factors
  together — usually because the codes lived on the same device.
  Store them in the password manager and on paper, in different
  places (§6, step 4).
- **Drill.** Once a year, the Deployment Owner and Authoriser walk
  through §4–§5 on a non-production copy and confirm the role
  names, contact details, and database access still work. A drill
  never executes Step 1 against production.
- **Known gaps this runbook does not fix** (recorded for Phase 3
  follow-up, not silently assumed away): there is no admin
  password-change endpoint, so a recovered admin cannot rotate
  their own password (Phase 2 report, Notes); recovery codes are
  unsalted SHA-256 and never expire (Phase 2 report, Notes) — a
  stolen code stays valid until used or until re-enrolment deletes
  it, which is another reason the codes must be stored separately
  and the enrolment window kept short.

## 11. Rejected alternatives (with reasons)

### (b) Pre-provisioned sealed recovery credential (sealed envelope / vault-stored one-time credential) — REJECTED as the primary mechanism

The idea: at deploy time, create a spare credential — e.g. an extra
set of recovery codes, or a second admin account's password — seal
it in an envelope or a vault, and open it on lockout.

- A **standing secret** that must be created, stored, rotated, and
  kept intact for months or years, with no signal when it fails.
  If the envelope is lost, damaged, or was never created, the
  lockout is discovered to be unrecoverable at the worst moment.
  If it is stolen, the thief holds a silent MFA bypass — recovery
  codes never expire, so a stolen sealed set stays valid until used.
- It cannot be drilled without consuming it (recovery codes are
  single-use), so each drill burns the credential and requires
  re-issuing and re-sealing — operational burden that guarantees it
  will not be drilled.
- A vault-stored spare **admin account** is worse: it is a second
  Platform Admin that nobody monitors, whose TOTP is unenrolled or
  whose password never rotates.
- Sealing the *procedure authorisation* (who may approve) is useful
  and is adopted in §3; sealing a *credential* is not. The envelope
  that §6 step 4 asks for holds the admin's own current recovery
  codes as a backup copy — that is storage of an existing factor,
  not a second standing credential, and it is the admin's
  responsibility, not the recovery mechanism.

### (c) Standing second Platform Admin as the recovery mechanism — ADOPTED as prevention, not as recovery

Having two Platform Admins means the peer can reset the locked-out
admin through the application (§3 precondition 2). That is strictly
better than any break-glass and is required policy (§10). But it is
prevention, not a recovery procedure: it does not answer the
question asked — what to do when the *only* Platform Admin is
locked out — and it fails the moment the second admin leaves, is
deactivated, or loses their own factors. The runbook still needs to
exist underneath it.

### (d) Escrowed / split TOTP secret (e.g. Shamir-split secret or escrowed encryption key) — REJECTED

Splitting the TOTP secret or the `app.encryption-key` between two
holders would let the pair reconstruct the second factor without
database access. It adds key-management machinery (share creation,
storage, rotation on every re-enrolment, revocation) that the team
does not operate today, and the escrowed material is again a
standing secret — whoever holds enough shares *is* the second
factor. It also couples recovery to the encryption key, whose
compromise would expose every admin's TOTP secret at once. More
moving parts, worse blast radius, no benefit over (a).

### (e) Email-OTP exception for the sole Platform Admin — REJECTED

Allowing email OTP alone to satisfy 2FA "just this once" would
directly contradict D-078 (email OTP alone NEVER satisfies 2FA for
a Platform Admin), adopted precisely because the admin's email is
the weaker factor: an email compromise would then be a full
Platform Admin takeover with no second factor at all. An exception
process that silently re-opens the exact hole the rule closed is
not a recovery mechanism.

### (f) Delete the admin row and re-trigger bootstrap — REJECTED

`AdminBootstrap` only runs when **no** admin accounts exist, so
recovering one admin this way requires deleting the sole row — and
the delete itself fails while audit rows reference it
(`fk_audit_actor ... ON DELETE RESTRICT`, V006). Even if it
succeeded, the new account would get a new id, orphaning the audit
history and the admin's identity, and the bootstrap password would
arrive via environment variable with no dual control or audit row.
Destructive, unaudited, and unnecessary: §5 achieves the same
result without deleting anything.

### (g) Unauthenticated reset endpoint, backdoor password, or disabling TOTP — REJECTED, never to be built

Any endpoint that resets admin credentials without authentication,
any shared password compiled into the code, any "maintenance mode"
that skips MFA, and any global TOTP disable flag would hand every
future attacker the same path the runbook gives the owner — but
without the approval, the audit, or the expiry. These are not
trade-offs; they are vulnerabilities. No such code path exists, and
none may be added as a "convenience" for recovery.

## 12. What must NEVER be done

- Never add a shared backdoor password, a hardcoded credential, an
  unauthenticated reset endpoint, or a flag/config option that
  bypasses authentication — in code, in config, or "temporarily".
- Never disable TOTP globally, and never grant an email-OTP
  exception for a Platform Admin (D-078).
- Never delete the `admin_accounts` row to re-trigger bootstrap
  (§11f).
- Never reset the password in the main path, never write a temporary
  password in a ticket, email, chat message, or log, and never send
  it over the same channel as the approval.
- Never execute without the Authoriser's signed, unexpired approval
  for that incident — including when the Executor is the Requester.
- Never execute while the Requester is unavailable to re-enrol
  immediately; never leave the account in `NEEDS_ENROLMENT`
  overnight without treating it as an open incident.
- Never skip the audit row, and never "repair" the audit chain by
  updating or deleting a row (D-037 triggers forbid it).
- Never reuse an approval, and never treat a successful recovery as
  a reason to skip creating the second Platform Admin.

## 13. Single-person deployment (no second trusted person)

If the organisation has **no second trusted person** — one person
is the sole Platform Admin, the Deployment Owner, and the only
operator — true dual control is impossible, and this runbook says so
plainly instead of pretending otherwise. A person with sole database
access can always act alone; no procedure can physically prevent
that. The minimum viable control is **detection, delay, and audit**,
and the residual risk must be accepted in writing at deploy time:

1. **External identity verifier.** Name, in advance, one person
   *outside* the deployment — e.g. the university registrar, HR,
   or a named contact at the parent organisation — who can confirm
   the Requester's identity out of band before execution. They hold
   no access and give no technical approval; their role is to
   confirm the person, not the action.
2. **Pre-announcement + cooling-off.** Before executing, the
   Executor sends a written notice of the intended action (account,
   ticket reference, timestamp) to a pre-registered monitoring
   mailbox that the Executor does not solely control, and waits a
   minimum of **4 hours** before executing. A compromised actor can
   still act, but cannot act silently or instantly.
3. **Written self-authorisation filed first.** The request, the
   external verifier's confirmation, and the intended action are
   written into the incident record *before* any database change.
4. **Full audit as in §7**, plus the chain verification and CSV
   export filed with the incident record.
5. **Immediate second admin.** The first act after re-enrolment is
   creating a second Platform Admin (§6, step 6), so the deployment
   stops being single-person. If no second person can be found even
   then, the organisation records in writing that it accepts
   single-person operation with detection-only controls, and reviews
   that acceptance annually.

Minimum viable control, in one line: *announce it, wait, have
someone outside confirm the person, write everything down, audit
everything, and stop being single-person at the first opportunity.*

## 14. Open items / owner actions

- Owner to name the Executor and Authoriser (and their deputies)
  for each deployment, with pre-registered contact details (§3).
- Owner to confirm the single-person acceptance (§13) where it
  applies, in writing, reviewed annually.
- Follow-up code work (not this runbook): an admin password-change
  endpoint, so a recovered admin can rotate their own password
  without a peer (Phase 2 report, Notes); salted, expiring recovery
  codes (Phase 2 report, Notes). Neither blocks this procedure.
- D-068's recovery question is answered by this runbook for the
  sole-admin case; the remaining part of D-068 (TOTP design
  approval) is unchanged.

## 15. Sources grounded in

- `backend/src/main/java/com/unilink/admin/AdminAuthService.java` —
  login `NEEDS_ENROLMENT`/`NEEDS_TOTP` stages; `enrolTotp`
  (`ALREADY_ENROLLED`, 10 single-use SHA-256 recovery codes, old
  codes deleted); `verifyTotpOrRecovery`; `verifyEmailOtp` refusing
  Platform Admin; `admin-totp:<id>` throttle.
- `backend/src/main/java/com/unilink/admin/AdminService.java` —
  `resetModeratorCredentials` (Platform Admin only, temp password
  returned once, `totpEnrolled=false`, TOTP secret row deleted,
  sessions invalidated, `ADMIN_CREDENTIALS_RESET` audit); note that
  it requires an authenticated Platform Admin caller.
- `backend/src/main/java/com/unilink/admin/AdminBootstrap.java` —
  runs only when no admin accounts exist (`admins.count() > 0`
  returns); env `INITIAL_ADMIN_EMAIL` / `INITIAL_ADMIN_PASSWORD`
  (≥12 chars).
- `backend/src/main/java/com/unilink/admin/api/AdminAuthController.java`
  — `POST /api/admin/auth/login`, `/totp/enrol`, `/totp/verify`.
- `backend/src/main/java/com/unilink/audit/AuditService.java` —
  hash-chain formula (`prev_hash`, `row_hash`), `verifyChain`
  (linkage only), `AUDIT` log line.
- `backend/src/main/resources/db/migration/V003__admin.sql`,
  `V006__moderation_notifications_audit.sql`, `V007__*.sql` —
  `admin_accounts` / `admin_totp_secrets` /
  `admin_recovery_codes` tables; `audit_logs` append-only triggers
  and `fk_audit_actor ... ON DELETE RESTRICT`;
  `SPRING_SESSION.PRINCIPAL_NAME` with attributes cascading.
- `docs/phase-reports/06-phase2-verification.md` — sole Platform
  Admin lockout has no in-code recovery; recovery codes unsalted
  SHA-256, never expire; no admin password-change endpoint.
- `docs/phase-reports/05-phase2-api-contracts.md` — admin auth
  flows; moderator credential reset returns temp password once.
- `docs/requirements/REQUIREMENTS_DECISION_LOG.md` — D-031, D-037,
  D-068 (recovery OPEN), D-075, D-078.
