# Phase 2 inventory: frontend ↔ backend API contract diff

- **HEAD:** `1f59a50` (verified `git rev-parse HEAD`).
- **Date:** 2026-11-17. **Author:** Phase 2 inventory worker (subagent).
- **⚠️ SNAPSHOT WARNING — the tree moved during this inventory.** A
  sibling fix agent was editing the working tree concurrently: at the
  start of this task `git status` showed 20 modified files; by the end
  it showed 33+ (including `V009__chat_room_closed_at.sql`, untracked).
  The counts and classifications in this report reflect the **working
  tree as re-verified at the end of the task**, not HEAD `1f59a50`.
  Items the sibling fixed mid-task are marked **[FIXED DURING
  INVENTORY]** in §3/§5 and collected in §10. Anything still marked
  MISMATCH/SHAPE below was still broken in the working tree at freeze
  time; re-run the §1/§2 greps before acting on a single row, because
  the sibling is still working.
- **Method:** static source audit only. Every frontend call was taken from
  `grep apiGet|apiPost|apiPut|apiPatch|apiDelete|fetch(` over
  `frontend/src` (plus `ListPage`/`AdminList` `path=` props, the raw
  `<img src>` in `Verifications.tsx` and the plain `<a href>` in
  `Audit.tsx`); every backend mapping from `grep` over
  `@*Mapping` in `backend/src/main/java`, with request/response DTOs read
  from the DTO and service source, never guessed. **No live verification:**
  Java processes cannot reach MySQL in this sandbox (see MEMORY.md), so
  all HTTP status predictions (400/404/422/500) are static inferences from
  the code, not observed responses. Where a runtime behaviour depends on a
  framework default, that is flagged explicitly.
- **Client conventions** (`frontend/src/api/client.ts`): every `api*`
  path is prefixed with `/api`; `credentials: 'include'` sends the
  `UL_SESSION` cookie; `X-XSRF-TOKEN` is sent when the cookie exists;
  `apiPost/apiPut/apiPatch` JSON-stringify the body (FormData passes
  through); a 204 returns `undefined`. `errorMessage()` maps 404 to
  *"That item was not found. It may have been removed, or the backend
  endpoint is not available yet."*
- **Auth chains** (`backend/.../config/SecurityConfig.java`): `@Order(1)`
  admin chain matches `/api/admin/**` — `/api/admin/auth/**` is
  `permitAll`, everything else requires authority `MFA_VERIFIED`;
  `@Order(2)` student chain matches `/api/**` + `/ws/**` —
  `/api/auth/**`, `/api/public/**`, `/api/csrf`,
  `/api/appeals/public` are `permitAll`, everything else
  `authenticated()`. CSRF is ignored for `/api/auth/**`,
  `/api/admin/auth/**`, `/api/public/**`, `/api/appeals/public`,
  `/actuator/**`, `/ws/**` (line 63).
- **Classification:** MATCH = path + method + body + response all agree.
  SHAPE = path + method right, body/params/response field(s) wrong (the
  exact field is named). MISMATCH = no backend route at that path/method.
  ORPHAN-BACKEND = backend route no frontend page calls.

## 0. Summary counts

| | Count |
|---|---|
| Frontend call operations inventoried (§1) | **76** (74 REST call sites + 2 STOMP ops) |
| MATCH | **36** |
| SHAPE | **17** |
| MISMATCH | **23** |
| Backend routes inventoried (§2 + §2.1) | **124** (123 REST + 1 STOMP) |
| Backend routes touched by ≥1 frontend call (correct path or not) | 51 |
| ORPHAN-BACKEND routes (no frontend caller at all) | **73** |

So **40 of 76 frontend operations (53%) do not cleanly work** as written,
and **59% of the backend surface is never called**. The audit-03
headline ("~50 paths: ~20 match, ~5 partial, ~30 mismatch", §4.10) is
out of date: the real denominator is 76 operations, and several items its
"matches cleanly" list names are in fact wrong — see §7. (At the start
of this task, before the sibling's mid-task fixes, the counts were
74 ops: 23 MATCH / 14 SHAPE / 37 MISMATCH, with 104 backend routes and
69 orphans. The §10 list explains the delta.)

## 1. Frontend call inventory (all 74 operations)

Paths are as written in the source, i.e. **before** the `/api` prefix in
`client.ts`. `F##` ids are used in §3.

