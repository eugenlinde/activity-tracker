#!/usr/bin/env bash
set -uo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
INFRA=$(dirname "$HERE")
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

PASSED=0
FAILED=0
CASE=""
CASE_OK=1
STATUS=0
OUTPUT=""
TRICKY_PASSWORD="p\$w+/d=="

begin() {
  CASE=$1
  CASE_OK=1
  APP="$WORK/$RANDOM$RANDOM"
  mkdir -p "$APP/media"
  cp "$INFRA"/deploy.sh "$INFRA"/backup.sh "$INFRA"/restore.sh "$INFRA"/staging.sh "$APP/"
  printf 'SITE_ADDRESS=tracker.test\nIMAGE_TAG=old\nPOSTGRES_PASSWORD=pg\nJWT_SECRET=abc+/=\n' > "$APP/.env"
  chmod 600 "$APP/.env"
  echo "old-compose" > "$APP/compose.yml"
  printf 'RESTIC_REPOSITORY=/tmp/repo\nRESTIC_PASSWORD=%s\n' "$TRICKY_PASSWORD" > "$APP/backup.env"
  : > "$APP/stub.log"
  unset STUB_UP_FAILS_TAG STUB_EDGE_MISSING SSH_ORIGINAL_COMMAND TRACKER_DIR STAGING_DIR APP_DIR APP_USER AUTHORIZED_KEYS COMPOSE_PROJECT_NAME COMPOSE_FILE STUB_DB_RUNNING STUB_DUMP_FAILS STUB_PULL_FAILS STUB_HEALTHY_TAGS STUB_RESTIC_FAILS STUB_WEEKDAY STUB_SNAPSHOT_HAS_DUMP
  export STUB_LOG="$APP/stub.log" STUB_APP_DIR="$APP" STUB_MEDIA_DIR="$APP/media"
  export HEALTH_ATTEMPTS=2 HEALTH_INTERVAL=0
}

run() {
  OUTPUT=$(PATH="$HERE/stubs:$PATH" "$@" 2>&1)
  STATUS=$?
}

fail() {
  CASE_OK=0
  echo "  FAIL [$CASE] $1"
}

expect_status() {
  if [ "$STATUS" -ne "$1" ]; then fail "exit status $STATUS, expected $1"; fi
}

expect_failure() {
  if [ "$STATUS" -eq 0 ]; then fail "exit status 0, expected a failure"; fi
}

expect_tag() {
  local tag
  tag=$(grep -E '^IMAGE_TAG=' "$APP/.env" | cut -d= -f2-)
  if [ "$tag" != "$1" ]; then fail "IMAGE_TAG is $tag, expected $1"; fi
}

expect_log() {
  if ! grep -qF -- "$1" "$APP/stub.log"; then fail "expected call: $1"; fi
}

expect_no_log() {
  if grep -qF -- "$1" "$APP/stub.log"; then fail "unexpected call: $1"; fi
}

expect_log_count() {
  local count
  count=$(grep -cF -- "$1" "$APP/stub.log")
  if [ "$count" -ne "$2" ]; then fail "call '$1' seen $count times, expected $2"; fi
}

expect_output() {
  case "$OUTPUT" in
    *"$1"*) ;;
    *) fail "output lacks: $1" ;;
  esac
}

expect_no_output() {
  case "$OUTPUT" in
    *"$1"*) fail "output must not contain: $1" ;;
  esac
}

expect_content() {
  if [ "$(cat "$1" 2>/dev/null)" != "$2" ]; then fail "$1 does not contain exactly: $2"; fi
}

incoming_compose() {
  mkdir -p "$APP/incoming"
  echo "new-compose" > "$APP/incoming/compose.yml"
}

expect_file() {
  if [ ! -e "$1" ]; then fail "missing file: $1"; fi
}

expect_no_file() {
  if [ -e "$1" ]; then fail "file should not exist: $1"; fi
}

end() {
  if [ "$CASE_OK" -eq 1 ]; then
    PASSED=$((PASSED + 1))
    echo "  ok   $CASE"
  else
    FAILED=$((FAILED + 1))
    printf '%s\n' "$OUTPUT" | while IFS= read -r line; do echo "       | $line"; done
  fi
}

echo "deploy.sh"

