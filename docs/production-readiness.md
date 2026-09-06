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

- Move the server profile from H2/in-memory vectors to PostgreSQL + pgvector
- Store sessions in PostgreSQL or Redis before adding replicas
- Automate encrypted backups, retention, restore drills, and migration checks
- Move model processing to durable background jobs with bounded retries
- Add idempotency, dead-letter handling, and explicit processing states
- Define retention and deletion behavior for accounts and narrative data

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
