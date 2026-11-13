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

cd "$(dirname "$0")"

read_env() {
  grep -E "^$1=" .env | head -n 1 | cut -d= -f2-
}

write_tag() {
  sed "s|^IMAGE_TAG=.*|IMAGE_TAG=$1|" .env > .env.tmp
  mv .env.tmp .env
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

IMAGE_TAG=$TAG docker compose pull
write_tag "$TAG"
docker compose up -d --remove-orphans

if wait_healthy; then
  docker image prune -af --filter until=168h >/dev/null
  echo "Deployed $TAG, health check UP"
  exit 0
fi

echo "Health check did not report UP for $TAG. Last lines of the api log:" >&2
docker compose logs --tail 50 api >&2 || true

if [ "$PREVIOUS" = "none" ]; then
  echo "No previous version to roll back to" >&2
  exit 1
fi

write_tag "$PREVIOUS"
docker compose up -d --remove-orphans

if wait_healthy; then
  echo "Rolled back to $PREVIOUS, health check UP" >&2
else
  echo "Rolled back to $PREVIOUS, but the health check is still not UP" >&2
fi
exit 1