begin "healthy version is deployed"
export STUB_HEALTHY_TAGS="new"
run "$APP/deploy.sh" new
expect_status 0
expect_tag new
expect_log "pull-tag new"
expect_log_count "up-tag" 1
expect_log "up-tag new"
expect_log "docker image prune -af --filter until=168h"
expect_file "$APP/predeploy/20261113T120000Z-old.sql.gz"
if [ "$(gzip -dc "$APP/predeploy/20261113T120000Z-old.sql.gz" 2>/dev/null)" != "-- fake dump" ]; then fail "dump content is wrong"; fi
end

begin "the rest of .env survives and stays private"
export STUB_HEALTHY_TAGS="new"
run "$APP/deploy.sh" new
if [ "$(grep -c '' "$APP/.env")" -ne 4 ]; then fail ".env line count changed"; fi
if ! grep -qxF 'JWT_SECRET=abc+/=' "$APP/.env"; then fail "JWT_SECRET line changed"; fi
if [ -n "$(find "$APP/.env" -perm -004)" ] || [ -n "$(find "$APP/.env" -perm -040)" ]; then fail ".env is readable by others"; fi
end

begin "unhealthy version is rolled back"
export STUB_HEALTHY_TAGS="old"
run "$APP/deploy.sh" broken
expect_status 1
expect_tag old
expect_log_count "up-tag" 2
expect_log "up-tag broken"
expect_log "up-tag old"
expect_no_log "image prune"
expect_output "Rolled back to old, health check UP"
end

begin "backend log of a failed deploy stays on the server"
export STUB_HEALTHY_TAGS="old"
run "$APP/deploy.sh" broken
expect_no_output "fake api log line"
expect_output "last-failed-deploy.log"
expect_content "$APP/last-failed-deploy.log" "fake api log line"
if [ -n "$(find "$APP/last-failed-deploy.log" -perm -004)" ] || [ -n "$(find "$APP/last-failed-deploy.log" -perm -040)" ]; then fail "the saved log is readable by others"; fi
end

begin "containers that fail to start are rolled back"
export STUB_UP_FAILS_TAG=broken STUB_HEALTHY_TAGS="old"
run "$APP/deploy.sh" broken
expect_status 1
expect_tag old
expect_log "up-tag broken"
expect_log "up-tag old"
expect_output "Rolled back to old, health check UP"
end

begin "new compose file replaces the old one after the dump"
incoming_compose
export STUB_HEALTHY_TAGS="new"
run "$APP/deploy.sh" new
expect_status 0
expect_log "dump-compose old-compose"
expect_log "pull-compose new-compose"
expect_log "up-compose new-compose"
expect_content "$APP/compose.yml" "new-compose"
expect_no_file "$APP/incoming/compose.yml"
end

begin "rollback also brings back the previous compose file"
incoming_compose
export STUB_HEALTHY_TAGS="old"
run "$APP/deploy.sh" broken
expect_status 1
expect_log "up-compose new-compose"
expect_content "$APP/compose.yml" "old-compose"
if [ "$(grep -F 'up-compose' "$APP/stub.log" | tail -n 1)" != "up-compose old-compose" ]; then fail "the rollback did not start the old compose file"; fi
end

begin "failed pull brings back the previous compose file"
incoming_compose
export STUB_PULL_FAILS=1
run "$APP/deploy.sh" new
expect_failure
expect_tag old
expect_content "$APP/compose.yml" "old-compose"
end

begin "deploy without a new compose file keeps the current one on rollback"
echo "stale-compose" > "$APP/compose.yml.prev"
export STUB_HEALTHY_TAGS="old"
run "$APP/deploy.sh" broken
expect_status 1
expect_content "$APP/compose.yml" "old-compose"
end

begin "missing IMAGE_TAG in .env is reported"
printf 'SITE_ADDRESS=tracker.test\n' > "$APP/.env"
run "$APP/deploy.sh" new
expect_status 1
expect_output "missing from .env"
expect_no_log "docker"
end

begin "rollback that stays unhealthy says so"
export STUB_HEALTHY_TAGS=""
run "$APP/deploy.sh" broken
expect_status 1
expect_tag old
expect_output "still not UP"
end

begin "first deploy that fails has nothing to roll back to"
sed 's/^IMAGE_TAG=.*/IMAGE_TAG=none/' "$APP/.env" > "$APP/.env.new" && mv "$APP/.env.new" "$APP/.env"
export STUB_HEALTHY_TAGS="" STUB_DB_RUNNING=0
run "$APP/deploy.sh" first
expect_status 1
expect_log_count "up-tag" 1
expect_output "No previous version"
end

