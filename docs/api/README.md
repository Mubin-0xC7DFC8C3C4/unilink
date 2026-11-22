# API documentation

The live, always-current reference is the OpenAPI document served by
the backend itself:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Raw spec (JSON): `http://localhost:8080/v3/api-docs`

## Endpoint groups

| Group | Base path | Notes |
|---|---|---|
| Health | `GET /api/health` | Public |
| Auth | `/api/auth/**` | Register (multipart, incl. optional `idCardBack`), login, OTP, email verification, password reset |
| Public catalogue | `/api/public/**` | Universities, departments, batches, sectors |
| Feed | `GET /api/feed?level=&sort=recent\|top` | Top uses the published ranking formula; DTO carries `reactionCount`, `viewerReaction`, `savedByViewer`, author department/university/batch |
| Posts & comments | `/api/posts/**` | Comments accept `parentId` for replies and return it |
| Reactions | `POST/DELETE /api/reactions?reaction=` | LIKE, CELEBRATE, INSIGHTFUL, SUPPORT, CURIOUS — one per user per target, re-reacting replaces |
| Events (student) | `/api/events/**` | Browse + `POST /events/{id}/respond` (INTERESTED/GOING; capacity + deadline enforced). Student event creation is refused (403) |
| Bookmarks | `/api/bookmarks` | The single save mechanism app-wide |
| Profiles & network | `/api/profiles/**` | `/{id}/followers`, `/{id}/following`, `/suggestions`, `/me/activity-items`, `/{id}/activity-items` |
| Notifications | `/api/notifications/**` | Persistent; types include FOLLOW, REACTION, COMMENT, REPLY, EVENT_PUBLISHED, EVENT_CANCELLED, REGISTRATION_CONFIRMED, VERIFICATION_DECISION |
| Teams | `/api/teams/**` | Teams and join requests |
| Workspace | `/api/workspace/**` | GitHub status/authorize/callback/repos/contents/file/commits; UniLink-native `discussions` |
| Admin auth | `/api/admin/auth/**` | Password + TOTP MFA |
| Admin | `/api/admin/**` | Dashboard, verifications (ID images incl. `/id-image/back`), users, moderation, **events CRUD + publish/cancel + registrations**, identity changes, master data, analytics, audit logs |

## Admin events contract (added 2026-11-22)

- `POST /api/admin/events` — body per the Create Event form; `status`
  is `DRAFT` or `PUBLISHED`. Publishing requires a future deadline.
- `GET /api/admin/events`, `GET /api/admin/events/{id}` — the feed DTO
  plus `eventStatus` and the extended event fields.
- `PUT /api/admin/events/{id}` — full edit (cancelled events refuse).
- `POST /api/admin/events/{id}/publish` · `POST /api/admin/events/{id}/cancel`
  (notifies every respondent) · `DELETE /api/admin/events/{id}` (soft
  remove).
- `PATCH /api/admin/events/{id}` — moderation lifecycle:
  `{status: HIDE|REMOVE|RESTORE}`, deadline, visibility,
  `registrationClosed`.
- `GET /api/admin/events/{id}/registrations` — userId, fullName, email,
  response, createdAt.

All admin endpoints require an MFA-verified admin session; moderators
are restricted to their own university's scope.
