FROM caddy:2-alpine

LABEL org.opencontainers.image.source=https://github.com/eugenlinde/activity-tracker

COPY infra/Caddyfile /etc/caddy/Caddyfile
COPY frontend/dist /srv
