#!/usr/bin/env bash
set -euo pipefail

APP_USER=${APP_USER:-deploy}
APP_DIR=${APP_DIR:-/opt/tracker}
STAGING_DIR=${STAGING_DIR:-/opt/tracker-staging}
AUTHORIZED_KEYS=${AUTHORIZED_KEYS:-/home/$APP_USER/.ssh/authorized_keys}
KEY_NAME=github-actions-staging

if [ $# -ne 3 ] || [ -z "$1" ] || [ -z "$2" ] || [ ! -f "$3" ]; then
  echo "Usage: provision-staging.sh <staging-address> <allowed-emails> <public-key-file>" >&2
  exit 1
fi

STAGING_ADDRESS=$1
SIGNUP_ALLOWED_EMAILS=$2
KEY_FILE=$3

if [[ ! $STAGING_ADDRESS =~ ^[A-Za-z0-9.-]+$ ]] || [[ ! $SIGNUP_ALLOWED_EMAILS =~ ^[A-Za-z0-9@._+,-]+$ ]]; then
  echo "The staging address may hold letters, digits, dots and hyphens; the emails also @ _ + and commas." >&2
  exit 1
fi

if [ "$(grep -c '' "$KEY_FILE")" -ne 1 ] || ! grep -qE '^ssh-ed25519 [A-Za-z0-9+/=]+( [A-Za-z0-9@._-]+)?$' "$KEY_FILE"; then
  echo "The key file must hold exactly one ssh-ed25519 public key." >&2
  exit 1
fi
PUBLIC_KEY=$(cut -d' ' -f1,2 "$KEY_FILE")

if [ ! -f "$APP_DIR/.env" ] || [ ! -f "$AUTHORIZED_KEYS" ]; then
  echo "Production is not set up on this server (no $APP_DIR/.env or no authorized_keys)." >&2
  exit 1
fi

APP_GROUP=$(id -gn "$APP_USER")

install -d -m 750 -o "$APP_USER" -g "$APP_GROUP" "$STAGING_DIR"
if [ -f "$STAGING_DIR/.env" ]; then
  echo "$STAGING_DIR/.env already exists, left untouched."
else
  (
    umask 077
    {
      printf 'SITE_ADDRESS=:80\n'
      printf 'IMAGE_TAG=none\n'
      printf 'POSTGRES_PASSWORD=%s\n' "$(openssl rand -hex 24)"
      printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 32)"
      printf 'SIGNUP_ALLOWED_EMAILS=%s\n' "$SIGNUP_ALLOWED_EMAILS"
      printf 'OPENROUTER_API_KEY=placeholder-until-set-for-staging\n'
      printf 'OPENROUTER_BASE_URL=https://openrouter.ai/api/v1\n'
      printf 'OPENROUTER_MODEL=placeholder-until-set-for-staging\n'
      printf 'HEALTH_URL=https://%s/actuator/health\n' "$STAGING_ADDRESS"
      printf 'PREDEPLOY_DUMP=off\n'
    } > "$STAGING_DIR/.env"
  )
  chown "$APP_USER:$APP_GROUP" "$STAGING_DIR/.env"
  chmod 600 "$STAGING_DIR/.env"
  echo "$STAGING_DIR/.env written with its own database password and JWT secret (not shown)."
fi

if grep -q '^STAGING_ADDRESS=' "$APP_DIR/.env"; then
  echo "STAGING_ADDRESS is already in $APP_DIR/.env, left untouched."
else
  printf 'STAGING_ADDRESS=%s\n' "$STAGING_ADDRESS" >> "$APP_DIR/.env"
  echo "STAGING_ADDRESS added to $APP_DIR/.env."
fi

if ! docker network inspect tracker-edge >/dev/null 2>&1; then
  docker network create tracker-edge >/dev/null
  echo "Shared network tracker-edge created."
fi

(
  umask 077
  { grep -v " $KEY_NAME\$" "$AUTHORIZED_KEYS" || true; } > "$AUTHORIZED_KEYS.new"
  printf 'restrict,command="%s/staging.sh" %s %s\n' "$APP_DIR" "$PUBLIC_KEY" "$KEY_NAME" >> "$AUTHORIZED_KEYS.new"
)
chown "$APP_USER:$APP_GROUP" "$AUTHORIZED_KEYS.new"
chmod 600 "$AUTHORIZED_KEYS.new"
mv "$AUTHORIZED_KEYS.new" "$AUTHORIZED_KEYS"
echo "The staging key can now run $APP_DIR/staging.sh and nothing else."

echo "Staging is prepared. The proxy starts answering for the staging address after the web container is restarted."
