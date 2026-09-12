#!/bin/sh
set -eu

failed="$(psql --tuples-only --no-align --command="SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE")"
latest="$(psql --tuples-only --no-align --command="SELECT COALESCE((SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1), '0')")"

test "$failed" = "0"
test "$latest" = "5"
printf '%s\n' "database migrations healthy at version ${latest}"
