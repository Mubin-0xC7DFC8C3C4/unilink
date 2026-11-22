# UniLink — Production Deployment

## Topology

Single host, Docker Compose (`docker-compose.prod.yml`):

- **frontend** (nginx) — serves the built React app (student + admin entries)
  and proxies `/api/` and `/ws/` to the backend, so the whole product is one
  origin. Session cookies, CSRF and WebSocket all work without CORS.
- **backend** (Spring Boot, `SPRING_PROFILES_ACTIVE=prod`) — the prod profile
  fails fast when a required environment variable is missing, sets `Secure`
  session cookies, and disables Swagger/OpenAPI docs.
- **mysql** (MySQL 8) — private to the compose network (no published port).
  Flyway migrates on backend startup (V001–V012).
- **Volumes** — `mysql-data` (database) and `uploads` (user files at
  `/data/unilink/storage`) persist across container replacement and restarts.

## First deploy

```bash
git clone <repository> unilink && cd unilink
cp .env.production.example .env.production   # fill in every REQUIRED value
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
curl http://localhost/api/health             # via the frontend proxy
```

The first platform admin is created on first boot from
`INITIAL_ADMIN_EMAIL` / `INITIAL_ADMIN_PASSWORD`. Sign in at `/admin/login`
and complete the forced password change + TOTP setup.

## HTTPS

Terminate TLS in front of the frontend service (host-level Caddy/nginx with
certbot, or a cloud load balancer), then set `APP_BASE_URL` to the
`https://` origin and restart the backend. The session cookie is already
`Secure` under the prod profile. Do not publish the app over plain HTTP with
real users: login and OTP traffic must be encrypted.

## Health, logs, restart

- Health: `GET /api/health` (also proxied publicly as shown above).
- Logs: `docker compose -f docker-compose.prod.yml logs -f backend`.
- All services use `restart: unless-stopped`; the backend waits for a healthy
  MySQL before starting, and Flyway runs before the app serves traffic.

## Backups

- Database: `mysqldump` from the mysql container (see `scripts/` for the
  local equivalent) on a schedule, stored off-host.
- Files: snapshot the `uploads` volume together with the database backup —
  post attachments and ID images are referenced by database keys.

## Database portability — MySQL 8 and TiDB (D-167)

The Flyway chain V001–V013 is portable across MySQL 8 and TiDB Cloud:
seed upserts use `INSERT IGNORE` (the original `VALUES()` form was
rejected by MySQL 8.0.46 and is unsupported on TiDB), there are no
triggers and no FULLTEXT indexes (both unsupported on TiDB; search is
LIKE-based), and the `utf8mb4_0900_ai_ci` collation used throughout is
supported by TiDB ≥ 7.4 (TiDB Cloud Starter qualifies). The full chain
was executed against MySQL 8.0.46 after these changes: 60 tables, seeds
correct.

**Existing databases:** V001, V004 and V006 changed, so their Flyway
checksums no longer match a database that already applied them (such as
the Windows development database). Run Flyway `repair` once against that
database — or boot the backend once with
`spring.flyway.validate-on-migrate=false` — before the next migration.

For the zero-cost demo topology (Cloudflare Pages + Render Free + TiDB
Cloud Starter), follow **DEPLOY-FREE.md** instead of the compose stack.

## Alternative: managed hosting

The same artifacts map onto managed services without code changes: the
backend jar on any Java 21 host (set the same environment variables and
`SPRING_PROFILES_ACTIVE=prod`), the built `frontend/dist` on any static host
with the same `/api` + `/ws` proxy rules as `frontend/nginx.conf`, and any
managed MySQL 8 — plus a persistent disk mounted at the storage path
(`LOCAL_STORAGE_PATH`).