begin "failed pull changes nothing"
export STUB_PULL_FAILS=1 STUB_HEALTHY_TAGS="new old"
run "$APP/deploy.sh" new
expect_failure
expect_tag old
expect_no_log "up-tag"
end

begin "failed dump stops the deploy"
export STUB_DUMP_FAILS=1 STUB_HEALTHY_TAGS="new old"
run "$APP/deploy.sh" new
expect_failure
expect_tag old
expect_no_log "compose pull"
expect_no_log "up-tag"
expect_no_file "$APP/predeploy/20261113T120000Z-old.sql.gz"
end

begin "no dump when the database is not running"
export STUB_DB_RUNNING=0 STUB_HEALTHY_TAGS="new"
run "$APP/deploy.sh" new
expect_status 0
expect_no_log "pg_dump"
expect_tag new
end

begin "only the five newest dumps are kept"
mkdir -p "$APP/predeploy"
for day in 01 02 03 04 05 06 07; do
  : > "$APP/predeploy/202611${day}T000000Z-x.sql.gz"
done
export STUB_HEALTHY_TAGS="new"
run "$APP/deploy.sh" new
expect_status 0
if [ "$(find "$APP/predeploy" -name '*.sql.gz' | wc -l | tr -d ' ')" -ne 5 ]; then fail "dump count is not 5"; fi
expect_file "$APP/predeploy/20261113T120000Z-old.sql.gz"
expect_file "$APP/predeploy/20261104T000000Z-x.sql.gz"
expect_no_file "$APP/predeploy/20261103T000000Z-x.sql.gz"
end

begin "a tag with shell characters is refused"
run "$APP/deploy.sh" 'x;touch pwned'
expect_status 1
expect_tag old
expect_no_log "docker"
run "$APP/deploy.sh"
expect_status 1
end

begin "the shared network is created when it is missing"
export STUB_HEALTHY_TAGS="new" STUB_EDGE_MISSING=1
run "$APP/deploy.sh" new
expect_status 0
expect_log "docker network create tracker-edge"
if [ "$(grep -nF 'network create tracker-edge' "$APP/stub.log" | head -n 1 | cut -d: -f1)" -gt "$(grep -nF 'up-tag new' "$APP/stub.log" | head -n 1 | cut -d: -f1)" ]; then fail "the network was created after the containers were started"; fi
end

begin "the shared network is left alone when it exists"
export STUB_HEALTHY_TAGS="new"
run "$APP/deploy.sh" new
expect_status 0
expect_no_log "network create"
end

begin "health check uses the site address by default"
export STUB_HEALTHY_TAGS="new"
run "$APP/deploy.sh" new
expect_log "https://tracker.test/actuator/health"
end

begin "health check uses HEALTH_URL from .env when it is set"
echo 'HEALTH_URL=https://staging.test/actuator/health' >> "$APP/.env"
export STUB_HEALTHY_TAGS="new"
run "$APP/deploy.sh" new
expect_status 0
expect_log "https://staging.test/actuator/health"
expect_no_log "https://tracker.test/actuator/health"
end

begin "no dump when PREDEPLOY_DUMP is off"
echo 'PREDEPLOY_DUMP=off' >> "$APP/.env"
export STUB_HEALTHY_TAGS="new"
run "$APP/deploy.sh" new
expect_status 0
expect_no_log "pg_dump"
expect_no_file "$APP/predeploy"
end

begin "the staging override file travels with the compose file"
incoming_compose
echo "new-staging-override" > "$APP/incoming/compose.staging.yml"
echo "old-staging-override" > "$APP/compose.staging.yml"
export STUB_HEALTHY_TAGS="new"
run "$APP/deploy.sh" new
expect_status 0
expect_content "$APP/compose.staging.yml" "new-staging-override"
expect_no_file "$APP/incoming/compose.staging.yml"
end

begin "rollback also brings back the previous staging override file"
incoming_compose
echo "new-staging-override" > "$APP/incoming/compose.staging.yml"
echo "old-staging-override" > "$APP/compose.staging.yml"
export STUB_HEALTHY_TAGS="old"
run "$APP/deploy.sh" broken
expect_status 1
expect_content "$APP/compose.yml" "old-compose"
expect_content "$APP/compose.staging.yml" "old-staging-override"
end