| ID | File:line | Method | Path as written | Body sent | What the page does with the response |
|---|---|---|---|---|---|
| F01 | `app/Layout.tsx:7` | POST | `/auth/logout` | — | Ignores result; clears user, nav `/login` |
| F02 | `features/auth/SignUp.tsx:25` | POST | `/auth/register` | FormData: `fullName,phone,email,universityId,departmentId,batchId?,address,studentId,password` + files `idCard`,`profilePhoto` | Reads `id` from `UserDto`; stores it in `sessionStorage` (`unilink:otpUserId`) for the OTP step; nav `/otp` **[FIXED DURING INVENTORY]** |
| F02a | `features/auth/SignUp.tsx:15` | GET | `/public/universities` | — | Populates the university `<select>` **[new call, added during inventory]** |
| F02b | `features/auth/SignUp.tsx:16` | GET | `/public/departments?universityId=` | — | Populates the department `<select>` **[new call]** |
| F02c | `features/auth/SignUp.tsx:17` | GET | `/public/batches?universityId=&departmentId?` | — | Populates the batch `<select>` **[new call]** |
| F03 | `features/auth/Login.tsx:7` | POST | `/auth/login` | `{email,password}` | Reads `fullName,email,state`; `setUser` (incl. `verified: state==='VERIFIED'`), nav `/feed` |
| F04 | `features/auth/OtpStep.tsx:12` | POST | `/auth/otp/resend` | `{userId}` | Reads `status,channel`; 60s cooldown on success **[FIXED DURING INVENTORY — was `/auth/otp/send {phone}`]** |
| F05 | `features/auth/OtpStep.tsx:13` | POST | `/auth/verify-phone` | `{userId, otp: code}` (`userId` from `sessionStorage`) | Clears stored id; nav `/pending` **[FIXED DURING INVENTORY — was `/auth/otp/verify {phone,code}`]** |
| F06 | `features/auth/ForgotPassword.tsx:9` | POST | `/auth/password-reset/request` | `{email}` | Step 1→2 on success **[FIXED DURING INVENTORY — was `/auth/password/forgot`]** |
| F07 | — (removed) | — | — | — | The old 3-step wizard's middle step (`POST /auth/password/verify-otp`) was deleted during the inventory; the flow is now 2-step (F06 → F08). |
| F08 | `features/auth/ForgotPassword.tsx:10` | POST | `/auth/password-reset/confirm` | `{email, otp: code, newPassword}` | Step 2→3 ("password has been reset") **[FIXED DURING INVENTORY — was `/auth/password/reset {email,code,newPassword}`]** |
| F09 | `features/auth/EmailVerify.tsx:4` | GET | `/auth/verify-email?token=` | — | Sets state `ok` on any 2xx; ignores body **[FIXED DURING INVENTORY — was `/auth/email/verify`]** |
| F10 | `features/auth/Pending.tsx:4` | GET | `/auth/verification/status` | — | Reads `state`, `rejectionReason` |
| F11 | `features/auth/Pending.tsx:5` | POST | `/auth/verification/resubmit` | Empty `FormData()` (no file input on the page) | Shows "Resubmitted" on success |
| F12 | `features/feed/Feed.tsx:7` | GET | `/feed?level=&sector=&cursor=` | — | Reads `items[]`, `nextCursor`; renders `title,body,authorName,sector,commentCount` |
| F13 | `features/feed/Feed.tsx:9` | POST | `/posts` | `{title,body,sector?,level}` (`level` = feed level tab) | Invalidates `['feed']`; no success feedback |
| F14 | `features/posts/PostDetail.tsx:5` | GET | `/posts/{id}` | — | Reads `title,body` |
| F15 | `features/posts/PostDetail.tsx:6` | GET | `/posts/{id}/comments` | — | Reads `id,body,authorName` per comment |
| F16 | `features/posts/PostDetail.tsx:7` | POST | `/posts/{id}/comments` | `{body}` | Clears input; invalidates comments |
| F17 | `features/posts/PostDetail.tsx:8` | POST | `/posts/{id}/reactions` | `{type}` (`'LIKE'`) | No UI change on success |
| F18 | `features/posts/PostDetail.tsx:9` | POST | `/bookmarks` | `{targetType:'POST',targetId:id}` (id is a URL string) | Button flips to "Saved" on success |
| F19 | `features/posts/SavedPage.tsx:2` | GET | `/bookmarks` | — | Reads `id,targetId,title` |
| F20 | `features/events/EventsPage.tsx:7` | GET | `/events/upcoming` | — | Renders `title,type,deadline,visibility` |
| F21 | `features/events/EventsPage.tsx:7` | GET | `/events` | — | Same as F20 (past tab) |
| F22 | `features/events/EventsPage.tsx:6` | POST | `/events` | `{title,type,deadline,visibility}` (`type` = Seminar/Workshop/Competition/Hackathon; `deadline` = `yyyy-MM-dd` date-only string) | Invalidates `['events']` |
| F23 | `features/events/EventDetail.tsx:4` | GET | `/events/{id}` | — | Reads `type,title,description,deadline,visibility,regLink,prizes,eligibility` |
| F24 | `features/events/EventDetail.tsx:5` | POST | `/events/{id}/rsvp` | `{status}` (`'INTERESTED'`/`'GOING'`) | No UI change on success |
| F25 | `features/events/EventDetail.tsx:6` | POST | `/events/{id}/team-request` | `{lookingForTeammates:boolean}` | Shows "Team request sent." on success |
| F26 | `features/events/CalendarPage.tsx:4` | GET | `/calendar?month=yyyy-MM` | — | Reads `title,date,deadline` |
| F27 | `features/teams/TeamsPage.tsx:5` | GET | `/teams` | — | Reads `id,name,eventTitle,memberCount,pendingRequests[]` |
| F28 | `features/teams/TeamsPage.tsx:3` | POST | `/teams/{teamId}/join` | `{}` | Invalidates `['teams']` |
| F29 | `features/teams/TeamsPage.tsx:4` | POST | `/teams/{teamId}/requests/{rid}/accept` | `{}` | Invalidates `['teams']` |
| F30 | `features/search/SearchPage.tsx:4` | GET | `/search?q=&type=&sector=` | — | Expects an **array**; reads `id,fullName,title,university,department,body,type` |
| F31 | `features/profile/ProfilePage.tsx:4` | GET | `/me/profile` (self) | — | Reads `fullName,university,department,batch,verified,email,skills,projects,education,achievements,links` |
| F32 | `features/profile/ProfilePage.tsx:4` | GET | `/users/{id}` (other) | — | Same as F31 |
| F33 | `features/profile/ProfilePage.tsx:5` | GET | `/me/activity` (self) | — | Reads `id,title,text` |
| F34 | `features/profile/ProfilePage.tsx:5` | GET | `/users/{id}/activity` (other) | — | Same as F33 |
| F35 | `features/profile/ProfilePage.tsx:6` | POST | `/users/{id}/follow` | `{}` | Button flips to "Following" on success |
| F36 | `features/messages/MessagesPage.tsx:22` | GET | `/rooms` | — | Reads `id,publicId,type,name` |
| F37 | `features/messages/MessagesPage.tsx:23` | GET | `/auth/me` | — | Reads `id` (number) only |
| F38 | `features/messages/MessagesPage.tsx:24` | GET | `/rooms/{publicId}/messages` | — | Reads `id,senderId,senderName,body,text,createdAt,read` |
| F39 | `features/messages/MessagesPage.tsx:59` | POST | `/rooms/{publicId}/messages` | `{body,idempotencyKey}` | Appends returned message to cache (REST fallback only) |
| F40 | `features/messages/MessagesPage.tsx:71` | POST | `/rooms/{publicId}/read?messageId=` | `{}` | Ignores result (fire-and-forget read receipt) |
| F41 | `features/messages/MessagesPage.tsx:41` | STOMP SUBSCRIBE | `/topic/room.{publicId}` | — | Parses frame body as `ChatMessage`, appends (dedupe by `id`) |
| F42 | `features/messages/MessagesPage.tsx:58` | STOMP SEND | `/app/rooms.{publicId}.send` | `{body,idempotencyKey}` | Returns `null` (no REST fallback when STOMP succeeds) |
| F43 | `features/notifications/NotificationsPage.tsx:4` | GET | `/notifications` | — | Reads `id,title,message,body,createdAt,read` |
| F44 | `features/notifications/NotificationsPage.tsx:3` | PATCH | `/me/notification-preferences` | `{emailDigest,mentions}` (two fixed presets) | Sets "Preferences saved." on success |
| F45 | `features/settings/SettingsPage.tsx:4` | PUT | `/profiles/me` | `{bio}` | Shows "Profile saved." |
| F46 | `features/settings/SettingsPage.tsx:5` | PUT | `/profiles/me` | `{phoneVisible}` | Shows "Privacy saved." |
| F47 | `features/settings/SettingsPage.tsx:6` | POST | `/me/delete` | `{password}` | Shows "Account deletion requested. This is processed server-side." |
| F48 | `admin/AdminLayout.tsx:7` | POST | `/admin/auth/logout` | — | Errors swallowed (`catch{}`); clears user, nav `/admin/login` **[FIXED DURING INVENTORY — was `/admin/logout`]** |
| F49 | `admin/pages/AdminLogin.tsx:10` | POST | `/admin/auth/login` | `{email,password}` | Reads `stage,otpauthUrl`; nav `/admin/totp` with `{stage,otpauthUrl}` as route state **[FIXED DURING INVENTORY — was `/admin/login`, read `totpRequired`]** |
| F50 | `admin/pages/Totp.tsx:13` | POST | `/admin/auth/totp/enrol` | `{code}` | Reads `recoveryCodes[]` and displays them (shown once) **[FIXED DURING INVENTORY — was `/admin/totp/enrol {}`, read `secret`]** |
| F51 | `admin/pages/Totp.tsx:14` | POST | `/admin/auth/totp/verify` | `{code}` | Sets admin user from `{adminId,email,role}`; nav `/admin` **[FIXED DURING INVENTORY — was `/admin/totp/verify`]** |
| F52 | `admin/pages/Dashboard.tsx:3` | GET | `/admin/dashboard` | — | Reads `userCount|users,pendingVerifications,pendingReports,activeEvents,newUsersPerDay` |
| F53 | `admin/pages/Verifications.tsx:4` | GET | `/admin/verification-queue?status=` | — | Reads `id,fullName,email,university,status,studentId,ocrMatchScore,ocrText,previousAttempts` |
| F54 | `admin/pages/Verifications.tsx:5` | POST | `/admin/verification/{id}/approve` | `{reason: undefined}` → serialises to `{}` | Invalidates queue; clears selection |
| F55 | `admin/pages/Verifications.tsx:5` | POST | `/admin/verification/{id}/reject` | `{reason}` | Same as F54 |
| F56 | `admin/pages/Verifications.tsx:31` | GET (raw `<img src>`, not `api*`) | `/admin/verification/{id}/id-image` | — | Renders JPEG; `onError` shows "not available / outside scope" |
| F57 | `admin/pages/Users.tsx:6` | GET | `/admin/users?q=` | — | Reads `id,fullName,email,status` |
| F58 | `admin/pages/Users.tsx:4` | POST | `/admin/users/{id}/{action}` | `{reason}`; actions `suspend,ban,reactivate,revoke,force-reset` | Shows "Action applied." on success |
| F59 | `admin/pages/Moderation.tsx:4` | GET | `/admin/moderation` | — | Reads `id,targetType,targetId,reportCount,status` |
| F60 | `admin/pages/Moderation.tsx:3` | POST | `/admin/moderation/{id}/{action}` | `{}`; actions `hide,remove,restore,request-edit,warn` | Invalidates queue |
| F61 | `admin/pages/Appeals.tsx:4` | GET | `/admin/appeals` | — | Reads `id,targetType,targetId,status` |
| F62 | `admin/pages/Appeals.tsx:3` | POST | `/admin/appeals/{id}/{action}` | `{}`; actions `uphold,overturn` | Invalidates queue |
| F63 | `admin/pages/AdminEvents.tsx:2` | GET | `/admin/events` | — | Reads `id,title,type,status` |
| F64 | `admin/pages/Announcements.tsx:4` | GET | `/admin/announcements` | — | Reads `id,title,body` |
| F65 | `admin/pages/Announcements.tsx:3` | POST | `/admin/announcements` | `{title,body}` | Clears form; invalidates list |
| F66 | `admin/pages/MasterData.tsx:5` | GET | `/admin/universities` | — | Reads `id,name,emailDomains[] (joins),active` |
| F67 | `admin/pages/MasterData.tsx:5` | GET | `/admin/department-catalog` | — | Reads `id,name,code,active` |
| F68 | `admin/pages/MasterData.tsx:5` | GET | `/admin/departments` | — | Same as F67 |
| F69 | `admin/pages/MasterData.tsx:5` | GET | `/admin/batches` | — | Same as F67 |
| F70 | `admin/pages/MasterData.tsx:5` | GET | `/admin/sectors` | — | Same as F67 |
| F71 | `admin/pages/Moderators.tsx:2` | GET | `/admin/moderators` | — | Reads `id,fullName|name,email,university,active` |
| F72 | `admin/pages/Analytics.tsx:2` | GET | `/admin/analytics` | — | Reads `activeUniversities,verificationTurnaroundDays,eventCount,eventsPerSector,userGrowth` |
| F73 | `admin/pages/Audit.tsx:3` | GET | `/admin/audit?action=` | — | Reads `id,createdAt,actorId|actor,action,targetType,targetId,reason` |
| F74 | `admin/pages/Audit.tsx:2` | GET (raw `<a href>`, not `api*`) | `/admin/audit/export?action=` | — | Plain navigation to the URL (browser download) |

No other `fetch(` exists in `frontend/src` outside `client.ts`
(the three `fetch(` hits in `Feed.tsx:20`, `shared.tsx:9`,
`AdminList.tsx:7` are the literal string `"Request failed (…)"` inside
`errorMessage` usage, not calls). `apiDelete` is exported but **never
used** anywhere. `GET /api/auth/me` is called only from `MessagesPage`
(F37) — nothing calls it on app load (§6, G-1). `GET /api/public/*`
is now called only by the sign-up form (F02a–F02c) — nothing else uses
the catalog. No page ever calls `GET /api/csrf`.

## 2. Backend mapping inventory (all 104 routes)

Full paths include the class-level prefix. Auth column: S = student
chain (`authenticated()` + `requireStudent`, plus `requireVerified`
where noted), A = admin chain (`MFA_VERIFIED`), P = `permitAll`.
Response DTO field lists are from the DTO/service source cited.

