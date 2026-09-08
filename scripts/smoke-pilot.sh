#!/usr/bin/env bash
set -euo pipefail

base_url="${1:?Kullanım: bash scripts/smoke-pilot.sh https://api-or-your-proxy.example}"
base_url="${base_url%/}"

curl --fail --silent --show-error "$base_url/api/health" >/dev/null
curl --fail --silent --show-error "$base_url/api/questions?size=1" >/dev/null
curl --fail --silent --show-error --cookie-jar /dev/null "$base_url/api/auth/csrf" >/dev/null

printf 'Pilot smoke test passed: %s\n' "$base_url"
