#!/usr/bin/env bash
set -euo pipefail

APP_USER=${APP_USER:-deploy}
APP_DIR=${APP_DIR:-/opt/tracker}
STAGING_DIR=${STAGING_DIR:-/opt/tracker-staging}
AUTHORIZED_KEYS=${AUTHORIZED_KEYS:-/home/$APP_USER/.ssh/authorized_keys}
KEY_NAME=github-actions-staging

if [ "$(id -u)" -ne 0 ] && [ "$(id -un)" != "$APP_USER" ]; then
  echo "Run as root." >&2
  exit 1
fi

if [ $# -ne 3 ] || [ -z "$1" ] || [ -z "$2" ] || [ ! -f "$3" ]; then
  echo "Usage: provision-staging.sh <staging-address> <allowed-emails> <public-key-file>" >&2
  exit 1
fi

STAGING_ADDRESS=$1
SIGNUP_ALLOWED_EMAILS=$2
KEY_FILE=$3

HOST_LABEL='[a-z0-9]([a-z0-9-]*[a-z0-9])?'
if [[ ! $STAGING_ADDRESS =~ ^$HOST_LABEL(\.$HOST_LABEL)+$ ]] || [[ ! $SIGNUP_ALLOWED_EMAILS =~ ^[A-Za-z0-9@._+,-]+$ ]]; then
  echo "The staging address must be a plain host name in lower case (like staging.example.com); the emails may hold letters, digits and @ . _ + , -" >&2
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

if [ "$({ grep -E '^SITE_ADDRESS=' "$APP_DIR/.env" || true; } | head -n 1 | cut -d= -f2-)" = "$STAGING_ADDRESS" ]; then
  echo "The staging address must differ from production's address." >&2
  exit 1
fi

KEY_MATERIAL=$(cut -d' ' -f2 "$KEY_FILE")
if grep -a -v " $KEY_NAME\$" "$AUTHORIZED_KEYS" | grep -a -qF -- "$KEY_MATERIAL"; then
  echo "This key is already listed with other rights. Make a new key for staging." >&2
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
      printf 'COMPOSE_PROJECT_NAME=tracker-staging\n'
      printf 'COMPOSE_FILE=base.compose.yml:staging.compose.yml\n'
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
  if [ -n "$(tail -c 1 "$APP_DIR/.env")" ]; then
    printf '\n' >> "$APP_DIR/.env"
  fi
  printf 'STAGING_ADDRESS=%s\n' "$STAGING_ADDRESS" >> "$APP_DIR/.env"
  echo "STAGING_ADDRESS added to $APP_DIR/.env."
fi

if ! docker network inspect tracker-edge >/dev/null 2>&1; then
  docker network create tracker-edge >/dev/null
  echo "Shared network tracker-edge created."
fi

(
  umask 077
  { grep -a -v " $KEY_NAME\$" "$AUTHORIZED_KEYS" || true; } > "$AUTHORIZED_KEYS.new"
  printf 'restrict,command="%s/staging.sh" %s %s\n' "$APP_DIR" "$PUBLIC_KEY" "$KEY_NAME" >> "$AUTHORIZED_KEYS.new"
)
KEPT=$(grep -a -c -v " $KEY_NAME\$" "$AUTHORIZED_KEYS" || true)
if [ "$(grep -a -c '' "$AUTHORIZED_KEYS.new")" -ne "$((KEPT + 1))" ]; then
  rm -f "$AUTHORIZED_KEYS.new"
  echo "Could not rewrite $AUTHORIZED_KEYS safely; it was left as it was." >&2
  exit 1
fi
chown "$APP_USER:$APP_GROUP" "$AUTHORIZED_KEYS.new"
chmod 600 "$AUTHORIZED_KEYS.new"
mv "$AUTHORIZED_KEYS.new" "$AUTHORIZED_KEYS"
echo "The staging key can now run $APP_DIR/staging.sh and nothing else."

echo "Staging is prepared. Once production runs a version that knows about staging, run 'docker compose up -d web' in $APP_DIR: the proxy then starts answering for the staging address."
