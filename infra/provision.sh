#!/usr/bin/env bash
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root." >&2
  exit 1
fi

if [ $# -ne 2 ] || [ -z "$1" ] || [ -z "$2" ]; then
  echo "Usage: provision.sh <site-address> <allowed-emails>" >&2
  exit 1
fi

SITE_ADDRESS=$1
SIGNUP_ALLOWED_EMAILS=$2
APP_DIR=/opt/tracker
APP_USER=deploy

case "$SITE_ADDRESS$SIGNUP_ALLOWED_EMAILS" in
  *[[:space:]\'\"\$\#\\]*)
    echo "Site address and emails must not contain spaces, quotes, \$, # or backslashes." >&2
    exit 1
    ;;
esac

export DEBIAN_FRONTEND=noninteractive

apt-get update -q
apt-get install -y -q ca-certificates curl openssl ufw unattended-upgrades restic

install -m 0755 -d /etc/apt/keyrings
if [ ! -f /etc/apt/keyrings/docker.asc ]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
fi
CODENAME=$(grep -E '^VERSION_CODENAME=' /etc/os-release | cut -d= -f2)
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $CODENAME stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -q
apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl enable --now docker
echo "Docker installed and enabled."

cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
echo "Automatic security updates on."

if ! id "$APP_USER" >/dev/null 2>&1; then
  adduser --disabled-password --gecos '' "$APP_USER"
fi
usermod -aG docker "$APP_USER"
install -d -m 700 -o "$APP_USER" -g "$APP_USER" "/home/$APP_USER/.ssh"
if [ ! -f "/home/$APP_USER/.ssh/authorized_keys" ]; then
  install -m 600 -o "$APP_USER" -g "$APP_USER" /root/.ssh/authorized_keys "/home/$APP_USER/.ssh/authorized_keys"
fi
echo "User $APP_USER ready (docker group, SSH keys copied from root)."

cat > /etc/ssh/sshd_config.d/00-tracker.conf <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
EOF
sshd -t
systemctl reload ssh || systemctl restart ssh
echo "SSH: keys only, no passwords."

ufw allow 22/tcp >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw allow 443/udp >/dev/null
ufw --force enable >/dev/null
echo "Firewall on: 22, 80, 443."

install -d -m 750 -o "$APP_USER" -g "$APP_USER" "$APP_DIR"
if [ -f "$APP_DIR/.env" ]; then
  echo "$APP_DIR/.env already exists, left untouched."
else
  (
    umask 077
    {
      printf 'SITE_ADDRESS=%s\n' "$SITE_ADDRESS"
      printf 'IMAGE_TAG=none\n'
      printf 'POSTGRES_PASSWORD=%s\n' "$(openssl rand -hex 24)"
      printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 32)"
      printf 'SIGNUP_ALLOWED_EMAILS=%s\n' "$SIGNUP_ALLOWED_EMAILS"
      printf 'OPENROUTER_API_KEY=placeholder-until-import-is-built\n'
      printf 'OPENROUTER_BASE_URL=https://openrouter.ai/api/v1\n'
      printf 'OPENROUTER_MODEL=placeholder-until-import-is-built\n'
    } > "$APP_DIR/.env"
  )
  chown "$APP_USER:$APP_USER" "$APP_DIR/.env"
  chmod 600 "$APP_DIR/.env"
  echo "$APP_DIR/.env written with fresh database password and JWT secret (not shown)."
fi

cat > /etc/systemd/system/tracker-backup.service <<EOF
[Unit]
Description=Tracker nightly backup
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
ExecStart=$APP_DIR/backup.sh
EOF

cat > /etc/systemd/system/tracker-backup.timer <<'EOF'
[Unit]
Description=Tracker nightly backup

[Timer]
OnCalendar=*-*-* 03:30:00
Persistent=true

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now tracker-backup.timer >/dev/null 2>&1
echo "Backup timer enabled (daily 03:30). It fails until backup.sh and backup.env are in $APP_DIR."

echo "Done."
