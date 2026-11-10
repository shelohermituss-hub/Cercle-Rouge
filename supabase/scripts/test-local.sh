#!/usr/bin/env bash
# Applique les migrations sur une base PostgreSQL locale jetable et lance supabase/tests/security.sql.
# Usage : PGHOST=/tmp/pg PGPORT=54322 PGUSER=postgres supabase/scripts/test-local.sh
set -euo pipefail
cd "$(dirname "$0")/.."
DB=cr_test_$$
createdb "$DB"
trap 'dropdb --if-exists "$DB"' EXIT
psql -q -v ON_ERROR_STOP=1 -d "$DB" -f tests/local_supabase_stub.sql
for m in migrations/*.sql; do psql -q -v ON_ERROR_STOP=1 -d "$DB" -f "$m"; done
psql -q -v ON_ERROR_STOP=1 -d "$DB" -f tests/security.sql
