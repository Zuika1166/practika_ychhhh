#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
image=${1:?Укажите образ с тегом коммита}
[[ "$image" =~ ^[a-zA-Z0-9][a-zA-Z0-9._/:@-]+$ ]] || exit 1
mkdir -p .deploy
exec 9>.deploy/lock
flock -n 9 || { printf '%s\n' 'Другое развёртывание уже выполняется'; exit 1; }
previous=$(docker compose ps -q app | xargs -r docker inspect --format '{{.Config.Image}}' | head -n 1)
export APP_IMAGE="$image"
if ! docker image inspect "$image" >/dev/null 2>&1; then
    docker pull "$image"
fi
if docker compose up -d --no-build --wait --wait-timeout 180 && python3 scripts/smoke.py; then
    if [ -n "$previous" ]; then printf '%s\n' "$previous" > .deploy/previous-image; fi
    python3 - <<'ENV'
import os
from pathlib import Path
path = Path('.env')
lines = [line for line in path.read_text().splitlines() if not line.startswith('APP_IMAGE=')]
lines.append('APP_IMAGE=' + os.environ['APP_IMAGE'])
path.write_text('\n'.join(lines) + '\n')
path.chmod(0o600)
ENV
    printf '%s\n' "$image" > .deploy/current-image
    printf '%s\n' "Развёрнут образ: $image"
else
    docker compose logs --tail=100 app
    if [ -n "$previous" ]; then
        APP_IMAGE="$previous" docker compose up -d --no-build --wait --wait-timeout 180
        printf '%s\n' "Восстановлен образ: $previous"
    fi
    exit 1
fi
