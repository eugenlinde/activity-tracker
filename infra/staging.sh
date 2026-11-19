#!/usr/bin/env bash
set -euo pipefail
umask 077
export LC_ALL=C

TRACKER_DIR=${TRACKER_DIR:-/opt/tracker}
STAGING_DIR=${STAGING_DIR:-/opt/tracker-staging}
if [ -n "${SSH_CONNECTION:-}" ]; then
  TRACKER_DIR=/opt/tracker
  STAGING_DIR=/opt/tracker-staging
  PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
fi
REQUEST=${SSH_ORIGINAL_COMMAND:-}
DEPLOY_REQUEST='^deploy (pr-([1-9][0-9]{0,6})-[0-9a-f]{40}) ([1-9][0-9]{0,6})$'

if [ "$STAGING_DIR" = "$TRACKER_DIR" ] || [ ! -f "$STAGING_DIR/.env" ]; then
  echo "Staging is not set up on this server" >&2
  exit 1
fi

if [ "$REQUEST" != "reset" ] && { [[ ! $REQUEST =~ $DEPLOY_REQUEST ]] || [ "${BASH_REMATCH[2]}" != "${BASH_REMATCH[3]}" ]; }; then
  echo "Refused: this key can only run 'deploy <tag> <pr>' or 'reset' on staging" >&2
  exit 2
fi

cd "$STAGING_DIR"
export COMPOSE_PROJECT_NAME=tracker-staging
export COMPOSE_FILE=base.compose.yml:staging.compose.yml

if command -v flock >/dev/null 2>&1; then
  exec 9> .lock
  if ! flock -n 9; then
    echo "result=busy"
    exit 1
  fi
fi

if [ "$REQUEST" = "reset" ]; then
  if [ -f base.compose.yml ] && [ -f staging.compose.yml ]; then
    docker compose down --volumes --remove-orphans > last-reset.log 2>&1
    if [ "$({ grep -E '^IMAGE_TAG=' .env || true; } | head -n 1 | cut -d= -f2-)" != "none" ]; then
      docker compose up -d >> last-reset.log 2>&1
    fi
  fi
  echo "result=reset"
  exit 0
fi

TAG=${BASH_REMATCH[1]}
PR=${BASH_REMATCH[3]}
PREVIOUS_PR=none
if [ -f current-pr ] && [[ $(cat current-pr) =~ ^[1-9][0-9]{0,6}$ ]]; then
  PREVIOUS_PR=$(cat current-pr)
fi
echo "previous-pr=$PREVIOUS_PR"

install_from_production() {
  cp -p "$TRACKER_DIR/$1" "$2.incoming"
  mv "$2.incoming" "$2"
}
install_from_production compose.yml base.compose.yml
install_from_production compose.staging.yml staging.compose.yml
install_from_production deploy.sh deploy.sh

if ./deploy.sh "$TAG" > last-deploy.log 2>&1; then
  echo "$PR" > current-pr
  echo "result=deployed"
  exit 0
fi

if grep -q '^Could not pull the images for ' last-deploy.log; then
  echo "result=pull-failed"
elif grep -q '^Rolled back to .*, health check UP$' last-deploy.log; then
  echo "result=rolled-back"
else
  echo "result=failed"
fi
exit 1