echo "staging.sh"

SHA_A=0123456789abcdef0123456789abcdef01234567
SHA_B=89abcdef0123456789abcdef0123456789abcdef

staging() {
  STAGING="$APP/staging"
  mkdir -p "$STAGING"
  printf 'SITE_ADDRESS=:80\nIMAGE_TAG=%s\nPOSTGRES_PASSWORD=stagingpg\nJWT_SECRET=stagingjwt\nHEALTH_URL=https://staging.test/actuator/health\nPREDEPLOY_DUMP=off\n' "$1" > "$STAGING/.env"
  chmod 600 "$STAGING/.env"
  echo "main-staging-override" > "$APP/compose.staging.yml"
  export TRACKER_DIR="$APP" STAGING_DIR="$STAGING" STUB_APP_DIR="$STAGING"
}

begin "a PR is put on staging with the files production runs"
staging none
export STUB_HEALTHY_TAGS="pr-12-$SHA_A" SSH_ORIGINAL_COMMAND="deploy pr-12-$SHA_A 12"
run "$APP/staging.sh"
expect_status 0
expect_output "result=deployed"
expect_output "previous-pr=none"
expect_content "$STAGING/current-pr" "12"
expect_content "$STAGING/compose.yml" "old-compose"
expect_content "$STAGING/compose.staging.yml" "main-staging-override"
expect_log "up-project tracker-staging files compose.yml:compose.staging.yml"
expect_log "https://staging.test/actuator/health"
expect_no_log "pg_dump"
if [ "$(grep -E '^IMAGE_TAG=' "$STAGING/.env" | cut -d= -f2-)" != "pr-12-$SHA_A" ]; then fail "staging IMAGE_TAG was not set"; fi
if [ "$(grep -E '^IMAGE_TAG=' "$APP/.env" | cut -d= -f2-)" != "old" ]; then fail "production's IMAGE_TAG was touched"; fi
end

begin "the PR that had the slot is named"
staging "pr-12-$SHA_A"
echo 12 > "$STAGING/current-pr"
export STUB_HEALTHY_TAGS="pr-15-$SHA_B" SSH_ORIGINAL_COMMAND="deploy pr-15-$SHA_B 15"
run "$APP/staging.sh"
expect_status 0
expect_output "previous-pr=12"
expect_content "$STAGING/current-pr" "15"
end

begin "a PR that does not start leaves the previous one running"
staging "pr-12-$SHA_A"
echo 12 > "$STAGING/current-pr"
export STUB_HEALTHY_TAGS="pr-12-$SHA_A" SSH_ORIGINAL_COMMAND="deploy pr-15-$SHA_B 15"
run "$APP/staging.sh"
expect_status 1
expect_output "result=rolled-back"
expect_output "previous-pr=12"
expect_content "$STAGING/current-pr" "12"
if [ "$(grep -E '^IMAGE_TAG=' "$STAGING/.env" | cut -d= -f2-)" != "pr-12-$SHA_A" ]; then fail "staging did not go back to the previous tag"; fi
end

begin "a first PR that does not start is reported as failed"
staging none
export STUB_HEALTHY_TAGS="" SSH_ORIGINAL_COMMAND="deploy pr-12-$SHA_A 12"
run "$APP/staging.sh"
expect_status 1
expect_output "result=failed"
expect_no_file "$STAGING/current-pr"
end

begin "the deploy log stays on the server"
staging none
export STUB_HEALTHY_TAGS="pr-12-$SHA_A" SSH_ORIGINAL_COMMAND="deploy pr-12-$SHA_A 12"
run "$APP/staging.sh"
expect_no_output "Deploying"
expect_file "$STAGING/last-deploy.log"
if ! grep -q "Deployed pr-12-$SHA_A" "$STAGING/last-deploy.log"; then fail "the deploy log was not kept"; fi
end

