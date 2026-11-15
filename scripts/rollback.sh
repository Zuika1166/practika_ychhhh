#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
image=${1:-$(cat .deploy/previous-image)}
bash scripts/deploy.sh "$image"
