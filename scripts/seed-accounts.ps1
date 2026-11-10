# IM-BANK 역할별 계정 생성 스크립트 (Windows PowerShell 5.1 이상)
#
# 실행 (IM-BANK 디렉터리에서, DB 가 떠 있어야 함):
#   powershell -ExecutionPolicy Bypass -File scripts\seed-accounts.ps1
#
# - 점검 담당자 2개, 검토자 1개, 관리자 1개 계정을 만듭니다(이메일 *@docguard.local).
# - 비밀번호는 서버가 무작위로 만들어 .local\accounts.txt 에만 적습니다. 화면과 로그에는 출력하지 않습니다.
# - 이미 있는 계정은 건드리지 않습니다. 비밀번호를 잊었으면 DB 에서 그 계정을 지운 뒤 다시 실행합니다.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $root 'server')
try {
    & .\gradlew.bat bootRun --quiet "--args=--app.seed-accounts=true --app.scheduling.enabled=false --server.port=0"
    if ($LASTEXITCODE -ne 0) { throw "계정 생성에 실패했습니다 (exit $LASTEXITCODE)." }
    Write-Host "완료: $(Join-Path $root '.local\accounts.txt') 를 확인하세요."
} finally {
    Pop-Location
}
