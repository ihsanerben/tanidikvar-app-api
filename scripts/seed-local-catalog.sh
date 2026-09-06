#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ $# -ne 0 ]]; then
  echo 'Kullanım: ./scripts/seed-local-catalog.sh' >&2
  exit 1
fi
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 --single-transaction' < scripts/seed-local-catalog.sql
echo 'Başlangıç kataloğu eklendi: 10 üniversite, 10 bölüm, 75 eşleşme, 19 tag. Mevcut kayıtlar korunur.'
