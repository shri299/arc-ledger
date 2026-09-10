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
- The backup container produces AES-256 encrypted custom-format dumps with SHA-256 sidecars every 24 hours, retains 14 days, checks Flyway migration version 4, and restores each dump into an isolated verification database.

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

- Email verification, password reset, recovery, and session/device management
- Roles and explicit story-sharing permissions
- Account export and deletion workflows
- Administrative abuse controls and privacy/audit review

## Legacy-data note

Migration `V2__add_user_ownership.sql` intentionally leaves existing stories unassigned. They are invisible after authentication is enabled, preventing the first public signup from claiming someone else's data. The previous demo data was explicitly purged before the authenticated release.
