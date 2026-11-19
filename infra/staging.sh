#!/usr/bin/env bash
set -euo pipefail
umask 077

TRACKER_DIR=${TRACKER_DIR:-/opt/tracker}
STAGING_DIR=${STAGING_DIR:-/opt/tracker-staging}
REQUEST=${SSH_ORIGINAL_COMMAND:-}
DEPLOY_REQUEST='^deploy (pr-([0-9]{1,7})-[0-9a-f]{40}) ([0-9]{1,7})$'

if [ ! -f "$STAGING_DIR/.env" ]; then
  echo "Staging is not set up on this server" >&2
  exit 1
fi

if [ "$REQUEST" != "reset" ] && { [[ ! $REQUEST =~ $DEPLOY_REQUEST ]] || [ "${BASH_REMATCH[2]}" != "${BASH_REMATCH[3]}" ]; }; then
  echo "Refused: this key can only run 'deploy <tag> <pr>' or 'reset' on staging" >&2
  exit 2
fi

cd "$STAGING_DIR"
export COMPOSE_PROJECT_NAME=tracker-staging
export COMPOSE_FILE=compose.yml:compose.staging.yml

if [ "$REQUEST" = "reset" ]; then
  docker compose down --volumes --remove-orphans > last-reset.log 2>&1
  if [ "$({ grep -E '^IMAGE_TAG=' .env || true; } | head -n 1 | cut -d= -f2-)" != "none" ]; then
    docker compose up -d >> last-reset.log 2>&1
  fi
  echo "result=reset"
  exit 0
fi

TAG=${BASH_REMATCH[1]}
PR=${BASH_REMATCH[3]}
PREVIOUS_PR=none
if [ -f current-pr ] && [[ $(cat current-pr) =~ ^[0-9]{1,7}$ ]]; then
  PREVIOUS_PR=$(cat current-pr)
fi
echo "previous-pr=$PREVIOUS_PR"

for file in compose.yml compose.staging.yml deploy.sh; do
  cp -p "$TRACKER_DIR/$file" "$file.incoming"
  mv "$file.incoming" "$file"
done

if ./deploy.sh "$TAG" > last-deploy.log 2>&1; then
  echo "$PR" > current-pr
  echo "result=deployed"
  exit 0
fi

if grep -q '^Rolled back to .*, health check UP$' last-deploy.log; then
  echo "result=rolled-back"
else
  echo "result=failed"
fi
exit 1
