#!/bin/sh
set -eu

umask 077
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
raw_dump="/tmp/arcledger-${timestamp}.dump"
encrypted_dump="/backups/arcledger-${timestamp}.dump.enc"
trap 'rm -f "$raw_dump"' EXIT HUP INT TERM

pg_dump --format=custom --compress=6 --no-owner --no-acl --file="$raw_dump"
openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 \
  -in "$raw_dump" -out="$encrypted_dump" -pass env:BACKUP_PASSPHRASE
sha256sum "$encrypted_dump" > "${encrypted_dump}.sha256"

/usr/local/bin/migration-check.sh

find /backups -type f -name 'arcledger-*.dump.enc' -mtime "+${BACKUP_RETENTION_DAYS:-14}" -delete
find /backups -type f -name 'arcledger-*.dump.enc.sha256' -mtime "+${BACKUP_RETENTION_DAYS:-14}" -delete

if [ "${RUN_RESTORE_DRILL:-false}" = "true" ]; then
  /usr/local/bin/restore-verify.sh "$encrypted_dump"
fi

printf '%s\n' "$encrypted_dump"
