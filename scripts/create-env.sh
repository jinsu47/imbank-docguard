#!/usr/bin/env sh
# IM-BANK 로컬 .env 생성 스크립트 (macOS, Linux, Git Bash)
#
# 실행 (IM-BANK 디렉터리에서): sh scripts/create-env.sh
#
# create-env.ps1 과 같은 일을 합니다.
# - 이미 .env 가 있으면 덮어쓰지 않고 멈춥니다.
# - DB_PASSWORD 와 APP_ENCRYPTION_KEY 는 무작위로 생성하고 화면에 출력하지 않습니다.
# - UPSTAGE_API_KEY, UPSTAGE_PROJECT_ID, UPSTAGE_AGENT_ID 는 비워 둡니다. 사용자가 직접 넣습니다.
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
example="$root/.env.example"
target="$root/.env"

if [ -e "$target" ]; then
  echo ".env 가 이미 있어 덮어쓰지 않습니다: $target"
  echo "(주의: APP_ENCRYPTION_KEY 를 바꾸면 이미 저장한 원본 파일과 결과를 복호화할 수 없습니다.)"
  exit 1
fi
if [ ! -f "$example" ]; then
  echo ".env.example 을 찾을 수 없습니다: $example"
  exit 1
fi

random_bytes() {
  head -c "$1" /dev/urandom
}

db_password=$(random_bytes 24 | od -An -tx1 | tr -d ' \n')
encryption_key=$(random_bytes 32 | base64 | tr -d '\n')

umask 077
{
  echo "# scripts/create-env.sh 가 생성한 로컬 전용 파일입니다. 저장소에 올리지 않습니다(.gitignore)."
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      UPSTAGE_MODE=) echo "UPSTAGE_MODE=replay" ;;
      UPSTAGE_CONFIG_ID=) echo "UPSTAGE_CONFIG_ID=2" ;;
      UPSTAGE_BASE_URL=) echo "UPSTAGE_BASE_URL=https://api.upstage.ai/v2" ;;
      UPSTAGE_POLL_INTERVAL_MS=) echo "UPSTAGE_POLL_INTERVAL_MS=3000" ;;
      UPSTAGE_JOB_TIMEOUT_SEC=) echo "UPSTAGE_JOB_TIMEOUT_SEC=600" ;;
      DB_URL=) echo "DB_URL=jdbc:postgresql://localhost:5434/docguard" ;;
      DB_USERNAME=) echo "DB_USERNAME=docguard" ;;
      DB_PASSWORD=) echo "DB_PASSWORD=$db_password" ;;
      APP_ENCRYPTION_KEY=) echo "APP_ENCRYPTION_KEY=$encryption_key" ;;
      APP_STORAGE_DIR=) echo "APP_STORAGE_DIR=../storage" ;;
      APP_RETENTION_HOURS=) echo "APP_RETENTION_HOURS=168" ;;
      APP_UPLOAD_MAX_MB=) echo "APP_UPLOAD_MAX_MB=20" ;;
      APP_COOKIE_SECURE=) echo "APP_COOKIE_SECURE=false" ;;
      *) printf '%s\n' "$line" ;;
    esac
  done < "$example"
} > "$target"

echo ".env 를 만들었습니다: $target"
echo "- DB_PASSWORD, APP_ENCRYPTION_KEY: 무작위로 생성했습니다(화면에 출력하지 않음)."
echo "- 다음 값은 비어 있습니다. .env 를 편집기로 열어 직접 넣어 주세요."
echo "    UPSTAGE_API_KEY, UPSTAGE_PROJECT_ID, UPSTAGE_AGENT_ID"
echo "  지금은 UPSTAGE_MODE=replay(저장 응답 재생)입니다. 키를 넣은 뒤 UPSTAGE_MODE=live 로 바꾸세요."
echo "  키는 채팅이나 다른 파일에 붙여 넣지 마세요."