| ID | Method + path | Controller:line | Request | Response | Auth |
|---|---|---|---|---|---|
| B01 | GET `/api/csrf` | AuthController:24 | — | `{token,headerName}` | P |
| B02 | GET `/api/health` | AuthController:28 | — | `{status,service}` | S (authenticated — see §6 G-9) |
| B03 | POST `/api/auth/register` (multipart) | AuthController:29 | `RegisterForm`: `fullName,phone,email,universityId,departmentId,batchId?,address,studentId,password` (all required except `batchId`) + files `idCard`,`profilePhoto` (both required, `AuthService.register:55-56`) | `UserDto{id,fullName,email,phone,universityId,departmentId,batchId,studentId,state,phoneVerified,emailVerified,createdAt}`; 201 | P |
| B04 | POST `/api/auth/verify-phone` | AuthController:32 | `{userId (Long, @NotNull), otp}` | `UserDto` | P |
| B05 | GET `/api/auth/verify-email?token=` | AuthController:33 | `token` query | `UserDto` | P |
| B06 | POST `/api/auth/login` | AuthController:34 | `{email,password}` | `UserDto` | P |
| B07 | POST `/api/auth/logout` | AuthController:40 | — | `{status}` | P (permitAll `/api/auth/**`) |
| B08 | POST `/api/auth/password-reset/request` | AuthController:45 | `{email}` | `{status}` | P |
| B09 | POST `/api/auth/password-reset/confirm` | AuthController:48 | `{email,otp,newPassword}` | `{status}` | P |
| B10 | POST `/api/auth/change-password` | AuthController:51 | `{oldPassword,newPassword}` | `{status}`; invalidates session | S |
| B11 | GET `/api/auth/me` | AuthController:57 | — | `UserDto` | S |
| B12 | POST `/api/me/delete` | AuthController:61 | `{password}` | `{deleted:true}` | S |
| B13 | GET `/api/auth/verification/status` | AuthController:68 | — | `VerificationStatusDto{userId,state,requestStatus,ocrMatchScore,ocrStatus,rejectionReason}` | S |
| B14 | POST `/api/auth/verification/resubmit` (multipart) | AuthController:72 | optional part `idCard` | `UserDto`; only when state is REJECTED, ≤3/24h (`AuthService.resubmit:153-159`) | S |
| B15 | GET `/api/public/universities` | PublicController:12 | — | `UniversityDto{id,name,shortName,emailDomains (String, not array),active,createdAt,updatedAt}[]` | P |
| B16 | GET `/api/public/departments?universityId=` (required) | PublicController:13 | `universityId` | `DepartmentDto{id,universityId,catalogId,name,active,createdAt}[]` | P |
| B17 | GET `/api/public/department-catalog` | PublicController:14 | — | `DepartmentCatalogDto{id,name,shortName,active,createdAt}[]` | P |
| B18 | GET `/api/public/batches?universityId=&departmentId?` | PublicController:15 | `universityId` required | `BatchDto{id,universityId,departmentId,label,startYear,active,createdAt}[]` | P |
| B19 | GET `/api/public/sectors` | PublicController:18 | — | `SectorTagDto{id,name,active,createdAt}[]` | P |
| B20 | GET `/api/public/skills` | PublicController:19 | — | `SkillDto{id,name,active,createdAt}[]` | P |
| B21 | GET `/api/feed?level=&cursor=&limit=20&sector=` | FeedController:11 | `level` ∈ DEPARTMENT/UNIVERSITY/HUB/NATIONAL; `sector` is a **Long id**; `cursor`; `limit` (1–50) | `PageResponse{items:PostDto[],nextCursor,hasMore}`; unverified users get NATIONAL-only (`FeedService.feed:18-27`) | S |
| B22 | GET `/api/announcements` | FeedController:22 | — | `AnnouncementDto{id,adminId,title,body,scope,universityId,expiresAt,active,createdAt}[]` | S (student principal) |
| B23 | POST `/api/posts` | PostController:17 | `CreatePost{type,visibility,title,body,sectorIds[]}`; `visibility` ∈ DEPARTMENT/UNIVERSITY/DEPARTMENT_HUB/NATIONAL, `type` defaults to `POST`; `requireVerified` | `PostDto{id,authorId,authorName,type,visibility,title,body,status,createdAt,eventDeadline,regLink}` | S + verified |
| B24 | GET `/api/posts/{id}` | PostController:21 | `id` | `PostDto` (unverified: NATIONAL only) | S |
| B25 | POST `/api/posts/{id}/comments` | PostController:24 | `CommentBody{body,mentionUserIds[]}` | `CommentDto{id,postId,authorId,body,status,createdAt}` | S + verified |
| B26 | GET `/api/posts/{id}/comments` | PostController:27 | `id` | `CommentDto[]` | **none** (no `Authentication` param — audit-03 §4.3) |
| B27 | POST `/api/reactions?reaction=LIKE` | PostController:28 | `{targetType,targetId}` + `reaction` query (default LIKE) | `{status:ok}` | S + verified |
| B28 | DELETE `/api/reactions` | PostController:31 | `{targetType,targetId}` | `{status:ok}` | S (no `requireVerified`) |
| B29 | POST `/api/bookmarks` | PostController:34 | `{targetType,targetId}` | `{status:ok}` | S + verified |
| B30 | DELETE `/api/bookmarks` | PostController:37 | `{targetType,targetId}` | `{status:ok}` | S (no `requireVerified`) |
| B31 | GET `/api/bookmarks` | PostController:40 | — | `BookmarkDto{id,userId,targetType,targetId,createdAt}[]` | S |
| B32 | POST `/api/events` | PostController:43 | `CreateEvent{visibility,title,body,deadline (LocalDateTime),regLink,location,sectorIds[]}`; deadline must be future (`PostService.createEvent:86`); `body` validated non-empty (`ContentValidator:10`) | `PostDto` | S + verified (via `createPost`) |
| B33 | POST `/api/events/{id}/respond?response=` | PostController:47 | `response` ∈ INTERESTED/GOING (query, required) | `{status:ok}` | S + verified |
| B34 | GET `/api/calendar?from=&to=` | PostController:51 | `from`,`to` (both required strings) | `[{id,title,deadline,location,reg_link}]` (raw JDBC maps) | S |
| B35 | GET `/api/profiles/me` | ProfileController:12 | — | `ProfileDto{userId,fullName,email,phone,universityId,departmentId,batchId,studentId,state,bio,headline,links (String),skills[],sectors[],followers,following,isOwner}` | S |
| B36 | PUT `/api/profiles/me` | ProfileController:15 | `UpdateProfile{bio,headline,links,phoneVisible}` — null fields are left unchanged (`ProfileService.updateOwn:41-46`) | `ProfileDto` | S |
| B37 | GET `/api/profiles/{id}` | ProfileController:19 | `id` | `ProfileDto` (phone hidden unless owner/`phoneVisible`) | S (principal may be null) |
| B38 | POST `/api/profiles/me/projects` | ProfileController:23 | `TextPayload{title,description,url,...}` | `ProjectDto{id,userId,title,description,url,createdAt}` | S + verified |
| B39 | DELETE `/api/profiles/me/projects/{id}` | ProfileController:27 | `id` | `{status:deleted}` | S |
| B40 | POST `/api/profiles/me/education` | ProfileController:30 | `TextPayload{institution,degree,year,...}` | `EducationEntryDto{id,userId,institution,degree,year,createdAt}` | S + verified |
| B41 | POST `/api/profiles/me/achievements` | ProfileController:34 | `TextPayload{title,description,year}` | `AchievementDto{id,userId,title,description,year,createdAt}` | S + verified |
| B42 | POST `/api/profiles/me/skills` | ProfileController:38 | `{name}` (map) | `{status:added}` | S + verified |
| B43 | DELETE `/api/profiles/me/skills/{skillId}` | ProfileController:42 | `skillId` | `{status:removed}` | S |
| B44 | POST `/api/profiles/me/sectors/{sectorId}` | ProfileController:45 | `sectorId` | `{status:followed}` | S |
| B45 | POST `/api/profiles/{id}/follow` | ProfileController:48 | `id` | `{status:followed}` | S + verified |
| B46 | DELETE `/api/profiles/{id}/follow` | ProfileController:52 | `id` | `{status:unfollowed}` | S |
| B47 | POST `/api/profiles/me/identity-change` | ProfileController:56 | `{newUniversityId,newDepartmentId,newStudentId}` | echoes the request body | S + verified |
| B48 | GET `/api/rooms` | MessagingRestController:12 | — | `RoomDto{id,publicId,type,name,memberIds[]}[]` | S |
| B49 | GET `/api/rooms/search?q=` | MessagingRestController:15 | `q` required | `RoomDto[]` | S |
| B50 | POST `/api/rooms/department` | MessagingRestController:18 | — | `RoomDto` | S |
| B51 | POST `/api/rooms/university` | MessagingRestController:21 | — | `RoomDto` | S |
| B52 | POST `/api/rooms/private/{userId}` | MessagingRestController:24 | `userId` | `RoomDto` | S |
| B53 | GET `/api/rooms/{publicId}/messages?limit=50` | MessagingRestController:27 | `publicId` | `MessageDto{id,publicId,roomId,senderId,body,createdAt}[]` — **no `read` field** | S |
| B54 | POST `/api/rooms/{publicId}/messages` | MessagingRestController:30 | `SendBody{body,idempotencyKey}` | `MessageDto`; also broadcasts to `/topic/room.{publicId}` | S + verified |
| B55 | POST `/api/rooms/{publicId}/read?messageId=` | MessagingRestController:33 | `messageId` query (required Long) | `{status:ok}` | S |
| B56 | POST `/api/blocks/{userId}` | MessagingRestController:36 | `userId` | `{status:blocked}` | S |
| B57 | DELETE `/api/blocks/{userId}` | MessagingRestController:39 | `userId` | `{status:unblocked}` | S |
| B58 | POST `/api/rooms/{publicId}/mute?minutes=60` | MessagingRestController:42 | `minutes` query | `{status:muted}` | S |
| B59 | STOMP `/app/rooms.{publicId}.send` | StompController:13 | `{body,idempotencyKey}` | none (broadcast `MessageDto` to `/topic/room.{publicId}`) | S (session; membership checked in `MessagingService.sendMessage:86`) |
| B60 | POST `/api/reports` | ReportController:12 | `ReportBody{targetType,targetId,reason}` | `ReportDto{id,reporterId,targetType,targetId,reason,status,resolvedAt,resolvedBy,createdAt}` | S |
| B61 | POST `/api/appeals` | ReportController:16 | `AppealBody{targetType,targetId,reason}` | `AppealDto{id,appellantId,targetType,targetId,reason,status,tokenExpiresAt,decidedBy,decisionReason,decidedAt,createdAt}` | S |
| B62 | POST `/api/appeals/public?token=` | ReportController:21 | `token` query + `AppealBody` | `{status:Appeal submitted}` | P |
| B63 | GET `/api/notifications?limit=20` | NotificationController:10 | `limit` | `NotificationDto{id,recipientId,type,targetType,targetId,title,body,idempotencyKey,readFlag,createdAt}[]` — field is **`readFlag`, not `read`** | S |
| B64 | GET `/api/notifications/unread-count` | NotificationController:13 | — | `{unread}` | S |
| B65 | POST `/api/notifications/{id}/read` | NotificationController:16 | `id` | `{status:ok}` | S |
| B66 | POST `/api/notifications/read-all` | NotificationController:19 | — | `{status:ok}` | S |
| B67 | POST `/api/notifications/preferences` | NotificationController:23 | `PrefBody{type,emailEnabled,inAppEnabled}` | `{status:saved}` | S |
| B68 | GET `/api/search?q=&type=all` | SearchController:9 | `q` required; `type` ∈ `all`/`posts`/`users` only | `Map{posts:PostHit{id,title,body,visibility}[], users:UserHit{id,fullName,universityId,departmentId}[]}` — a **Map, not an array**; no `sector` param | S |
| B69 | POST `/api/teams` | TeamController:12 | `CreateTeam{eventPostId,name,maxSize}` | `TeamDto{id,version,eventPostId,creatorId,name,maxSize,status,createdAt}` | S |
| B70 | GET `/api/teams/event/{eventPostId}` | TeamController:16 | `eventPostId` | `TeamDto[]` | **none** (no `Authentication` param) |
| B71 | POST `/api/teams/{teamId}/join` | TeamController:17 | optional `{message}` | `TeamJoinRequestDto{id,teamId,userId,status,message,decidedAt,createdAt}` | S |
| B72 | GET `/api/teams/{teamId}/requests` | TeamController:21 | `teamId` | `TeamJoinRequestDto[]` | S |
| B73 | POST `/api/teams/requests/{requestId}/decide` | TeamController:24 | `DecideBody{accept:boolean}` | `{status:accepted|rejected}` | S |
| B74 | POST `/api/admin/auth/login` | AdminAuthController:30 | `{email,password}` | `{adminId,email,role,universityId,mfaVerified,stage,otpauthUrl?}`; `stage` ∈ `NEEDS_ENROLMENT`/`NEEDS_TOTP` (`AdminAuthService.login:43-51`); **no `totpRequired` field** | P |
| B75 | POST `/api/admin/auth/totp/enrol` | AdminAuthController:39 | `{code}` (**required** — `enrolTotp` verifies it, `AdminAuthService:53-56`) | `{status:enrolled, recoveryCodes:[10 × "NNNN-NNNN"]}`; **no `secret`/`otpauthUrl`** | A (session from login; NPE if unauthenticated — audit-03 §4.3) |
| B76 | POST `/api/admin/auth/totp/verify` | AdminAuthController:46 | `{code}` | `{adminId,email,role,universityId,mfaVerified,stage:MFA_VERIFIED}` | A (session from login) |
| B77 | POST `/api/admin/auth/email-otp/request` | AdminAuthController:55 | — | `{status:Email OTP sent}`; 202 | A |
| B78 | POST `/api/admin/auth/email-otp/verify` | AdminAuthController:62 | `{otp}` | `{…,stage:MFA_VERIFIED}` | A |
| B79 | POST `/api/admin/auth/logout` | AdminAuthController:71 | — | `{status:logged out}` | P |
| B80 | GET `/api/admin/auth/me` | AdminAuthController:76 | — | `{adminId,email,role,universityId,mfaVerified}` | A |
| B81 | GET `/api/admin/dashboard` | AdminController:31 | — | Map keys: `totalUsers,pendingVerifications,reportsAwaitingReview,activeEvents,pendingAppeals,newUsersToday` (`AdminService.dashboard:39-49`) | A |
| B82 | GET `/api/admin/verification-queue?status=` | AdminController:32 | `status` ∈ PENDING_REVIEW/APPROVED/REJECTED or absent | `VerificationRequestDto{id,version,userId,status,reason,ocrText,ocrMatchScore,ocrStatus,submittedAt,decidedAt,decidedBy,createdAt,fullName,email,university,studentId,previousAttempts}[]` | A (moderator-scoped) |
| B83 | POST `/api/admin/verification/{id}/approve` | AdminController:33 | no body | `VerificationRequestDto` | A |
| B84 | POST `/api/admin/verification/{id}/reject` | AdminController:36 | `{reason}` (mandatory, `VerificationFacade.reject:40`) | `VerificationRequestDto` | A |
| B85 | GET `/api/admin/verification/{id}/id-image` | AdminController:40 | `id` | JPEG bytes, `Cache-Control: no-store`; 404 out of moderator scope | A |
| B86 | GET `/api/admin/users?q=` | AdminController:48 | `q` optional | `AdminUserDto{id,version,universityId,departmentId,batchId,fullName,email,phone,address,studentId,state,phoneVerifiedAt,emailVerifiedAt,createdAt,updatedAt}[]` — field is **`state`, not `status`** | A |
| B87 | GET `/api/admin/users/{id}` | AdminController:49 | `id` | `AdminUserDto` | A |
| B88 | POST `/api/admin/users/{id}/{action}` | AdminController:50 | `{reason}` (mandatory); `action` upper-cased by the controller, accepted: SUSPEND/BAN/REACTIVATE/REVOKE/FORCE_RESET (`AdminService.changeUserState:108-118`) | `AdminUserDto` | A |
| B89 | POST `/api/admin/users/{id}/recommend-suspension` | AdminController:53 | `{reason}` | `SuspensionRecommendationDto{id,userId,moderatorId,reason,status,decidedBy,decidedAt,createdAt}` | A (moderators only) |
| B90 | GET `/api/admin/suspension-recommendations` | AdminController:56 | — | `SuspensionRecommendationDto[]` | A |
| B91 | POST `/api/admin/suspension-recommendations/{id}/decide` | AdminController:59 | `{action,reason}`; `"CONFIRM"` (case-insensitive) = confirm, anything else = reject | `SuspensionRecommendationDto` | A |
| B92 | GET `/api/admin/moderation-queue` | AdminController:62 | — | `ReportDto[]` — **no `reportCount` field** | A |
| B93 | POST `/api/admin/moderation/decide` | AdminController:63 | `{targetType,targetId,action,reason}`; `reason` mandatory; `action` ∈ HIDE/REMOVE/RESTORE/EDIT_REQUEST; `targetType` ∈ POST/EVENT/COMMENT (`AdminService.decideContent:170-188`) | `{status:ok}` | A |
| B94 | GET `/api/admin/appeals` | AdminController:67 | — | `AppealDto[]` (unscoped — audit-03 I-14) | A |
| B95 | POST `/api/admin/appeals/{id}/decide` | AdminController:70 | `{action,reason}`; `reason` mandatory; `"ACCEPT"` (case-insensitive) = accept, anything else = reject (`AdminService.decideAppeal:191-195`) | `AppealDto` | A |
| B96 | POST `/api/admin/announcements` | AdminController:73 | `{title,body,scope,universityId,expiresAt}` | `AnnouncementDto` | A |
| B97 | POST `/api/admin/master-data/universities` | AdminController:76 | `MasterBody{name,shortName,emailDomains,universityId,catalogId}`; `emailDomains` is a **String** | `UniversityDto` | A |
| B98 | POST `/api/admin/master-data/departments` | AdminController:79 | `MasterBody{…}` | `DepartmentDto` | A |
| B99 | POST `/api/admin/master-data/sectors` | AdminController:82 | `MasterBody{…}` | `SectorTagDto` | A |
| B100 | POST `/api/admin/moderators` | AdminController:85 | `ModeratorBody{email,universityId,password}` | `AdminAccountDto{id,email,role,universityId,active,totpEnrolled,createdAt,updatedAt}` (no password hash) | A |
| B101 | POST `/api/admin/moderators/{id}/deactivate` | AdminController:88 | `{reason}` | `{status:deactivated}` | A |
| B102 | GET `/api/admin/analytics` | AdminController:91 | — | Map keys: `usersByState,usersPerDay30,eventsPerSector,verificationTurnaroundHours,activeUniversities` (`AdminService.analytics:277-287`) | A |
| B103 | GET `/api/admin/audit-logs?limit=50` | AdminController:92 | `limit` (max 200); **no `action` filter** | `AuditLogDto{id,actorAdminId,action,targetType,targetId,reason,metadata,ip,prevHash,rowHash,createdAt}[]` — field is **`actorAdminId`, not `actorId`/`actor`** | A |
| B104 | GET `/api/admin/alerts` | AdminController:95 | — | `AdminAlertDto{id,adminId,universityId,type,targetType,targetId,message,readFlag,createdAt}[]` | A |

