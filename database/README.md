# Database

UniLink has exactly **one** schema mechanism: Flyway migrations inside
the backend, at `backend/src/main/resources/db/migration/`.

- `V001…V011` — the original audited schema and hardening migrations.
  Applied migrations are never edited.
- `V012__full_stack_integration.sql` — the full-stack integration build
  (2026-11-22): extended `event_details` (organizer, schedule, venue,
  registration settings, lifecycle status, creating admin), threaded
  comments (`comments.parent_id`), `users.id_card_back_key`, the
  `event_responses` uniqueness rule, and the new `github_connections`
  and `project_discussions` tables.

Flyway runs automatically on backend startup with the migration user
(`DB_MIGRATION_USER`); the application user has DML-only rights. To add
a change, create the next `V0NN__description.sql` — never a second
migration system, and never hand-edit a live database to match code.

Local setup, users, grants and the error-1419 note are in the root
`README.md` §8. Backups: `scripts/backup-database.ps1`.
