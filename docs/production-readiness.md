# Production-readiness delivery plan

The work is split into independently releasable phases. A phase is complete only after automated tests, a secret scan, deployment verification, and rollback notes are available.

## Phase 1 — identity and tenant isolation

- [x] Signup, login, logout, and current-user endpoints
- [x] BCrypt password hashing with configurable work factor
- [x] Server-side `HttpOnly`, `Secure`, `SameSite=Lax` session cookie
- [x] Session fixation protection and CSRF protection for every mutation
- [x] Authentication required by default for application APIs
- [x] Story ownership enforced at every nested story endpoint
- [x] Cross-account story IDs concealed with `404`
- [x] Frontend auth routes, protected routes, session restoration, and logout
- [x] Account-scoped browser metadata cache
- [x] Authentication and ownership integration tests
- [x] Purge the pre-authentication demo stories so no legacy data can be claimed
- [x] Deploy and run an HTTPS end-to-end smoke test

## Phase 2 — API hardening

- [x] Rate-limit login, signup, question answering, and scene processing
- [x] Add consistent, non-sensitive validation errors and request IDs
- [x] Enforce request/body limits, query limits, client timeouts, and pagination
- [x] Add security headers and a reviewed Content Security Policy
- [x] Add structured security audit events without credentials or story text
- [x] Define durable idempotency behavior for scene processing

### Phase 2 operating contract

- Collection endpoints return `{ items, page, size, totalItems, totalPages, hasNext }`; page sizes are capped at 100.
- JSON request bodies are capped at 128 KiB and scene text at 100,000 characters. Questions are capped at 500 characters.
- Scene writes require an `Idempotency-Key` containing 8–128 safe ASCII characters. Repeating the exact request returns the original scene with `Idempotency-Replayed: true`; reusing the key for different input returns `409`.
- Every response carries `X-Request-ID`. Error bodies use a safe public message and include the same request ID for support correlation.
- The current fixed-window limiter is intentionally process-local because production currently runs one application container. It must move to Redis or an edge limiter before replicas are introduced in Phase 3.
- The backend and frontend must be released together because Phase 2 changes collection response shapes and makes the scene idempotency header mandatory.

Rollback is application-first: restore the previous assembled JAR and Compose/Nginx configuration, then recreate only the app and proxy containers. Migration V3 adds nullable columns and a unique constraint, so those columns can safely remain during an application rollback; do not drop them while rolling back.

## Phase 3 — durable data and workload reliability

- [x] Move the server profile from H2/in-memory vectors to PostgreSQL + pgvector
- [x] Store sessions in PostgreSQL before adding replicas
- [x] Move fixed-window rate-limit state to PostgreSQL for replica-safe enforcement
- [x] Automate encrypted backups, retention, restore drills, and migration checks
- [x] Move model processing to durable background jobs with bounded retries
- [x] Add idempotency, dead-letter handling, explicit processing states, and user-triggered requeue
- [x] Define retention and deletion behavior for accounts and narrative data

### Phase 3 operating contract

- Scene submission commits the raw scene and its queue record in one database transaction, returns `202 Accepted`, and reports one of `QUEUED`, `PROCESSING`, `RETRYING`, `PROCESSED`, or `DEAD_LETTER`.
- Workers claim jobs under a PostgreSQL row lock. A claim has a seven-minute lease, failed work retries at bounded exponential intervals, and the third failed attempt moves the scene to the dead-letter state without partially changing canon.
- `GET /stories/{storyId}/scenes/{sceneId}` is the authoritative processing status. An owner can requeue dead-letter work with `POST /stories/{storyId}/scenes/{sceneId}/retry`; tenant ownership remains concealed with `404`.
- Spring Security sessions and fixed-window rate-limit counters are stored in PostgreSQL. Application restarts no longer sign users out or reset abuse counters, and multiple app replicas share both controls.
- PostgreSQL is the sole production system of record for relational state and 768-dimension pgvector embeddings. H2 and the in-memory vector store remain explicit development/test adapters only.
- The backup container produces AES-256 encrypted custom-format dumps with SHA-256 sidecars every 24 hours, retains 14 days, checks the expected Flyway migration version, and restores each dump into an isolated verification database.

