#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
commit=$(git rev-parse HEAD)
image="helpdesk:$commit"
./gradlew --no-daemon spotlessCheck build
docker build -t "$image" .
bash scripts/init-env.sh
bash scripts/deploy.sh "$image"
mkdir -p evidence
printf '%s\n' "$image" > evidence/deployed-image.txt
docker compose ps > evidence/containers.txt
printf '%s\n' 'Локальный pipeline завершён. Публикация в GHCR выполняется GitHub Actions.'
