#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -e .env ]; then
    printf '%s\n' '.env уже существует'
    exit 0
fi
umask 077
cp .env.example .env
secret=$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')
sed -i "s/^DB_PASSWORD=$/DB_PASSWORD=$secret/" .env
printf '%s\n' '.env создан. Пароль базы сгенерирован локально.'