Backend total at HEAD: 103 REST + 1 STOMP = **104 routes**. (Audit-03
§4.3 also counted 103 REST mappings; the count agrees at HEAD.)

### 2.1 Routes added in the working tree during this inventory (sibling fix work, uncommitted)

| ID | Method + path | Source | Request | Response | Auth |
|---|---|---|---|---|---|
| B105 | POST `/api/auth/otp/resend` | AuthController (+3 lines) | `OtpResendRequest{userId}` | `{status,channel}`; 60s cooldown, 5/user + 10/IP per window (`AuthService.resendPhoneOtp:96-107`) | P |
| B106 | PATCH `/api/posts/{id}` | PostController:35 | `UpdatePost{title,body,visibility,sectorIds}` | `PostDto`; owner only, non-event posts | S |
| B107 | DELETE `/api/posts/{id}` | PostController:39 | — | `{status:deleted}` | S |
| B108 | POST `/api/posts/{id}/attachments` (multipart) | PostController:43 | file part (one image/PDF ≤10 MB) | `ContentDtos.AttachmentDto{id,ownerType,ownerId,fileName,contentType,sizeBytes,createdAt}` | S |
| B109 | GET `/api/posts/{id}/attachments` | PostController:48 | — | `AttachmentDto[]` | S |
| B110 | GET `/api/posts/{id}/attachments/{attachmentId}` | PostController:51 | — | file bytes (download) | S |
| B111 | GET `/api/events/{id}` | PostController:83 | `id` | `PostDto` (now incl. `location`) | S |
| B112 | GET `/api/events/upcoming` | PostController:87 | — | `PostDto[]` | S |
| B113 | GET `/api/events?past=false` | PostController:90 | `past` (default false) | `PostDto[]` | S |
| B114 | PATCH `/api/events/{id}` | PostController:94 | `UpdateEvent{title,body,deadline,regLink,location,visibility}` | `PostDto`; owner only | S |
| B115 | DELETE `/api/events/{id}` | PostController:98 | — | `{status:deleted}` | S |
| B116 | POST `/api/events/{id}/team-request` | PostController:103 | optional `TeamRequestBody{name?,maxSize?}` (defaults: `"Team for event {id}"`, 4) | `TeamDto` | S |
| B117 | GET `/api/community/context` | MessagingRestController | — | `CommunityContextDto{universityId,universityName,departmentId,departmentName,catalogId,departmentRoomPublicId,universityRoomPublicId}` | S |
| B118 | POST `/api/rooms/temp` | MessagingRestController | `TempRoomBody{name,memberIds[]}` | `RoomDto` | S |
| B119 | POST `/api/rooms/{publicId}/invite` | MessagingRestController | `InviteBody{userId}` | `RoomDto` | S |
| B120 | DELETE `/api/rooms/{publicId}/members/{userId}` | MessagingRestController | — | `RoomDto` | S |
| B121 | POST `/api/rooms/{publicId}/close` | MessagingRestController | — | `RoomDto` | S |
| B122 | POST `/api/rooms/{publicId}/attachments` (multipart) | MessagingRestController | file part | `MessageDto` | S |
| B123 | GET `/api/rooms/{publicId}/messages/{messageId}/attachments` | MessagingRestController | — | `AttachmentDto{id,fileName,contentType,sizeBytes,createdAt}[]` | S |
| B124 | GET `/api/rooms/{publicId}/messages/{messageId}/attachments/{attachmentId}` | MessagingRestController | — | file bytes (download) | S |

Working-tree backend total: **124 routes** (123 REST + 1 STOMP).
Also changed in the tree but not new routes: `PostDto` gained a
`location` field (`FeedService` diff); `RegisterForm.phone` gained a
Bangladeshi-mobile `@Pattern` (`AuthDtos` diff — FR-AUTH-02);
`AdminAuthController` gained a `requireAdminPrincipal` null-guard, so
the unauthenticated-TOTP NPE (§6 G-10) is fixed; `ChatRoom` gained a
`closed_at` column with untracked migration
`V009__chat_room_closed_at.sql`.

## 3. Match table (all 76 frontend operations)

