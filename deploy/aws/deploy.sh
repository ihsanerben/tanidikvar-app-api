#!/usr/bin/env sh
set -eu

if [ ! -f .env.production ]; then
  echo "Missing deploy/aws/.env.production. Copy .env.production.example and fill it on the server."
  exit 1
fi

docker compose --env-file .env.production -f compose.yaml up --build --detach --remove-orphans
docker compose --env-file .env.production -f compose.yaml ps