for REQUEST in "" "deploy" "deploy pr-12-$SHA_A" "deploy pr-12-$SHA_A 13" "deploy pr-12-$SHA_A 12 extra" \
  "deploy pr-12-$SHA_A 12; touch $WORK/owned" "deploy pr-12-$SHA_A 12 && id" "deploy \$(id) 12" "deploy main-$SHA_A 12" \
  "deploy $SHA_A 12" "deploy pr-12-0123456 12" "deploy pr-12-0123456789ABCDEF0123456789ABCDEF01234567 12" "deploy pr--$SHA_A 12" "deploy pr-12-$SHA_A -1" \
  "reset now" "reset; id" "RESET" "bash" "cat /opt/tracker/.env" "deploy  pr-12-$SHA_A 12"; do
  begin "refused: '${REQUEST:0:40}'"
  staging "pr-9-$SHA_B"
  echo 9 > "$STAGING/current-pr"
  export STUB_HEALTHY_TAGS="pr-12-$SHA_A" SSH_ORIGINAL_COMMAND="$REQUEST"
  run "$APP/staging.sh"
  expect_status 2
  expect_output "Refused"
  if [ -s "$APP/stub.log" ]; then fail "something was run for a refused command"; fi
  expect_no_file "$STAGING/compose.yml"
  expect_no_file "$WORK/owned"
  expect_content "$STAGING/current-pr" "9"
  end
done

begin "reset empties staging and starts it again"
staging "pr-12-$SHA_A"
echo 12 > "$STAGING/current-pr"
export SSH_ORIGINAL_COMMAND="reset"
run "$APP/staging.sh"
expect_status 0
expect_output "result=reset"
expect_log "down-project tracker-staging files compose.yml:compose.staging.yml"
expect_log "plain-up-project tracker-staging"
expect_content "$STAGING/current-pr" "12"
end

begin "reset before any deploy only empties"
staging none
export SSH_ORIGINAL_COMMAND="reset"
run "$APP/staging.sh"
expect_status 0
expect_log "down-project tracker-staging"
expect_no_log "plain-up-project"
end

begin "a server without staging says so and runs nothing"
export TRACKER_DIR="$APP" STAGING_DIR="$APP/no-such-staging" SSH_ORIGINAL_COMMAND="deploy pr-12-$SHA_A 12"
run "$APP/staging.sh"
expect_status 1
expect_output "not set up"
if [ -s "$APP/stub.log" ]; then fail "something was run without a staging directory"; fi
end

echo "provision-staging.sh"

PUBLIC_KEY="ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIFakeFakeFakeFakeFakeFakeFakeFakeFakeFakeFake"

provisioning() {
  STAGING="$APP/staging"
  mkdir -p "$APP/ssh"
  printf 'ssh-ed25519 AAAAOwnerKey owner@laptop\nssh-ed25519 AAAADeployKey github-actions-deploy\n' > "$APP/ssh/authorized_keys"
  chmod 600 "$APP/ssh/authorized_keys"
  echo "$PUBLIC_KEY somebody@somewhere" > "$APP/staging-key.pub"
  export APP_USER APP_DIR="$APP" STAGING_DIR="$STAGING" AUTHORIZED_KEYS="$APP/ssh/authorized_keys"
  APP_USER=$(id -un)
}

begin "staging gets its own directory, secrets and restricted key"
provisioning
run "$INFRA/provision-staging.sh" staging.test me@example.test "$APP/staging-key.pub"
expect_status 0
expect_file "$STAGING/.env"
if [ -n "$(find "$STAGING/.env" -perm -004)" ] || [ -n "$(find "$STAGING/.env" -perm -040)" ]; then fail "staging .env is readable by others"; fi
for line in 'SITE_ADDRESS=:80' 'IMAGE_TAG=none' 'SIGNUP_ALLOWED_EMAILS=me@example.test' 'HEALTH_URL=https://staging.test/actuator/health' 'PREDEPLOY_DUMP=off'; do
  if ! grep -qxF "$line" "$STAGING/.env"; then fail "staging .env lacks: $line"; fi
