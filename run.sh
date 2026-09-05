#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./scripts/setup-local.sh
set -a
source .env
set +a
: "${DB_PASSWORD:?DB_PASSWORD zorunlu}"
: "${DB_NAME:?DB_NAME zorunlu}"
: "${DB_USERNAME:?DB_USERNAME zorunlu}"
if lsof -nP -iTCP:"${SERVER_PORT:-8080}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "API portu ${SERVER_PORT:-8080} kullanımda. .env ayarını kontrol et." >&2
  exit 1
fi
docker compose up -d --wait postgres mailpit
export DB_URL="${DB_URL:-jdbc:postgresql://localhost:${DB_PORT:-55432}/${DB_NAME}}"
export SPRING_PROFILES_ACTIVE=local
exec ./mvnw spring-boot:run
