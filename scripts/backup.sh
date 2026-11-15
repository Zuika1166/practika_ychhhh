#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
mkdir -p backups
file="${1:-backups/helpdesk-$(date -u +%Y%m%dT%H%M%SZ).dump}"
partial="$file.partial"
trap 'rm -f "$partial"' EXIT
docker compose exec -T db sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$partial"
test -s "$partial"
mv "$partial" "$file"
printf '%s\n' "$file"
