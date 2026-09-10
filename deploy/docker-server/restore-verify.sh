#!/bin/sh
set -eu

encrypted_dump="${1:?Pass an encrypted backup path}"
test -f "$encrypted_dump"
sha256sum -c "${encrypted_dump}.sha256"

umask 077
raw_dump="/tmp/arcledger-restore-verify.dump"
verify_database="arcledger_restore_verify"
cleanup() {
  rm -f "$raw_dump"
  dropdb --if-exists "$verify_database" >/dev/null 2>&1 || true
}
trap cleanup EXIT HUP INT TERM

openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -in "$encrypted_dump" -out="$raw_dump" -pass env:BACKUP_PASSPHRASE
dropdb --if-exists "$verify_database"
createdb "$verify_database"
pg_restore --exit-on-error --no-owner --no-acl --dbname="$verify_database" "$raw_dump"

test "$(psql --dbname="$verify_database" --tuples-only --no-align \
  --command="SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version = '4'")" = "1"
test "$(psql --dbname="$verify_database" --tuples-only --no-align \
  --command="SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'")" = "1"
test "$(psql --dbname="$verify_database" --tuples-only --no-align \
  --command="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'scene_processing_jobs'")" = "1"

printf '%s\n' "restore verification passed"
