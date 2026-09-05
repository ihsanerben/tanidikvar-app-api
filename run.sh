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
if [[ "${1:-}" == "--docker" ]]; then
  : "${JWT_SECRET:?JWT_SECRET zorunlu}"
  if [[ ! -f "${WEB_BUILD_CONTEXT:-../tanidikvar-app-web}/Dockerfile" ]]; then
    echo "Web Dockerfile bulunamadı. .env içindeki WEB_BUILD_CONTEXT yolunu kontrol et." >&2
    exit 1
  fi
  export DOCKER_WEB_ORIGIN="${DOCKER_WEB_ORIGIN:-http://localhost:${DOCKER_WEB_PORT:-5173}}"
  docker compose --profile app up -d --build --wait
  echo "TanıdıkVar hazır: ${DOCKER_WEB_ORIGIN}"
  exit 0
fi
if [[ $# -gt 0 ]]; then
  echo "Kullanım: ./run.sh [--docker]" >&2
  exit 1
fi
if lsof -nP -iTCP:"${SERVER_PORT:-8080}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "API portu ${SERVER_PORT:-8080} kullanımda. .env ayarını kontrol et." >&2
  exit 1
fi
docker compose up -d --wait postgres mailpit
export DB_URL="${DB_URL:-jdbc:postgresql://localhost:${DB_PORT:-55432}/${DB_NAME}}"
export SPRING_PROFILES_ACTIVE=local
exec ./mvnw spring-boot:run