done
STAGING_PG=$(grep -E '^POSTGRES_PASSWORD=' "$STAGING/.env" | cut -d= -f2-)
STAGING_JWT=$(grep -E '^JWT_SECRET=' "$STAGING/.env" | cut -d= -f2-)
if [ "${#STAGING_PG}" -lt 32 ] || [ "$STAGING_PG" = "pg" ]; then fail "staging database password is missing or production's"; fi
if [ "${#STAGING_JWT}" -lt 32 ] || [ "$STAGING_JWT" = "abc+/=" ]; then fail "staging JWT secret is missing or production's"; fi
if ! grep -qxF 'STAGING_ADDRESS=staging.test' "$APP/.env"; then fail "production .env did not get the staging address"; fi
if ! grep -qxF 'JWT_SECRET=abc+/=' "$APP/.env"; then fail "production .env was damaged"; fi
if [ -n "$(find "$APP/.env" -perm -004)" ] || [ -n "$(find "$APP/.env" -perm -040)" ]; then fail "production .env became readable by others"; fi
if ! grep -qxF "restrict,command=\"$APP/staging.sh\" $PUBLIC_KEY github-actions-staging" "$APP/ssh/authorized_keys"; then fail "the restricted key line is missing or wrong"; fi
if [ "$(grep -c '' "$APP/ssh/authorized_keys")" -ne 3 ]; then fail "authorized_keys should hold three lines"; fi
if [ -n "$(find "$APP/ssh/authorized_keys" -perm -004)" ] || [ -n "$(find "$APP/ssh/authorized_keys" -perm -040)" ]; then fail "authorized_keys became readable by others"; fi
expect_log "docker network inspect tracker-edge"
expect_no_output "$STAGING_PG"
expect_no_output "$STAGING_JWT"
end

begin "running it twice changes nothing"
provisioning
run "$INFRA/provision-staging.sh" staging.test me@example.test "$APP/staging-key.pub"
BEFORE=$(cat "$STAGING/.env")
run "$INFRA/provision-staging.sh" staging.test other@example.test "$APP/staging-key.pub"
expect_status 0
if [ "$(cat "$STAGING/.env")" != "$BEFORE" ]; then fail "staging .env was rewritten"; fi
if [ "$(grep -c '^STAGING_ADDRESS=' "$APP/.env")" -ne 1 ]; then fail "STAGING_ADDRESS is in production .env more than once"; fi
if [ "$(grep -c 'github-actions-staging$' "$APP/ssh/authorized_keys")" -ne 1 ]; then fail "the staging key is listed more than once"; fi
end

begin "a new staging key replaces the old one and leaves the other keys"
provisioning
run "$INFRA/provision-staging.sh" staging.test me@example.test "$APP/staging-key.pub"
echo "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAINewNewNewNewNewNewNewNewNewNewNewNew" > "$APP/staging-key.pub"
run "$INFRA/provision-staging.sh" staging.test me@example.test "$APP/staging-key.pub"
expect_status 0
if [ "$(grep -c 'github-actions-staging$' "$APP/ssh/authorized_keys")" -ne 1 ]; then fail "expected exactly one staging key"; fi
if ! grep -q 'INewNewNew.* github-actions-staging$' "$APP/ssh/authorized_keys"; then fail "the new key is not the one listed"; fi
if ! grep -qxF 'ssh-ed25519 AAAAOwnerKey owner@laptop' "$APP/ssh/authorized_keys"; then fail "the owner's key was lost"; fi
if ! grep -qxF 'ssh-ed25519 AAAADeployKey github-actions-deploy' "$APP/ssh/authorized_keys"; then fail "the production deploy key was lost"; fi
end

begin "the network is created when it is missing"
provisioning
export STUB_EDGE_MISSING=1
run "$INFRA/provision-staging.sh" staging.test me@example.test "$APP/staging-key.pub"
expect_status 0
expect_log "docker network create tracker-edge"
end

for BAD_KEY in "" "not a key" "ssh-rsa AAAAB3Nza comment" "-----BEGIN OPENSSH PRIVATE KEY-----" \
  "command=\"/bin/sh\" ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIFake" "ssh-ed25519 AAAA\"; id; \""; do
  begin "refused key file: '${BAD_KEY:0:30}'"
  provisioning
  printf '%s\n' "$BAD_KEY" > "$APP/staging-key.pub"
  run "$INFRA/provision-staging.sh" staging.test me@example.test "$APP/staging-key.pub"
  expect_failure
  expect_no_file "$STAGING/.env"
  if [ "$(grep -c '' "$APP/ssh/authorized_keys")" -ne 2 ]; then fail "authorized_keys was changed"; fi
  if grep -q '^STAGING_ADDRESS=' "$APP/.env"; then fail "production .env was changed"; fi
  end
done

begin "a key file with two keys is refused"
provisioning
printf '%s\n%s\n' "$PUBLIC_KEY" "$PUBLIC_KEY" > "$APP/staging-key.pub"
run "$INFRA/provision-staging.sh" staging.test me@example.test "$APP/staging-key.pub"
expect_failure
expect_no_file "$STAGING/.env"
end

