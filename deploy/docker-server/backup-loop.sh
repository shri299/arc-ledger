#!/bin/sh
set -eu

sleep "${BACKUP_INITIAL_DELAY_SECONDS:-300}"
while true; do
  /usr/local/bin/backup.sh
  sleep "${BACKUP_INTERVAL_SECONDS:-86400}"
done
