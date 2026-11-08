#!/usr/bin/env bash
# 운영 서버에 역할별 계정을 만듭니다(root 로 서버에서 실행). 비밀번호는 무작위로 만들어
# /var/lib/docguard/accounts.txt 에만 적고 화면에 출력하지 않습니다. 이미 있는 계정은 건드리지 않습니다.
set -euo pipefail

set -a
# shellcheck disable=SC1091
. /etc/docguard/docguard.env
set +a

cd /var/lib/docguard
runuser -u docguard -- env SPRING_PROFILES_ACTIVE=prod java -jar /opt/docguard/app.jar \
  --app.seed-accounts=true --app.scheduling.enabled=false --server.port=0 >/dev/null
chmod 600 /var/lib/docguard/accounts.txt
echo "완료: /var/lib/docguard/accounts.txt"
