#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
file=${1:?Укажите путь к резервной копии}
target=${2:-helpdesk_restore_check}
[[ "$target" =~ ^[a-z][a-z0-9_]{0,50}$ ]] || { printf '%s\n' 'Недопустимое имя базы'; exit 1; }
test -s "$file"
docker compose exec -T db sh -c 'test "$1" != "$POSTGRES_DB"' sh "$target" || { printf '%s\n' 'Восстановление в рабочую базу запрещено'; exit 1; }
docker compose exec -T db sh -c 'exec createdb -U "$POSTGRES_USER" "$1"' sh "$target"
docker compose exec -T db sh -c 'exec pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --no-privileges --exit-on-error' sh "$target" < "$file"
docker compose exec -T db sh -c 'exec psql -U "$POSTGRES_USER" -d "$1" -v ON_ERROR_STOP=1 -c "SELECT count(*) AS tickets FROM tickets; SELECT count(*) AS history FROM status_history;"' sh "$target"
