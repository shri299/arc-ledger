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
- [ ] Assign pre-authentication stories to a chosen account before deployment
- [ ] Deploy and run an HTTPS end-to-end smoke test

## Phase 2 — API hardening

- Rate-limit login, signup, question answering, and scene processing
- Add consistent, non-sensitive validation errors and correlation IDs
- Enforce request/body limits, query limits, timeouts, and pagination
- Add security headers and a reviewed Content Security Policy
- Add structured security audit events without credentials or story text
- Define idempotency behavior for expensive write operations

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

## Deployment gate for Phase 1

Migration `V2__add_user_ownership.sql` intentionally leaves existing stories unassigned. They are invisible after authentication is enabled, preventing the first public signup from claiming someone else's data. Before deploying Phase 1, choose the account that owns the current server stories and run an explicit, reviewed data migration after that account exists.