| ID | Frontend → backend | Class | Exact delta / evidence |
|---|---|---|---|
| F01 | POST `/auth/logout` → B07 | MATCH | Same path/method; `{status}` ignored. |
| F02 | POST `/auth/register` → B03 | MATCH **[FIXED DURING INVENTORY]** | Was SHAPE (severe). Now: catalog dropdowns (F02a–F02c) supply `universityId`/`departmentId`/`batchId` as Longs; file part renamed to `idCard`; `profilePhoto` matches; phone has a client-side BD regex plus a new server `@Pattern`. FormData appends Longs as strings — Spring `@ModelAttribute` binds them. |
| F02a | GET `/public/universities` → B15 | MATCH | New call; no params; `CatalogUniversity` type matches `UniversityDto` fields used. |
| F02b | GET `/public/departments?universityId=` → B16 | MATCH | New call; required `universityId` is sent. |
| F02c | GET `/public/batches?universityId=&departmentId?` → B18 | MATCH | New call; required `universityId` sent, optional `departmentId` sent when set. |
| F03 | POST `/auth/login` → B06 | MATCH | `{email,password}` = `LoginRequest`; response `UserDto` has `fullName,email` as read. (UX note: ignores `state`, so a REGISTERED/PENDING user is sent to `/feed` — feed then shows NATIONAL-only; not a contract break.) |
| F04 | POST `/auth/otp/resend` → B105 | MATCH **[FIXED DURING INVENTORY]** | Was MISMATCH (`/auth/otp/send {phone}`, no such route). New backend `POST /api/auth/otp/resend {userId}` returns `{status,channel}`, exactly what the page reads; 60s cooldown + caps exist server-side. |
| F05 | POST `/auth/verify-phone` → B04 | MATCH **[FIXED DURING INVENTORY]** | Was MISMATCH. Now `{userId, otp}` = `OtpVerifyRequest`; `userId` comes from `sessionStorage`, set by SignUp from the register response `id`. |
| F06 | POST `/auth/password-reset/request` → B08 | MATCH **[FIXED DURING INVENTORY]** | Was MISMATCH. Now the exact backend path/body. |
| F07 | (removed) | — | The 3-step wizard's middle step is gone with the old `ForgotPassword.tsx`; the flow is now 2-step. |
| F08 | POST `/auth/password-reset/confirm` → B09 | MATCH **[FIXED DURING INVENTORY]** | Was MISMATCH. Now the exact backend path; field renamed `code` → `otp`. |
| F09 | GET `/auth/verify-email` → B05 | MATCH **[FIXED DURING INVENTORY]** | Was MISMATCH (word order). Now the exact backend path; `token` param matches. |
| F10 | GET `/auth/verification/status` → B13 | MATCH | Path/method right; `state` + `rejectionReason` both exist on `VerificationStatusDto`. |
| F11 | POST `/auth/verification/resubmit` → B14 | MATCH* | Path/method right; empty multipart is accepted (`idCard` optional). *Functional gap, not a contract break: the page has no file input, so a rejected user can never attach a corrected ID card; resubmit reuses the old `idCardKey` (`AuthService.resubmit:160-165`). |
| F12 | GET `/feed` → B21 | **SHAPE** | (1) `sector` sent as a **name** (`"Data Science"`) from `SECTORS` (`types.ts:11`); backend `sector` is a **Long id** → Spring type-mismatch 400 whenever a sector is selected (works only with "All sectors"). (2) Response `PostDto` has no `sector`, `reactionCount`, `commentCount`, `author.fullName`, `content`, or `level` — the page reads all of them and silently renders without them. `items`/`nextCursor` agree. |
| F13 | POST `/posts` → B23 | **SHAPE (severe — create always 400s)** | Sends `{title,body,sector?,level}`; backend `CreatePost{type,visibility,title,body,sectorIds}`. `visibility` is never sent → `null` → `INVALID_VISIBILITY` 400 (`PostService.createPost:25-26`) — before any sector handling. `level` and `sector` (name string) have no backend field and are dropped. (`type` defaults to `POST` server-side, so that part is harmless.) |
| F14 | GET `/posts/{id}` → B24 | MATCH | Path/method right; `title`/`body` exist on `PostDto`. |
| F15 | GET `/posts/{id}/comments` → B26 | **SHAPE (minor)** | Path/method right; `CommentDto` has no `authorName` — the page falls back to `'Student'` for every comment. (Also note B26 takes no `Authentication` — audit-03 §4.3.) |
| F16 | POST `/posts/{id}/comments` → B25 | MATCH | `{body}` = `CommentBody.body`; `mentionUserIds` is optional and never sent despite the @-mention UI (hardcoded suggestions `@teammate`,`@moderator`). |
| F17 | POST `/posts/{id}/reactions` → — | **MISMATCH** | No such route. Real route is B27 `POST /api/reactions {targetType,targetId}?reaction=LIKE`. |
| F18 | POST `/bookmarks` → B29 | MATCH | `{targetType:'POST',targetId:id}` = `TargetBody`; `targetId` arrives as a JSON string (`useParams`) and Jackson coerces it to Long. No un-bookmark UI (B30 exists, never called). |
| F19 | GET `/bookmarks` → B31 | **SHAPE (minor)** | Path/method right; `BookmarkDto` has no `title` — the page renders `"Saved item {targetId}"` for every row. |
| F20 | GET `/events/upcoming` → B112 | **SHAPE** **[backend route added during inventory; frontend still SHAPE]** | Route now exists (`PostController:87`, returns `List<PostDto>`). Response has `eventDeadline`, not `deadline` — the page renders `e.deadline` as blank; `type`/`visibility`/`title` agree. |
| F21 | GET `/events` → B113 | **SHAPE** **[backend route added during inventory]** | Route now exists (`?past=` defaults to `false`). Same `deadline` vs `eventDeadline` field gap as F20, and the past tab sends no `past=true`, so it shows upcoming events, not the archive. |
| F22 | POST `/events` → B32 | **SHAPE (severe)** | (1) `deadline` sent as a date-only string (`"2026-11-20"` from `<input type=date>`); backend expects `LocalDateTime` — Jackson cannot parse a date-only value into `LocalDateTime` (expected 400; **not live-verified**). (2) `body` never sent → `null` → `EMPTY_CONTENT` 400 (`ContentValidator:10`). (3) `type` (`'Seminar'…`) has no backend field and is dropped (events are `type='EVENT'` server-side). `title`/`visibility` agree. |
| F23 | GET `/events/{id}` → B111 | **SHAPE** **[backend route added during inventory]** | Route now exists (`PostController:83`, returns `PostDto`). `type/title/visibility/regLink` agree (`PostDto` now also carries `location`); `description`,`prizes`,`eligibility` still do not exist on `PostDto` (page renders them blank). |
| F24 | POST `/events/{id}/rsvp` → B33 | **MISMATCH** | Real route `POST /api/events/{id}/respond?response=INTERESTED|GOING` — different path suffix **and** different transport (query param, not `{status}` JSON body). The values themselves (`INTERESTED`/`GOING`) are correct. |
| F25 | POST `/events/{id}/team-request` → B116 | **SHAPE (minor)** **[backend route added during inventory]** | Route now exists (`PostController:103`, optional `TeamRequestBody{name?,maxSize?}`). The page sends `{lookingForTeammates}` — an unknown property, ignored — so the team is created with defaults (`"Team for event {id}"`, maxSize 4). The "looking" toggle has no effect on the created team, and the page never supplies a name/size. |
| F26 | GET `/calendar?month=` → B34 | **SHAPE** | Path right, params wrong: backend requires `from` **and** `to` (both `@RequestParam`, no defaults) → 400 when absent. Response rows do have `deadline` (the page reads `date||deadline`), but the call never succeeds. |
| F27 | GET `/teams` → — | **MISMATCH** | No `GET /api/teams` route (only B69 create, B70 by-event, B72 pending requests). |
| F28 | POST `/teams/{teamId}/join` → B71 | MATCH | Path/method right; `{}` is fine (`JoinBody` is `@RequestBody(required=false)`). |
| F29 | POST `/teams/{teamId}/requests/{rid}/accept` → B73 | **MISMATCH** | Real route `POST /api/teams/requests/{requestId}/decide {accept:boolean}` — different path shape (no `{teamId}` segment) and different body (`{}` vs `{accept:true}`). Note audit-03 §6 I-8 misstates this body as `{action, reason}` — the code takes `{accept}` (`TeamController:21`). |
| F30 | GET `/search` → B68 | **SHAPE (severe — always empty)** | (1) Response is a **Map** `{posts:[],users:[]}`; the page expects an array (`Array.isArray` else `.items`) → `items` is always `[]` → "No results" even on success. (2) `type='people'` (the page's option) matches neither backend branch (`posts`/`users`) → both keys omitted. (3) `sector` param is ignored server-side. `q` agrees. |
| F31 | GET `/me/profile` → B35 | **MISMATCH** | Real route `GET /api/profiles/me`. Even at the right path the response shape differs: `ProfileDto` carries `universityId/departmentId/batchId` (Longs) not `university/department/batch` (strings), `state` not `verified`, `links` is a **String** not an array, and there are **no** `projects`/`education`/`achievements` arrays (those live behind B38–B41, never called). |
| F32 | GET `/users/{id}` → B37 | **MISMATCH** | Real route `GET /api/profiles/{id}`. Same response-shape notes as F31. |
| F33 | GET `/me/activity` → — | **MISMATCH** | No activity endpoint (audit-03 I-7, FR-PROF-07). |
| F34 | GET `/users/{id}/activity` → — | **MISMATCH** | No activity endpoint. |
| F35 | POST `/users/{id}/follow` → B45 | **MISMATCH** | Real route `POST /api/profiles/{id}/follow` (also `requireVerified`). |
| F36 | GET `/rooms` → B48 | MATCH | Path/method right; `RoomDto{id,publicId,type,name,memberIds}` matches the page's local `Room` type. (`types.ts` `Room.lastMessage` has no backend field but is never read here.) |
| F37 | GET `/auth/me` → B11 | MATCH | Path/method right; `UserDto.id` is read. |
| F38 | GET `/rooms/{publicId}/messages` → B53 | MATCH | Path/method right; `MessageDto{id,publicId,roomId,senderId,body,createdAt}` matches `ChatMessage` (extra local fields `senderName`,`text`,`read` are fallbacks only). |
| F39 | POST `/rooms/{publicId}/messages` → B54 | MATCH | `{body,idempotencyKey}` = `SendBody`. |
| F40 | POST `/rooms/{publicId}/read` → B55 | MATCH | `?messageId=` query matches the required `@RequestParam Long messageId`. |
| F41 | STOMP SUB `/topic/room.{id}` → §4 | MATCH | See §4. |
| F42 | STOMP SEND `/app/rooms.{id}.send` → B59 | MATCH | See §4. |
| F43 | GET `/notifications` → B63 | **SHAPE (minor)** | Path/method right; `NotificationDto` field is **`readFlag`**, not `read` — the page's `n.read?'· Read':''` never renders. `title/body/createdAt` agree. |
| F44 | PATCH `/me/notification-preferences` → B67 | **MISMATCH** | Real route `POST /api/notifications/preferences {type,emailEnabled,inAppEnabled}` — wrong path **and** wrong method **and** wrong body (`{emailDigest,mentions}` matches no backend field). |
| F45 | PUT `/profiles/me {bio}` → B36 | MATCH | Path/method right; `UpdateProfile` nulls are left unchanged server-side, so a bio-only PUT is valid. (Response `ProfileDto` is ignored.) |
| F46 | PUT `/profiles/me {phoneVisible}` → B36 | MATCH | Same as F45. Two separate PUTs where one would do; harmless. |
| F47 | POST `/me/delete` → B12 | MATCH | `{password}` = `DeleteAccountRequest`. (The success copy is wrong — see §6 G-7: deletion is synchronous, not "requested".) |
| F48 | POST `/admin/auth/logout` → B79 | MATCH **[FIXED DURING INVENTORY]** | Was MISMATCH (missing `/auth`). Error is still swallowed (`catch{}`), so a failed logout is invisible — see §6 G-4. |
| F49 | POST `/admin/auth/login` → B74 | MATCH **[FIXED DURING INVENTORY]** | Was MISMATCH. Body `{email,password}` matches; the page now reads `stage`/`otpauthUrl` (the real response fields) and passes them as route state to `/admin/totp` instead of reading the non-existent `totpRequired`. |
| F50 | POST `/admin/auth/totp/enrol` → B75 | MATCH **[FIXED DURING INVENTORY]** | Was MISMATCH. Now the exact path with the required `{code}` body; the page reads `recoveryCodes[]` and displays them (shown once), and shows the `otpauthUrl` from login state for first enrolment. |
| F51 | POST `/admin/auth/totp/verify` → B76 | MATCH **[FIXED DURING INVENTORY]** | Was MISMATCH (missing `/auth`). Body `{code}` matches; the page now sets the admin user from the response. |
| F52 | GET `/admin/dashboard` → B81 | **SHAPE** | Path right; 3 of 5 keys the page reads do not exist: backend sends `totalUsers` (page reads `userCount|users`), `reportsAwaitingReview` (page reads `pendingReports`), `newUsersToday` (page reads `newUsersPerDay`). Only `pendingVerifications` and `activeEvents` agree. Result: three "—" stats on a page whose lead claims "Real counts only". |
| F53 | GET `/admin/verification-queue` → B82 | MATCH | Path/method right; `status` filter values (`PENDING_REVIEW`/`APPROVED`/`REJECTED`) are exactly the accepted ones; all rendered fields exist on the enriched `VerificationRequestDto`. |
| F54 | POST `/admin/verification/{id}/approve` → B83 | MATCH | Path/method right; backend takes no body and the `{}` sent is ignored. |
| F55 | POST `/admin/verification/{id}/reject` → B84 | MATCH | Path/method right; `{reason}` matches `ReasonBody` and the UI enforces a non-empty reason. |
| F56 | GET `/admin/verification/{id}/id-image` → B85 | MATCH | Direct `<img>` GET to the authorised JPEG stream; `no-store`; scope-checked server-side. (Bypasses `api*`, so no `ApiError` handling — `onError` covers it.) |
| F57 | GET `/admin/users` → B86 | **SHAPE (minor)** | Path/method/`q` param right; `AdminUserDto` field is **`state`**, not `status` — the Status column renders `'—'` for every row. |
| F58 | POST `/admin/users/{id}/{action}` → B88 | **SHAPE** | Path/method/`{reason}` right; 4 of 5 actions work, but `force-reset` fails: the controller upper-cases the action (`"force-reset"` → `"FORCE-RESET"`), while the service switch accepts only `FORCE_RESET` (underscore) → 400 `INVALID_ACTION`. (Audit-03 §6 I-8 says actions "must send UPPERCASE values" — that is wrong: the controller upper-cases them. Only the hyphen is the bug.) |
| F59 | GET `/admin/moderation` → B92 | **MISMATCH** | Real route `GET /api/admin/moderation-queue`. Response also lacks the `reportCount` the page renders (it would show `'—'` even at the right path). |
| F60 | POST `/admin/moderation/{id}/{action}` → B93 | **MISMATCH** | Real route `POST /api/admin/moderation/decide {targetType,targetId,action,reason}` — different path, different body (the page sends `{}` with the action as a path segment and **no `targetType`/`targetId`/`reason`**; `reason` is mandatory server-side). Action vocabulary also differs: page `request-edit` vs backend `EDIT_REQUEST`; page `warn` has no backend action at all. |
| F61 | GET `/admin/appeals` → B94 | MATCH | Path/method right; `id,targetType,targetId,status` all exist on `AppealDto`. |
| F62 | POST `/admin/appeals/{id}/{action}` → B95 | **MISMATCH** | Real route `POST /api/admin/appeals/{id}/decide {action,reason}`. The page sends `{}` with action as a path segment; `reason` is mandatory server-side (400). Worse, the values are semantically inverted: the backend treats **anything other than `"ACCEPT"` as reject**, so both `uphold` and `overturn` would decide REJECTED — `uphold` (intended: keep the decision) would wrongly overturn it into REJECTED only by accident of the same outcome, and `overturn` (intended: accept the appeal) would be decided as REJECTED, the opposite of its intent. |
| F63 | GET `/admin/events` → — | **MISMATCH** | No admin events endpoint (audit-03 I-7, FR-ADM-07). |
| F64 | GET `/admin/announcements` → — | **MISMATCH** | No `GET /api/admin/announcements`. The only announcements read is B22 `GET /api/announcements`, which is on the **student** chain (`requireStudent`) — an admin session (`AdminPrincipal`) cannot use it. |
| F65 | POST `/admin/announcements` → B96 | **SHAPE (severe — expected 500)** | Path/method right, but `scope` is never sent (`null`) and `createAnnouncement` passes it straight into `Map.of("scope", scope)` (`AdminService:229`) — `Map.of` throws `NullPointerException` on a null value. The announcement row is saved first, then the audit write NPEs → the call returns 500 (`GlobalExceptionHandler` catch-all) despite the announcement existing. `scope`/`universityId`/`expiresAt` also have no UI. (500 not live-verified — inferred from `Map.of` semantics.) |
| F66 | GET `/admin/universities` → — | **MISMATCH** | No admin master-data read. Nearest is B15 `GET /api/public/universities`. Even there, `UniversityDto.emailDomains` is a **String**, while the page calls `.join(', ')` on it — that would throw at render time if the call ever succeeded. |
| F67 | GET `/admin/department-catalog` → — | **MISMATCH** | No admin route. Nearest is B17 `GET /api/public/department-catalog`. |
| F68 | GET `/admin/departments` → — | **MISMATCH** | No admin route. Nearest is B16 `GET /api/public/departments?universityId=` (required param the page never sends). |
| F69 | GET `/admin/batches` → — | **MISMATCH** | No admin route. Nearest is B18 `GET /api/public/batches?universityId=` (required param never sent). |
| F70 | GET `/admin/sectors` → — | **MISMATCH** | No admin route. Nearest is B19 `GET /api/public/sectors`. |
| F71 | GET `/admin/moderators` → — | **MISMATCH** | No moderators list route (only B100 create + B101 deactivate). |
| F72 | GET `/admin/analytics` → B102 | **SHAPE** | Path right; keys differ: backend sends `verificationTurnaroundHours` (page reads `verificationTurnaroundDays` — wrong unit *and* name), `usersPerDay30` (page reads `userGrowth`), and has no `eventCount` at all. Only `activeUniversities` and `eventsPerSector` agree. |
| F73 | GET `/admin/audit` → B103 | **MISMATCH** | Real route `GET /api/admin/audit-logs?limit=` — different path **and** different param (`action` filter does not exist server-side; `limit` is the only param). Response field is `actorAdminId`, not `actorId`/`actor` (page shows `'—'`). |
| F74 | GET `/admin/audit/export` → — | **MISMATCH** | No audit export endpoint (audit-03 I-7 lists it as "alias/export if wanted"). Plain `<a href>` navigation, so the 404 lands the admin on a raw error page, not an in-app error state. |

## 4. STOMP contract — MATCH (statically)

| Item | Frontend (`api/stomp.ts`, `MessagesPage.tsx`) | Backend (`config/WebSocketConfig.java`, `messaging/api/StompController.java`, `messaging/MessagingService.java`) | Verdict |
|---|---|---|---|
| Endpoint | Native WebSocket to `/ws/websocket` (`stomp.ts:76-79`) | `registerStompEndpoints("/ws").withSockJS()` — `/ws/websocket` is the SockJS websocket transport URL | MATCH |
| Broker / prefixes | Assumes `/topic` + `/queue`, app `/app`, user `/user` | `enableSimpleBroker("/topic","/queue")`, `setApplicationDestinationPrefixes("/app")`, `setUserDestinationPrefix("/user")` | MATCH |
| Subscribe | `/topic/room.{publicId}` (`MessagesPage.tsx:41`) | Interceptor authorises SUBSCRIBE to `/topic/room.*` only for room members (throws otherwise); broadcast goes to `/topic/room.{publicId}` (`MessagingService:102`) | MATCH |
| Send | `/app/rooms.{publicId}.send`, JSON `{body, idempotencyKey}` (`MessagesPage.tsx:58`) | `@MessageMapping("/rooms.{publicId}.send")`, `@Payload StompSend{body,idempotencyKey}` (`StompController:13`) | MATCH (note the dot, not a slash — both sides agree) |
| Broadcast payload | Parses frame body as `ChatMessage{id,publicId,roomId,senderId,body,createdAt}` | `MessageDto{id,publicId,roomId,senderId,body,createdAt}` via `convertAndSend` | MATCH |
| Auth | Session cookie (`UL_SESSION`) on the WS handshake | HTTP-session auth; interceptor requires `UnilinkUserPrincipal` | MATCH |
| Reconnect | Re-subscribes after CONNECTED; heartbeat 10s/10s; `send()` returns false while down → REST fallback | Broker drops subscriptions on reconnect (client handles it); no server-side resumption | MATCH |
| Gaps | No subscription to any `/user` destination (no push for notifications/mentions); only the open room is subscribed | — | Gap, not a mismatch |
| Unverified caveat | The interceptor checks membership only for destinations starting `/topic/room.`; SEND authorisation relies on the service-layer membership check in `sendMessage` (`MessagingService:86`), which throws `NotFound` for non-members | — | OK statically |

No STOMP frame has ever run against a live server (audit-03 §4.7);
the 5 unit tests in `frontend/src/test/stomp.test.ts` cover framing,
subscribe-on-CONNECTED, send-while-down, and reconnect — not the
handshake, the interceptor, or the broadcast path.

## 5. ORPHAN-BACKEND routes (73 of 124 — no frontend caller)

### 5.1 Orphans the SRS needs a caller for (from audit-03 §5/§6 I-7)

Rows marked **[FIXED DURING INVENTORY]** were orphans at the start of
this task and gained a caller (or a backend route) while it ran; they
are kept here so the pre-fix picture is not lost.

| Backend route | SRS | Why it matters |
|---|---|---|
| B04 `POST /api/auth/verify-phone` **[FIXED DURING INVENTORY]** | FR-AUTH-04 | Now called by OtpStep (F05). Was orphaned: the OTP screen called the wrong paths. |
| B105 `POST /api/auth/otp/resend` | FR-AUTH-04 | New route, called by OtpStep resend (F04). |
| B08/B09 password-reset request/confirm **[FIXED DURING INVENTORY]** | FR-AUTH-11 | Now called by ForgotPassword (F06/F08). |
| B10 `POST /api/auth/change-password` | FR-AUTH-11/12 | No UI anywhere (no settings change-password form). |
| B15–B20 `GET /api/public/*` (6 routes) **[PARTLY FIXED DURING INVENTORY]** | FR-AUTH-01, FR-ADM-10 | `universities`/`departments`/`batches` are now called by SignUp (F02a–F02c). `department-catalog`, `sectors`, `skills` are still never called; Master Data still calls wrong admin paths (F66–F70). |
| B22 `GET /api/announcements` | FR-ADM-09 | No student caller; the admin page calls a non-existent path (F64). |
| B27 `POST /api/reactions` | FR-CNT-02 | PostDetail calls `/posts/{id}/reactions` (F17). |
| B28/B30 DELETE reactions/bookmarks | FR-CNT-02, FR-BMK-01 | No unlike/un-bookmark UI. |
| B33 `POST /api/events/{id}/respond` | FR-EVT-02 | EventDetail calls `/rsvp` (F24). |
| B34 `GET /api/calendar` | FR-CAL-01 | Called with wrong params (F26, SHAPE not orphan — listed here for completeness of the fix). |
| B35/B37 `GET /api/profiles/me`, `/api/profiles/{id}` | FR-PROF-02 | ProfilePage calls `/me/profile` and `/users/{id}` (F31/F32). |
| B38–B44 profile sub-resources (projects/education/achievements/skills/sectors) | FR-PROF-03/04/05, FR-SEC-02 | No UI calls any of them; ProfilePage renders `projects`/`education`/`achievements` from the (wrong) profile response instead. |
| B45/B46 follow/unfollow | FR-PROF-06 | Called at the wrong path (F35). |
| B47 `POST /api/profiles/me/identity-change` | FR-PROF-09 | No UI; no admin approve path exists either. |
| B49–B52 rooms search/department/university/private | FR-MSG-01/02/03 | MessagesPage only lists rooms (F36); rooms are never created from the UI, so a new user sees an empty list. |
| B56–B58 block/unblock/mute | FR-MSG-06 | Backend-only. |
| B60/B61 `POST /api/reports`, `/api/appeals` | FR-REP-01/03 | No report/appeal UI in the student app at all. |
| B64–B67 notifications unread-count/read/read-all/preferences | FR-NOT-01 | Preferences called at the wrong path/method (F44); read-state never called (page shows `read` which does not exist). |
| B69 `POST /api/teams` | FR-TEAM-01 | No caller; EventDetail now calls the new B116 `POST /api/events/{id}/team-request` (F25, SHAPE) instead. |
| B70/B72 teams by-event / pending requests | FR-TEAM-02 | TeamsPage calls `GET /teams` (F27) which does not exist. |
| B73 teams decide | FR-TEAM-02 | Called at the wrong path/body (F29). |
| B74–B78 admin-auth (login/totp/enrol/email-otp) **[FIXED DURING INVENTORY]** | FR-ADM-01 | Now called with the `/auth` segment (F48–F51). |
| B82–B85 verification queue/approve/reject/id-image | FR-ADM-03 | These four **are** called correctly (F53–F56) — the only fully-wired admin flow. (Listed here only to show the exception.) |
| B106–B110 post edit/delete + attachments | FR-CNT-02/03 | New routes, no frontend caller yet (no edit/delete UI, no attachment picker). |
| B111–B113 event detail/upcoming/list **[FIXED DURING INVENTORY — routes added]** | FR-EVT-03/07 | Now called by EventsPage/EventDetail (F20/F21/F23), but as SHAPE (wrong `deadline` field, past tab sends no `past=true`). |
| B114/B115 event edit/delete | FR-EVT-08 | New routes, no frontend caller yet. |
| B116 event team-request **[FIXED DURING INVENTORY — route added]** | FR-TEAM-01 | Now called by EventDetail (F25, SHAPE — `lookingForTeammates` ignored). |
| B117–B124 community context, temp rooms, invites, member removal, close, message attachments | FR-MSG-01/04/07/08, FR-COM-01 | New routes, no frontend caller yet. MessagesPage still only lists rooms. |
| B87 `GET /api/admin/users/{id}` | FR-ADM-04 | No caller (list page never drills in). |
| B89–B91 suspension recommendations | FR-ADM-04 | No UI; moderators therefore cannot do the one action the Users page claims they can ("Moderators can only recommend suspension" — the recommend endpoint is never called). |
| B92/B93 moderation queue/decide | FR-ADM-05/06 | Called at wrong paths (F59/F60). |
| B95 appeals decide | FR-ADM-08 | Called at the wrong path (F62). |
| B97–B99 master-data creates | FR-ADM-10 | No create UI (Master Data is read-only and its reads are wrong too). |
| B100/B101 moderators create/deactivate | FR-ADM-12 | No create/deactivate UI; the list read (F71) does not exist either. |
| B103 `GET /api/admin/audit-logs` | FR-ADM-11 | Called as `/admin/audit?action=` (F73). |
| B104 `GET /api/admin/alerts` | FR-ADM-05/13 | No caller; nothing surfaces admin alerts (e.g. the 3-in-30-days strike flag). |

### 5.2 Remaining orphans (backend exists, no SRS-mandated caller in the current UI)

B01 `GET /api/csrf` (nothing primes the CSRF cookie — see §6 G-8),
B02 `GET /api/health` (requires auth — §6 G-9), B13-adjacent B14 resubmit
is called (F11), B26 comments GET is called (F15), B62
`POST /api/appeals/public` (dead feature per audit-03 I-13: the token is
never issued/emailed, so the endpoint is unreachable in practice),
B80 `GET /api/admin/auth/me` (no admin session-restore call).

## 6. Error-handling gaps

- **G-1. Session restore missing.** `AuthProvider`
  (`app/auth.tsx`) is plain in-memory state; nothing calls `GET
  /api/auth/me` on load (only `MessagesPage` calls it, F37). A reload
  drops the client-side user while the server session survives, so the
  nav shows "Log in / Sign up" to a signed-in user. `RequireAuth`
  (defined in `auth.tsx`) is used on **no route in either app** — no
  route is guarded client-side (audit-03 §4.10).
- **G-2. 404s are misreported everywhere.** `client.ts`
  `errorMessage()` renders *any* 404 as "the backend endpoint is not
  available yet", and `ListPage` (`features/shared.tsx:9`),
  `AdminList` (`admin/AdminList.tsx:7`), `Feed.tsx:20`,
  `ProfilePage.tsx:8`, `SearchPage.tsx:4`, `Verifications.tsx:10`,
  `Dashboard.tsx:3`, `Analytics.tsx:2` all render `<ApiUnavailable
  endpoint=…>` on *any* 404. A genuinely missing resource (deleted
  post, out-of-scope verification) is indistinguishable from a wrong
  path — and 37 of the 74 calls *are* wrong paths, so most
  `<ApiUnavailable>` screens the user can hit today are self-inflicted.
- **G-3. Errors rendered for the wrong cause.** `Pending.tsx:4` shows
  `<ApiUnavailable>` for *any* error on verification-status (401/500
  included). `ProfilePage.tsx:5` shows the static text "Activity is
  not available yet." for *any* activity error, masking real failures.
- **G-4. Errors swallowed silently.** `Login.tsx:7` has
  `onError: ()=>{}`; `Layout.tsx:7` and `AdminLayout.tsx:6` swallow
  logout errors with `catch{}`; `MessagesPage` fire-and-forget
  `markRead` (F40) has no error handling at all. The admin logout
  swallow (F48) hides the fact that the logout endpoint itself is
  wrong (F48 is a MISMATCH).
- **G-5. Success claimed where none can happen.** `SettingsPage.tsx:6`
  says "Account deletion requested. This is processed server-side."
  after `POST /me/delete` — but the backend hard-deletes synchronously
  in one transaction; there is no async processing. `EventDetail.tsx:6`
  shows "Team request sent." on `teamReq.isSuccess` — reachable now
  that B116 exists, but the toggle value is ignored server-side.
  (The old Totp "save your recovery codes when the backend provides
  them" copy was fixed during the inventory — Totp.tsx now displays
  the codes.)
- **G-6. Silent data loss in the UI.** `SearchPage` (F30) always shows
  "No results" because it treats a Map as an array — a successful
  search looks identical to an empty one. `Dashboard`/`Analytics`
  (F52/F72) render "—" for keys that exist under different names, so a
  working backend looks half-empty. `Users` (F57) shows "—" for every
  Status. `NotificationsPage` (F43) never shows "Read".
- **G-7. No feedback on fire-and-forget mutations.** `OtpStep` send
  (F04) has no success state; the Feed composer (F13) has no success
  state; `markRead` (F40) has none. Conversely `Users.tsx` shows
  "Action applied." on `act.isSuccess` even though the action may have
  partially failed server-side (e.g. `force-reset`, F58).
- **G-8. CSRF cookie is never primed.** Nothing calls `GET /api/csrf`
  (B01) on load (audit-03 I-10). The first mutating call after a fresh
  page load sends no `X-XSRF-TOKEN` header; whether that 403s depends
  on the CSRF filter's treatment of a missing token — **not verified
  from code** (the `csrf(http)` config in `SecurityConfig` was not
  fully read; see §9).
- **G-9. Health endpoint requires auth.** `GET /api/health` (B02) is
  under `/api/**` and is not in the student chain's `permitAll` list,
  so an unauthenticated health check 401s (audit-03 §4.3). Only
  `/actuator/health` is public. No frontend caller either way.
- **G-10. Admin TOTP endpoints NPE when unauthenticated — FIXED
  DURING INVENTORY.** `AdminAuthController` gained a
  `requireAdminPrincipal` guard (throws 401 `Unauthorized` instead of
  the old unconditional cast → NPE → 500). Kept here as a record; no
  longer an open gap.
- **G-11. `force-reset` is a dead end even if fixed.** `FORCE_RESET`
  generates a temporary password server-side (`AdminService:113-117`)
  but never returns it to the admin (audit-03 I-13) — the action cannot
  achieve its purpose as designed.

## 7. Corrections to audit-03 (§4.10, §6 I-8)

The completeness audit's integration section is stale or wrong in
several places; this inventory supersedes it on the points below
(all verified against the source at HEAD `1f59a50`):

1. **Counts.** §4.10 says "~50 distinct frontend API paths: ~20 match,
   ~5 partial, ~30 mismatch". The actual count is **74 operations:
   23 MATCH / 14 SHAPE / 37 MISMATCH** (69% not clean). The audit
   undercounted because `ListPage`/`AdminList` `path=` props,
   the raw `<img>`/`<a>` calls, and the second `PUT /profiles/me`
   call site were not enumerated as separate calls.
2. **"Matches cleanly" list (§4.10) is partly wrong.** It names
   *email verify* (F09 is a MISMATCH — `/auth/email/verify` vs
   `/api/auth/verify-email`), *feed/posts/comments/bookmarks* (F12
   sector is SHAPE, F13 create is SHAPE and always 400s, F15
   `authorName` is SHAPE), *admin dashboard/analytics* (F52/F72 are
   SHAPE — 3 of 5 / 3 of 5 keys wrong), *announcement-create* (F65 is
   SHAPE and expected to 500 on the null `scope`), and *profile PUT*
   (correct — F45/F46 are MATCH). It also omits that the admin-auth
   calls it lists under I-4 as needing the `/auth` prefix are still
   unwired.
3. **I-8 "user actions must send UPPERCASE values" is wrong.**
   `AdminController.userAction` upper-cases the action itself
   (`AdminController:50-51`); lowercase `suspend`/`ban`/`reactivate`/
   `revoke` are fine. Only `force-reset` fails, because of the
   **hyphen** (`FORCE-RESET` ≠ `FORCE_RESET`).
4. **I-8 team-accept body is wrong.** It says to call
   `POST /api/teams/requests/{id}/decide {action, reason}`. The code
   takes `DecideBody{accept:boolean}` (`TeamController:21`) — no
   `action`, no `reason`.
5. **I-8 "master-data and moderators reads must use `GET
   /api/public/*`" is only half the story.** The public catalog has
   no moderators list and no batches-without-`universityId` read, and
   the admin reads also need the admin chain (the public endpoints are
   `permitAll`, so they *would* work from the admin app — but the
   Master Data page would still need the `universityId` query param
   for departments/batches and would still hit the `emailDomains`
   String-vs-array bug, F66).

## 8. Ranked worst clusters (fix order)

1. **~~Admin auth is entirely unwired~~ — FIXED DURING INVENTORY.**
   F48–F51 now call `/admin/auth/*`, read `stage`/`otpauthUrl`, send
   `{code}` on enrol and display recovery codes. Remaining: the
   `catch{}` swallow on admin logout (§6 G-4).
2. **~~Sign-up cannot succeed~~ — FIXED DURING INVENTORY.** Catalog
   dropdowns, Long ids, `idCard` part name and BD phone validation are
   all in place (F02/F02a–F02c). Remaining: nothing on the contract;
   needs a live test.
3. **~~OTP + password-reset flows are dead~~ — FIXED DURING
   INVENTORY.** F04–F06/F08/F09 now match B105/B04/B08/B09/B05, and
   the resend endpoint with cooldown/caps exists. Remaining: live
   test of the OTP round-trip (dev SMS is log-only).
4. **Events are now half-wired (F20–F25).** The backend routes were
   added during the inventory (B111–B113, B116), so list/detail/
   team-request are SHAPE rather than MISMATCH — but create still
   400s twice over (date-only deadline, missing body), RSVP still
   calls the wrong path (`/rsvp` vs `/respond`), the past tab sends no
   `past=true`, and the detail/list pages read `deadline` where the
   DTO has `eventDeadline`. Frontend-only fixes now.
5. **Post creation always 400s (F13).** Missing `visibility` →
   `INVALID_VISIBILITY` on every submit; sector/level silently
   dropped. The feed's only write path is broken. (Post edit/delete
   and attachments now exist server-side as orphans, B106–B110.)
6. **Search always returns empty (F30).** Map-vs-array + `type=people`
   + ignored `sector`. A successful search is indistinguishable from
   no results (§6 G-6).
7. **Profile pages call the wrong API and read the wrong shape
   (F31–F35).** Both profile reads, both activity reads and follow
   are MISMATCH; even at the right path the page expects string
   university/department/batch, a `verified` boolean and
   projects/education/achievements arrays the DTO does not have.
   (Profile sub-resource endpoints B38–B44 remain orphans.)
8. **Admin moderation/appeals decide paths (F59–F62).** Wrong paths,
   missing mandatory `reason`, wrong action vocabularies — and the
   appeals values are semantically inverted (`uphold`/`overturn` both
   land as REJECTED). The two most safety-critical admin actions
   cannot be performed correctly.
9. **Master Data + Moderators + Audit reads (F66–F71, F73–F74).**
   Seven GETs to non-existent admin paths (plus a dead export link);
   the nearest public reads need query params the pages never send,
   and the universities render would throw on `emailDomains.join`.
10. **Dashboard/Analytics/Announcements shape rot (F52, F65, F72).**
    Wrong Map keys render "—" on pages that claim "real counts only";
    announcement create is expected to 500 on a null `scope`
    (`Map.of` NPE) after saving the row.
11. **Notification preferences (F44) + notifications read-state
    (F43).** Wrong path/method/body for preferences; `readFlag` vs
    `read` means read state is never shown. Small fix, user-visible.
12. **Feed sector filter (F12).** Sending a sector *name* where a Long
    id is required 400s the feed — the most-used student page breaks
    the moment a user filters.

## 9. What could not be verified from code (static-only limits)

- **No live behaviour was observed.** Java→MySQL is intercepted in
  this sandbox, so no endpoint was ever exercised; every predicted
  status (400 `INVALID_VISIBILITY`, 400 sector type-mismatch, 422
  register, 500 announcement `Map.of` NPE) is an inference from the
  source. The 500 in particular depends on `Map.of` rejecting nulls
  (true per JDK) and on the audit-write path being reached (true per
  `AdminService:224-230`).
- **Jackson defaults were not pinned down.** No `jackson:` config
  exists in `application.yml` (checked) and no `ObjectMapper` bean
  was found, so this inventory assumes Spring Boot's defaults:
  unknown JSON properties ignored (so `level`/`sector`/`type` on
  F13/F22 are dropped, not rejected) and date-only strings rejected
  for `LocalDateTime` (F22). If the project overrides either default,
  F13/F22 change from "silently dropped / 400" accordingly.
- **CSRF behaviour on a missing token (G-8)** was not verified: the
  `csrf(http)` helper in `SecurityConfig` was not fully read, and
  whether a POST without `X-XSRF-TOKEN` is rejected or ignored could
  not be established statically.
- **Auth-chain edge cases** (e.g. whether the admin chain or the
  student chain wins for `/api/admin/auth/**` under the `@Order(1)`
  matcher, and the exact 401-vs-403 behaviour for an admin session
  hitting a student endpoint like B22) were read from the matcher
  configuration only, not exercised.
- **`GET /api/posts/{id}/comments` and `GET /api/teams/event/{id}`**
  take no `Authentication` parameter (audit-03 §4.3); whether an
  unauthenticated caller actually reaches them depends on the student
  chain's `anyRequest().authenticated()` — read as yes-blocked, not
  live-tested.
- **Line numbers** are as found by `grep -n` at HEAD `1f59a50`;
  one-line-per-call frontend files mean most call sites are a single
  line; `ListPage`/`AdminList` calls are attributed to the `path=`
  prop line in the page, with the actual `apiGet` in
  `features/shared.tsx:4` / `admin/AdminList.tsx:4`.

## 10. What changed during this inventory (sibling fix work, uncommitted)

The working tree was being fixed concurrently. At task start
`git status` showed 20 modified files; at freeze time it showed 33
modified + 1 untracked (`V009__chat_room_closed_at.sql`). The
following findings in this report's first pass were fixed before the
freeze, and are recorded here rather than silently dropped:

| Area | What changed (working tree, uncommitted) | Effect on this inventory |
|---|---|---|
| Sign-up | `SignUp.tsx` rewritten: catalog dropdowns (`GET /public/universities|departments|batches`), `universityId/departmentId/batchId` Longs, file part `idCard`, BD phone regex; `types.ts` gained `CatalogUniversity/CatalogDepartment/CatalogBatch` + `BD_PHONE_RE`; `AuthDtos.RegisterForm.phone` gained a server `@Pattern` | F02 SHAPE→MATCH; +3 frontend calls (F02a–F02c); B15/B16/B18 no longer orphans |
| OTP | `OtpStep.tsx` rewritten: verify → `POST /auth/verify-phone {userId, otp}` (id from `sessionStorage`, set by SignUp); resend → new `POST /api/auth/otp/resend {userId}` (`AuthController` + `OtpResendRequest`, cooldown + caps in `AuthService.resendPhoneOtp`) | F04/F05 MISMATCH→MATCH; B04 no longer orphan; B105 added |
| Password reset | `ForgotPassword.tsx` rewritten to the 2-step backend flow (`password-reset/request` → `password-reset/confirm {email, otp, newPassword}`); middle verify step deleted | F06/F08 MISMATCH→MATCH; F07 removed; B08/B09 no longer orphans |
| Email verify | `EmailVerify.tsx`: path fixed to `/auth/verify-email` | F09 MISMATCH→MATCH; B05 no longer orphan |
| Admin auth | `AdminLogin.tsx` → `/admin/auth/login`, reads `stage`/`otpauthUrl`; `Totp.tsx` rewritten around `stage` (enrol `{code}` → shows `recoveryCodes[]`; verify sets admin user); `AdminLayout.tsx` logout → `/admin/auth/logout`; `AdminAuthController` gained `requireAdminPrincipal` 401 guard | F48–F51 MISMATCH→MATCH; B74–B76/B79 no longer orphans; §6 G-10 fixed |
| Events/posts | `PostController` gained 11 mappings (post PATCH/DELETE, post attachments ×3, event GET detail/upcoming/list, event PATCH/DELETE, event team-request); `PostService` gained `updatePost/deletePost/getEvent/listEvents/updateEvent`; `PostDto` gained `location` | F20/F21/F23 MISMATCH→SHAPE; F25 MISMATCH→SHAPE; B106–B116 added (B106–B110, B114–B115 still orphans) |
| Messaging | `MessagingRestController` gained 8 mappings (community context, temp rooms, invite, member removal, close, message attachments ×3); `MessagingService` +188 lines; `ChatRoom.closed_at` + `V009` migration | B117–B124 added, all orphans (no frontend caller yet) |
| Login | `Login.tsx` now reads `state` and sets `verified` | F03 still MATCH; UX note narrowed |

Net effect on the counts: 74 → 76 frontend operations
(−1 removed call, +3 catalog calls); MATCH 23 → 36; SHAPE 14 → 17;
MISMATCH 37 → 23; backend routes 104 → 124; orphans 69 → 73.
**Nothing in this section was committed by this inventory** — the
report file `docs/phase-reports/phase2-inventory.md` is the only file
this worker wrote, and no commit was made.
