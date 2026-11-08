#!/usr/bin/env bash
# 내 PC 에서 빌드해 운영 서버에 올립니다. 코드를 바꾼 뒤 다시 배포할 때도 이 스크립트를 씁니다.
# 사용법 (Git Bash, IM-BANK 디렉터리에서): bash deploy/deploy.sh <공인 IP>
set -euo pipefail

IP="${1:?공인 IP 를 넘겨 주세요}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Windows 에서는 SSH 키가 Windows ssh-agent 에 있으므로 Git Bash 의 ssh 대신 Windows 의 ssh 를 씁니다.
SSH=ssh
SCP=scp
if [ -x /c/Windows/System32/OpenSSH/ssh.exe ]; then
  SSH=/c/Windows/System32/OpenSSH/ssh.exe
  SCP=/c/Windows/System32/OpenSSH/scp.exe
fi

echo "[1/3] build"
# node_modules 가 있으면 그대로 씁니다. npm ci 는 폴더를 지우므로 켜 둔 개발 서버가 멈춥니다.
(cd "$ROOT/web" && { [ -d node_modules ] || npm ci --no-audit --no-fund; } && VITE_SIGNUP_ENABLED=false npm run build)
(cd "$ROOT/server" && ./gradlew bootJar -PwebDist=../web/dist --console=plain)
JAR="$(ls "$ROOT"/server/build/libs/*.jar | grep -v -- '-plain' | head -1)"
if command -v cygpath >/dev/null 2>&1; then
  JAR="$(cygpath -m "$JAR")"
fi

echo "[2/3] upload"
MSYS_NO_PATHCONV=1 "$SCP" "$JAR" "root@$IP:/opt/docguard/app.jar.new"

echo "[3/3] restart"
"$SSH" "root@$IP" bash -s <<'EOF'
set -e
chmod 644 /opt/docguard/app.jar.new
mv /opt/docguard/app.jar.new /opt/docguard/app.jar
systemctl restart docguard
for i in $(seq 1 40); do
  if curl -fsS http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q UP; then
    echo "server is up"
    exit 0
  fi
  sleep 2
done
echo "server did not start in 80s: journalctl -u docguard -n 50" >&2
exit 1
EOF

echo "done: https://${IP//./-}.sslip.io"
