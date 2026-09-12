# Docker server deployment

This Compose stack runs ArcLedger, PostgreSQL with pgvector, durable background processing, encrypted backups, Ollama inference and embedding models, the loopback Nginx proxy, and the Cloudflare fallback tunnel in Docker. Database files, encrypted backups, and Ollama models use explicit host bind mounts.

Place the assembled `arcledger.jar` (including the frontend static assets) in this directory, copy `.env.example` to `.env`, set the three persistent host paths, and generate independent random values for the database password and backup passphrase. Never commit `.env`.

Generate a separate random `ARCLEDGER_AUDIT_HASH_KEY` for privacy-preserving IP/session correlation. To activate email verification, password-reset email, and invitations, configure SMTP credentials in `.env`, test delivery, then set both `ARCLEDGER_MAIL_ENABLED=true` and `ARCLEDGER_REQUIRE_EMAIL_VERIFICATION=true`. Keep both flags false until delivery is working so new accounts cannot be stranded. `ARCLEDGER_ADMIN_EMAILS` is a comma-separated allowlist of existing accounts that receive the administrator role at startup.

Before the first startup, create the bind-mount directories with ownership matching UID 1001. Then run:

```sh
docker compose pull
docker compose up -d --build
```

The public host proxy should forward to `127.0.0.1:8080`. The one-shot `model-loader` container exits successfully after verifying that both models are present; that exited state is expected. Flyway migration validation must pass before the app becomes healthy.

The proxy accepts at most 132 KiB so the application can enforce its normalized 128 KiB JSON limit. Sessions and device metadata, rate-limit windows, hashed account tokens and recovery codes, story memberships, security audits, scene idempotency keys, model-work leases, retries, dead-letter states, canonical data, and vectors all persist in PostgreSQL and remain coordinated if application replicas are added later.

## Backups and restore drills

The `backup` container creates one encrypted PostgreSQL custom-format dump every 24 hours, writes a SHA-256 sidecar, retains 14 days, checks the Flyway state, and restores each new backup into an isolated verification database before reporting success. Raw dumps exist only in the container's temporary filesystem.

Run an immediate backup and restore drill:

```sh
docker compose run --rm --entrypoint /usr/local/bin/backup.sh backup
```

Validate migrations independently:

```sh
docker compose run --rm --entrypoint /usr/local/bin/migration-check.sh backup
```

Rollback is application-first: restore the previous JAR and recreate only `app`. Do not roll database migrations backward or replace the PostgreSQL bind mount. If database recovery is required, stop writers, preserve the current database directory, and restore a verified encrypted dump using the separately held passphrase.
