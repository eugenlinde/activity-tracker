#!/usr/bin/env bash
set -euo pipefail
umask 077

cd "$(dirname "$0")"

if [ ! -f backup.env ]; then
  echo "backup.env is missing, nothing backed up" >&2
  exit 1
fi

while IFS= read -r line; do
  export "${line%%=*}=${line#*=}"
done < <(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' backup.env)

if [ -z "${RESTIC_REPOSITORY:-}" ] || [ -z "${RESTIC_PASSWORD:-}" ]; then
  echo "RESTIC_REPOSITORY or RESTIC_PASSWORD is missing from backup.env" >&2
  exit 1
fi

MEDIA_DIR=$(docker volume inspect tracker_media --format '{{ .Mountpoint }}')
STAGING="$PWD/backup-staging"

mkdir -p "$STAGING"
docker compose exec -T postgres pg_dump -U tracker tracker > "$STAGING/db.sql.tmp"
mv "$STAGING/db.sql.tmp" "$STAGING/db.sql"

restic backup --host tracker --tag nightly "$STAGING/db.sql" "$MEDIA_DIR"
restic forget --host tracker --tag nightly --keep-daily 7 --keep-weekly 4 --keep-monthly 6 --prune

if [ "$(date -u +%u)" = "7" ]; then
  restic check
fi

rm -f "$STAGING/db.sql"
touch last-backup-ok
echo "Backup finished"
