#!/usr/bin/env bash
# 로컬 .env 의 Upstage 값 3개를 운영 서버의 환경 변수 파일로 옮기고 서비스를 다시 시작합니다.
# 값은 화면과 명령줄에 나오지 않고 SSH 표준 입력으로만 전달됩니다.
# 사용법 (Git Bash, IM-BANK 디렉터리에서): bash deploy/push-upstage-keys.sh <공인 IP>
set -euo pipefail

IP="${1:?공인 IP 를 넘겨 주세요}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PATTERN='^(UPSTAGE_API_KEY|UPSTAGE_PROJECT_ID|UPSTAGE_AGENT_ID)='

# Windows 에서는 SSH 키가 Windows ssh-agent 에 있으므로 Git Bash 의 ssh 대신 Windows 의 ssh 를 씁니다.
SSH=ssh
if [ -x /c/Windows/System32/OpenSSH/ssh.exe ]; then
  SSH=/c/Windows/System32/OpenSSH/ssh.exe
fi

if [ "$(grep -cE "$PATTERN.+" "$ROOT/.env")" -ne 3 ]; then
  echo ".env 에 UPSTAGE_API_KEY, UPSTAGE_PROJECT_ID, UPSTAGE_AGENT_ID 값이 모두 있어야 합니다." >&2
  exit 1
fi

grep -E "$PATTERN" "$ROOT/.env" | tr -d '\r' | MSYS_NO_PATHCONV=1 "$SSH" "root@$IP" 'set -e; f=/etc/docguard/docguard.env; t=$(mktemp); grep -v -e ^UPSTAGE_API_KEY= -e ^UPSTAGE_PROJECT_ID= -e ^UPSTAGE_AGENT_ID= $f > $t; cat >> $t; cat $t > $f; rm -f $t; systemctl restart docguard'

echo "Upstage 값을 서버에 넣고 서비스를 다시 시작했습니다."
