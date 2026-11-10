#!/usr/bin/env sh
# IM-BANK 역할별 계정 생성 스크립트 (macOS, Linux, Git Bash). seed-accounts.ps1 과 같은 일을 합니다.
# 실행 (IM-BANK 디렉터리에서, DB 가 떠 있어야 함): sh scripts/seed-accounts.sh
# 비밀번호는 .local/accounts.txt 에만 적고 화면에 출력하지 않습니다.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root/server"
./gradlew bootRun --quiet --args="--app.seed-accounts=true --app.scheduling.enabled=false --server.port=0"
echo "완료: $root/.local/accounts.txt 를 확인하세요."
