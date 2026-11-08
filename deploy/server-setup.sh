#!/usr/bin/env bash
# DocGuard 운영 서버 초기 설정 (Ubuntu 24.04, root 로 한 번 실행). 다시 실행해도 안전합니다.
# 사용법: bash server-setup.sh <공인 IP>
#
# 설치: Java 21, PostgreSQL 16, Caddy(HTTPS 자동 발급), systemd 서비스, 방화벽
# 비밀 값(DB 비밀번호, 암호화 키)은 이 서버에서 무작위로 만들고 화면에 출력하지 않습니다.
# Upstage 값 3개는 비어 있으며 deploy/push-upstage-keys.sh 로 넣습니다.
set -euo pipefail

IP="${1:?공인 IP 를 넘겨 주세요. 예: bash server-setup.sh 203.0.113.10}"
if ! [[ "$IP" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]]; then
  echo "공인 IP 형식이 아닙니다: $IP" >&2
  exit 1
fi
DOMAIN="${IP//./-}.sslip.io"
ENV_FILE=/etc/docguard/docguard.env

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y openjdk-21-jre-headless postgresql caddy ufw openssl unattended-upgrades
apt-get upgrade -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold

# 네이버클라우드 이미지는 자동 보안 업데이트가 꺼져 있으므로 켭니다.
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF

# 서비스 계정과 폴더. 문서는 /var/lib/docguard/storage 에 암호화되어 저장됩니다.
id docguard >/dev/null 2>&1 || useradd --system --home-dir /var/lib/docguard --shell /usr/sbin/nologin docguard
install -d -o docguard -g docguard -m 700 /var/lib/docguard /var/lib/docguard/storage
install -d -o root -g root -m 755 /opt/docguard
install -d -o root -g docguard -m 750 /etc/docguard

# 환경 변수 파일. 이미 있으면 덮어쓰지 않습니다(암호화 키를 바꾸면 저장된 문서를 풀 수 없습니다).
if [ ! -f "$ENV_FILE" ]; then
  umask 077
  cat > "$ENV_FILE" <<EOF
UPSTAGE_MODE=live
UPSTAGE_API_KEY=
UPSTAGE_PROJECT_ID=
UPSTAGE_AGENT_ID=
UPSTAGE_CONFIG_ID=2
UPSTAGE_BASE_URL=https://api.upstage.ai/v2
UPSTAGE_POLL_INTERVAL_MS=3000
UPSTAGE_JOB_TIMEOUT_SEC=600
DB_URL=jdbc:postgresql://localhost:5432/docguard
DB_USERNAME=docguard
DB_PASSWORD=$(openssl rand -hex 24)
APP_ENCRYPTION_KEY=$(openssl rand -base64 32)
APP_STORAGE_DIR=/var/lib/docguard/storage
APP_RETENTION_HOURS=168
APP_COOKIE_SECURE=true
APP_SIGNUP_ENABLED=false
EOF
  umask 022
fi
chown root:docguard "$ENV_FILE"
chmod 640 "$ENV_FILE"

# 데이터베이스. 비밀번호가 명령줄에 나오지 않도록 표준 입력으로 넘깁니다.
DB_PASSWORD="$(grep '^DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
if ! sudo -u postgres psql -tAc "select 1 from pg_roles where rolname='docguard'" | grep -q 1; then
  sudo -u postgres psql -q <<EOF
create role docguard login password '$DB_PASSWORD';
EOF
fi
if ! sudo -u postgres psql -tAc "select 1 from pg_database where datname='docguard'" | grep -q 1; then
  sudo -u postgres createdb -O docguard docguard
fi

# 큰 파일을 메모리에 올려 처리하므로, 순간적으로 메모리가 모자랄 때를 대비해 스왑을 둡니다.
if ! swapon --show | grep -q .; then
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

cat > /etc/systemd/system/docguard.service <<'EOF'
[Unit]
Description=DocGuard
After=network-online.target postgresql.service
Wants=network-online.target
Requires=postgresql.service

[Service]
User=docguard
Group=docguard
WorkingDirectory=/var/lib/docguard
EnvironmentFile=/etc/docguard/docguard.env
Environment=SPRING_PROFILES_ACTIVE=prod
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=60 -Dfile.encoding=UTF-8 -jar /opt/docguard/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/docguard

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable docguard >/dev/null

# HTTPS. Caddy 가 Let's Encrypt 인증서를 받고 자동으로 갱신합니다.
cat > /etc/caddy/Caddyfile <<EOF
$DOMAIN {
	encode gzip
	# 파일 업로드 외의 요청은 본문이 클 이유가 없습니다. 로그인 없이 큰 본문을 보내는 공격을 앞단에서 막습니다.
	@notUpload not path /api/analyses
	request_body @notUpload {
		max_size 1MB
	}
	reverse_proxy 127.0.0.1:8080
}
EOF
systemctl enable caddy >/dev/null
systemctl restart caddy

ufw allow 22/tcp >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw --force enable >/dev/null

# 키로 접속되는 것이 확인된 뒤에만 비밀번호 접속을 끕니다.
if [ -s /root/.ssh/authorized_keys ]; then
  cat > /etc/ssh/sshd_config.d/10-docguard.conf <<'EOF'
PasswordAuthentication no
PermitRootLogin prohibit-password
EOF
  systemctl reload ssh
fi

echo "서버 설정 완료: https://$DOMAIN"
