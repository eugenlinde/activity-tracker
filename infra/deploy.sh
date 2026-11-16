#!/usr/bin/env bash
set -euo pipefail
umask 077

if [ $# -ne 1 ] || [[ ! $1 =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Usage: deploy.sh <image-tag>" >&2
  exit 1
fi

TAG=$1
HEALTH_ATTEMPTS=${HEALTH_ATTEMPTS:-24}
HEALTH_INTERVAL=${HEALTH_INTERVAL:-5}
NEW_COMPOSE=0

cd "$(dirname "$0")"

read_env() {
  { grep -E "^$1=" .env || true; } | head -n 1 | cut -d= -f2-
}

write_tag() {
  sed "s|^IMAGE_TAG=.*|IMAGE_TAG=$1|" .env > .env.tmp
  mv .env.tmp .env
}

install_compose() {
  if [ ! -f incoming/compose.yml ]; then
    return 0
  fi
  if [ -f compose.yml ]; then
    cp -p compose.yml compose.yml.prev
  else
    rm -f compose.yml.prev
  fi
  mv incoming/compose.yml compose.yml
  NEW_COMPOSE=1
}

restore_compose() {
  if [ "$NEW_COMPOSE" -eq 1 ] && [ -f compose.yml.prev ]; then
    mv compose.yml.prev compose.yml
  fi
}

wait_healthy() {
  local attempt
  for ((attempt = 1; attempt <= HEALTH_ATTEMPTS; attempt++)); do
    if curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep "$HEALTH_INTERVAL"
  done
  return 1
}

PREVIOUS=$(read_env IMAGE_TAG)
SITE_ADDRESS=$(read_env SITE_ADDRESS)
HEALTH_URL="https://$SITE_ADDRESS/actuator/health"

if [ -z "$PREVIOUS" ] || [ -z "$SITE_ADDRESS" ]; then
  echo "IMAGE_TAG or SITE_ADDRESS is missing from .env" >&2
  exit 1
fi

echo "Deploying $TAG (current: $PREVIOUS)"

if docker compose ps --status running --services 2>/dev/null | grep -qx postgres; then
  mkdir -p predeploy
  DUMP="predeploy/$(date -u +%Y%m%dT%H%M%SZ)-$PREVIOUS.sql.gz"
  docker compose exec -T postgres pg_dump -U tracker tracker | gzip > "$DUMP.tmp"
  mv "$DUMP.tmp" "$DUMP"
  echo "Database dumped to $DUMP"
  find predeploy -name '*.sql.gz' | sort -r | tail -n +6 | xargs -r rm --
else
  echo "Database is not running, no pre-deploy dump"
fi

install_compose

if ! IMAGE_TAG=$TAG docker compose pull; then
  restore_compose
  echo "Could not pull the images for $TAG, nothing was changed" >&2
  exit 1
fi

write_tag "$TAG"

if docker compose up -d --remove-orphans && wait_healthy; then
  docker image prune -af --filter until=168h >/dev/null
  echo "Deployed $TAG, health check UP"
  exit 0
fi

docker compose logs --tail 50 api > last-failed-deploy.log 2>&1 || true
echo "$TAG did not start healthy. Backend log saved on the server: $PWD/last-failed-deploy.log" >&2

if [ "$PREVIOUS" = "none" ]; then
  echo "No previous version to roll back to" >&2
  exit 1
fi

write_tag "$PREVIOUS"
restore_compose

if docker compose up -d --remove-orphans && wait_healthy; then
  echo "Rolled back to $PREVIOUS, health check UP" >&2
else
  echo "Rolled back to $PREVIOUS, but the health check is still not UP" >&2
fi
exit 1