### Phase 3 retention and deletion policy

- Active account and narrative records have no automatic expiry; user-authored manuscripts are retained until an authenticated deletion workflow is introduced in Phase 5.
- Sessions expire after 12 hours of inactivity and are removed by the Spring Session cleanup task every 15 minutes.
- Rate-limit windows are disposable operational records and are pruned after expiration. Completed processing jobs are retained for 30 days; retry and dead-letter records remain until resolved so failures are not silently lost.
- Job error storage is restricted to a safe error code. It never stores model output, credentials, session identifiers, or additional manuscript text.
- Encrypted backups expire after 14 days. Once account deletion is implemented, deleted narrative data may remain only inside encrypted backups until that window elapses.
- Account deletion is deliberately not implied by database cascades. Phase 5 must provide an authenticated, audited deletion workflow before production records can be removed.

Rollback is application-first: restore the previous assembled JAR and recreate only the app container. Migration V4 adds durable session, rate-limit, and workload tables; leave them in place during application rollback. PostgreSQL data and encrypted backups must never be deleted as part of an application rollback.

## Phase 4 — operations and delivery

- Add readiness/liveness probes, metrics, traces, structured logs, and alerts
- Add CI gates for tests, dependency review, secret scanning, and container scanning
- Pin image versions, generate an SBOM, and run containers as non-root with minimal capabilities
- Automate immutable deployments, database-safe rollback, and smoke tests
- Document incident response, recovery objectives, and operational ownership

## Phase 5 — account lifecycle and collaboration

- [x] Hashed, expiring email-verification and password-reset tokens with an SMTP delivery boundary
- [x] One-time offline recovery codes and authenticated password changes
- [x] Active-device inventory and individual/all-other session revocation
- [x] Owner, editor, and viewer roles with email-bound, expiring story invitations
- [x] Portable account export and password-confirmed account deletion
- [x] Administrator account suspension/restoration and privacy-safe audit review
- [x] HMAC-pseudonymized IP metadata, 180-day audit retention, and no credentials/story text in audit events
- [ ] Configure and verify production SMTP delivery, enable verification enforcement, deploy, and complete an HTTPS smoke test

### Phase 5 operating contract

- Raw verification, reset, invitation, recovery, and session identifiers are never stored. Only SHA-256 token hashes or keyed HMAC pseudonyms are persisted, and API responses never expose emailed tokens. Expired tokens, stale invitation records, consumed recovery codes, and orphaned device metadata are pruned automatically.
- Existing accounts are migration-marked verified to avoid lockout. Once tested SMTP is enabled, new accounts must verify their email before creating, editing, or sharing stories.
- Viewers can read a shared story; editors can also add chapters and scenes. Only the owner can invite, revoke, or change permissions. Unauthorized story IDs remain concealed with `404`.
- Password reset, password change, account recovery, suspension, and deletion revoke server-side sessions. Recovery codes are displayed once and individually consumed.
- Account export excludes password hashes, session/token material, IP pseudonyms, and embedding vectors. Account deletion removes owned narrative data and identity records while de-identifying retained security events; encrypted backups expire after 14 days.
- Production email and administrator identities belong only in the server `.env`. The repository contains configuration names and safe placeholders, never provider credentials.

Rollback is application-first: restore the previous JAR and recreate only the app container. Migration V5 adds nullable/backfilled identity fields and separate lifecycle, membership, and audit tables; leave them in place during application rollback. Do not delete database state or encrypted backups during rollback.

## Legacy-data note

Migration `V2__add_user_ownership.sql` intentionally leaves existing stories unassigned. They are invisible after authentication is enabled, preventing the first public signup from claiming someone else's data. The previous demo data was explicitly purged before the authenticated release.
