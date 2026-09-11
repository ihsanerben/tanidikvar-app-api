#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
usage() {
  echo 'Kullanım: ./run.sh [--docker | --status | --stop | --help]'
  echo '  (seçeneksiz) API yerelde, PostgreSQL ve Mailpit Docker üzerinden başlar.'
  echo '  --docker     PostgreSQL, Mailpit ve API servislerini Docker üzerinden başlatır.'
  echo '  --status     Çalışan ve durmuş Docker servislerini gösterir; değişiklik yapmaz.'
  echo '  --stop       Docker servislerini durdurur; veritabanı ve dosya volume’ları korunur.'
}
if [[ $# -gt 1 ]]; then usage >&2; exit 1; fi
case "${1:-}" in
  --help|-h) usage; exit 0 ;;
  --status|--stop)
    if [[ ! -f .env ]]; then
      echo 'Yerel ayarlar bulunamadı. İlk kurulum için ./run.sh --docker çalıştır.' >&2
      exit 1
    fi
    if [[ "$1" == '--status' ]]; then
      docker compose --profile app ps --all
    else
      docker compose --profile app stop
      echo 'Docker servisleri durduruldu; kalıcı veriler korundu.'
      echo 'Ayrı terminalde çalışan ./run.sh veya npm run dev süreçleri Ctrl+C ile durdurulur.'
    fi
    exit 0 ;;
  ''|--docker) ;;
  *) usage >&2; exit 1 ;;
esac
./scripts/setup-local.sh
set -a
source .env
set +a
: "${DB_PASSWORD:?DB_PASSWORD zorunlu}"
: "${DB_NAME:?DB_NAME zorunlu}"
: "${DB_USERNAME:?DB_USERNAME zorunlu}"
if [[ "${1:-}" == "--docker" ]]; then
  : "${JWT_SECRET:?JWT_SECRET zorunlu}"
  docker compose --profile app up -d --build --wait
  echo "TanıdıkVar API hazır: http://localhost:${SERVER_PORT:-8080}"
  exit 0
fi
if lsof -nP -iTCP:"${SERVER_PORT:-8080}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "API portu ${SERVER_PORT:-8080} kullanımda. .env ayarını kontrol et." >&2
  exit 1
fi
docker compose up -d --wait postgres mailpit
export DB_URL="${DB_URL:-jdbc:postgresql://localhost:${DB_PORT:-55432}/${DB_NAME}}"
export SPRING_PROFILES_ACTIVE=local
exec ./mvnw spring-boot:run
