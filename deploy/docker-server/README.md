# Docker server deployment

This Compose stack runs the ArcLedger application, Ollama inference and embedding models, the loopback Nginx proxy, and the Cloudflare fallback tunnel in Docker. H2 data and Ollama models are persisted in host bind mounts.

Place the assembled `arcledger.jar` (including the frontend static assets) in this directory, copy `.env.example` to `.env`, and set the two persistent host paths. Then run:

```sh
docker compose pull
docker compose up -d --build
```

The public host proxy should forward to `127.0.0.1:8080`. The one-shot `model-loader` container exits successfully after verifying that both models are present; that exited state is expected.

The proxy accepts at most 132 KiB so the application can enforce its normalized 128 KiB JSON limit. Phase 2 rate-limit settings are supplied to the app from `compose.yml`; they are process-local and assume this single app container. Scene idempotency records persist in the H2 data bind mount.