begin "an address with shell characters is refused"
provisioning
run "$INFRA/provision-staging.sh" 'staging.test;id' me@example.test "$APP/staging-key.pub"
expect_failure
expect_no_file "$STAGING/.env"
end

begin "a server without production is refused"
provisioning
rm "$APP/.env"
run "$INFRA/provision-staging.sh" staging.test me@example.test "$APP/staging-key.pub"
expect_failure
expect_output "Production is not set up"
expect_no_file "$STAGING/.env"
end

echo "backup.sh"

begin "successful backup leaves the marker"
run "$APP/backup.sh"
expect_status 0
expect_log "restic backup --host tracker --tag nightly $APP/backup-staging/db.sql $APP/media"
expect_log "dump-present"
expect_log "restic forget --host tracker --tag nightly --keep-daily 7 --keep-weekly 4 --keep-monthly 6 --prune"
expect_no_log "restic check"
expect_file "$APP/last-backup-ok"
expect_no_file "$APP/backup-staging/db.sql"
end

begin "secrets are read literally from backup.env"
run "$APP/backup.sh"
expect_log "restic-password $TRICKY_PASSWORD"
end

begin "repository is checked on Sundays"
export STUB_WEEKDAY=7
run "$APP/backup.sh"
expect_status 0
expect_log "restic check"
expect_file "$APP/last-backup-ok"
end

begin "failed upload leaves no marker"
export STUB_RESTIC_FAILS=backup
run "$APP/backup.sh"
expect_failure
expect_no_file "$APP/last-backup-ok"
end

begin "failed clean-up of old snapshots leaves no marker"
export STUB_RESTIC_FAILS=forget
run "$APP/backup.sh"
expect_failure
expect_no_file "$APP/last-backup-ok"
end

begin "failed Sunday check leaves no marker"
export STUB_WEEKDAY=7 STUB_RESTIC_FAILS=check
run "$APP/backup.sh"
expect_failure
expect_no_file "$APP/last-backup-ok"
end

begin "failed dump uploads nothing"
export STUB_DUMP_FAILS=1
run "$APP/backup.sh"
expect_failure
expect_no_log "restic backup"
expect_no_file "$APP/last-backup-ok"
end

begin "missing backup.env is an error"
rm "$APP/backup.env"
run "$APP/backup.sh"
expect_status 1
expect_no_log "restic"
expect_no_file "$APP/last-backup-ok"
end

begin "backup.env without a password is an error"
printf 'RESTIC_REPOSITORY=/tmp/repo\n' > "$APP/backup.env"
run "$APP/backup.sh"
expect_status 1
expect_no_log "restic"
end

echo "restore.sh"

begin "snapshot is loaded into a scratch database only"
run "$APP/restore.sh" latest "$APP/out"
expect_status 0
expect_log "restic restore latest --host tracker --target $APP/out"
expect_log "docker network create --internal tracker-restore-check"
expect_log "docker run -d --name tracker-restore-check --network tracker-restore-check"
expect_no_log "docker compose"
expect_no_log " -p "
expect_no_log "--publish"
if [ "$(cat "$APP/loaded.sql" 2>/dev/null)" != "-- restored dump" ]; then fail "the dump was not loaded"; fi
expect_output "activities 3"
expect_output "Restored media files: 2"
expect_log "docker rm -fv tracker-restore-check"
expect_output "docker rm -fv tracker-restore-check"
end

begin "non-empty target directory is refused"
mkdir -p "$APP/out" && : > "$APP/out/keep"
run "$APP/restore.sh" latest "$APP/out"
expect_status 1
expect_no_log "restic"
expect_no_log "docker"
expect_file "$APP/out/keep"
end

begin "relative target directory is refused"
run "$APP/restore.sh" latest out
expect_status 1
expect_no_log "restic"
end

begin "snapshot without a dump is an error"
export STUB_SNAPSHOT_HAS_DUMP=0
run "$APP/restore.sh" latest "$APP/out"
expect_status 1
expect_no_log "docker run"
end

begin "missing backup.env is an error"
rm "$APP/backup.env"
run "$APP/restore.sh" latest "$APP/out"
expect_status 1
expect_no_log "restic"
end

echo
echo "$PASSED passed, $FAILED failed"
[ "$FAILED" -eq 0 ]
