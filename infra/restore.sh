#!/usr/bin/env bash
set -euo pipefail
umask 077

if [ $# -ne 2 ]; then
  echo "Usage: restore.sh <snapshot|latest> <target-dir>" >&2
  exit 1
fi

SNAPSHOT=$1
TARGET=$2
NAME=tracker-restore-check

case "$TARGET" in
  /*) ;;
  *)
    echo "The target directory must be an absolute path" >&2
    exit 1
    ;;
esac

cd "$(dirname "$0")"

if [ ! -f backup.env ]; then
  echo "backup.env is missing" >&2
  exit 1
fi

if [ -e "$TARGET" ] && [ -n "$(ls -A "$TARGET" 2>/dev/null)" ]; then
  echo "$TARGET exists and is not empty, choose another directory" >&2
  exit 1
fi

while IFS= read -r line; do
  export "${line%%=*}=${line#*=}"
done < <(grep -E '^[A-Za-z_][A-Za-z0-9_]*=' backup.env)

if [ -z "${RESTIC_REPOSITORY:-}" ] || [ -z "${RESTIC_PASSWORD:-}" ]; then
  echo "RESTIC_REPOSITORY or RESTIC_PASSWORD is missing from backup.env" >&2
  exit 1
fi

mkdir -p "$TARGET"
restic restore "$SNAPSHOT" --host tracker --target "$TARGET"

DUMP=$(find "$TARGET" -type f -name db.sql -print -quit)
MEDIA=$(find "$TARGET" -type d -path '*/tracker_media/_data' -print -quit)

if [ -z "$DUMP" ]; then
  echo "The snapshot has no db.sql" >&2
  exit 1
fi

docker rm -fv "$NAME" >/dev/null 2>&1 || true
docker network inspect "$NAME" >/dev/null 2>&1 || docker network create --internal "$NAME" >/dev/null
docker run -d --name "$NAME" --network "$NAME" \
  -e POSTGRES_DB=tracker -e POSTGRES_USER=tracker -e POSTGRES_PASSWORD="$(openssl rand -hex 16)" \
  postgres:17-alpine >/dev/null

READY=0
for _ in $(seq 1 30); do
  if docker exec "$NAME" pg_isready -q -h 127.0.0.1 -U tracker -d tracker; then
    READY=1
    break
  fi
  sleep 2
done

if [ "$READY" -ne 1 ]; then
  echo "The scratch database did not start" >&2
  exit 1
fi

docker exec -i "$NAME" psql -q -v ON_ERROR_STOP=1 -U tracker -d tracker < "$DUMP" >/dev/null

echo "Rows per table in the restored database:"
docker exec -i "$NAME" psql -At -F ' ' -U tracker -d tracker <<'SQL'
select format('select %L, count(*) from %I', table_name, table_name)
from information_schema.tables
where table_schema = 'public' and table_type = 'BASE TABLE'
order by table_name \gexec
SQL

if [ -n "$MEDIA" ]; then
  echo "Restored media files: $(find "$MEDIA" -type f | wc -l | tr -d ' ')"
else
  echo "Restored media files: 0"
fi

echo "Scratch database left running as container $NAME."
echo "Clean up: docker rm -fv $NAME && docker network rm $NAME && rm -rf $TARGET"
