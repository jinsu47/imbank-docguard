# IM-BANK 로컬 .env 생성 스크립트 (Windows PowerShell 5.1 이상)
#
# 실행 (IM-BANK 디렉터리에서):
#   powershell -ExecutionPolicy Bypass -File scripts\create-env.ps1
#
# - .env.example 의 변수 목록으로 .env 를 만듭니다. 이미 .env 가 있으면 덮어쓰지 않고 멈춥니다.
# - DB_PASSWORD 와 APP_ENCRYPTION_KEY 는 무작위로 생성합니다. 생성한 값은 화면에 출력하지 않습니다.
# - UPSTAGE_API_KEY, UPSTAGE_PROJECT_ID, UPSTAGE_AGENT_ID 는 비워 두므로, 사용자가 .env 를 열어 직접 넣습니다.
# - 그 밖의 변수는 ARCHITECTURE.md 7장의 기본값으로 채웁니다.

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$examplePath = Join-Path $root '.env.example'
$envPath = Join-Path $root '.env'

if (Test-Path $envPath) {
    Write-Host ".env 가 이미 있어 덮어쓰지 않습니다: $envPath"
    Write-Host '새로 만들려면 기존 .env 를 직접 지우거나 다른 이름으로 옮긴 뒤 다시 실행하세요.'
    Write-Host '(주의: APP_ENCRYPTION_KEY 를 바꾸면 이미 저장한 원본 파일과 결과를 복호화할 수 없습니다.)'
    exit 1
}
if (-not (Test-Path $examplePath)) {
    Write-Host ".env.example 을 찾을 수 없습니다: $examplePath"
    exit 1
}

function New-RandomBytes([int] $size) {
    $bytes = New-Object byte[] $size
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ,$bytes
}

# DB 비밀번호는 접속 URL·셸에서 문제가 없도록 16진수 48자로 만듭니다.
$dbPassword = -join ((New-RandomBytes 24) | ForEach-Object { $_.ToString('x2') })
# 암호화 키는 AES-256 용 32바이트를 Base64 로 만듭니다.
$encryptionKey = [Convert]::ToBase64String((New-RandomBytes 32))

$values = @{
    # 키를 넣기 전에는 재생 모드로 시작합니다. 키를 넣은 뒤 live 로 바꿉니다.
    'UPSTAGE_MODE'             = 'replay'
    'UPSTAGE_CONFIG_ID'        = '2'
    'UPSTAGE_BASE_URL'         = 'https://api.upstage.ai/v2'
    'UPSTAGE_POLL_INTERVAL_MS' = '3000'
    'UPSTAGE_JOB_TIMEOUT_SEC'  = '600'
    'DB_URL'                   = 'jdbc:postgresql://localhost:5434/docguard'
    'DB_USERNAME'              = 'docguard'
    'DB_PASSWORD'              = $dbPassword
    'APP_ENCRYPTION_KEY'       = $encryptionKey
    'APP_STORAGE_DIR'          = '../storage'
    'APP_RETENTION_HOURS'      = '168'
    'APP_COOKIE_SECURE'        = 'false'
}

$lines = [System.IO.File]::ReadAllLines($examplePath, [System.Text.Encoding]::UTF8)
$output = New-Object System.Collections.Generic.List[string]
$output.Add('# scripts/create-env.ps1 이 생성한 로컬 전용 파일입니다. 저장소에 올리지 않습니다(.gitignore).')
foreach ($line in $lines) {
    if ($line -match '^([A-Z0-9_]+)=$' -and $values.ContainsKey($Matches[1])) {
        $output.Add("$($Matches[1])=$($values[$Matches[1]])")
    } else {
        $output.Add($line)
    }
}

# BOM 없는 UTF-8 로 저장합니다. BOM 이 붙으면 첫 변수 이름을 Docker Compose 가 읽지 못합니다.
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($envPath, (($output -join "`n") + "`n"), $utf8NoBom)

Write-Host ".env 를 만들었습니다: $envPath"
Write-Host '- DB_PASSWORD, APP_ENCRYPTION_KEY: 무작위로 생성했습니다(화면에 출력하지 않음).'
Write-Host '- 다음 값은 비어 있습니다. .env 를 편집기로 열어 직접 넣어 주세요.'
Write-Host '    UPSTAGE_API_KEY     (Upstage 콘솔에서 만든 키)'
Write-Host '    UPSTAGE_PROJECT_ID  (워크플로가 속한 Project ID)'
Write-Host '    UPSTAGE_AGENT_ID    (v2.5 워크플로 Agent ID)'
Write-Host '  지금은 UPSTAGE_MODE=replay(저장 응답 재생)입니다. 키를 넣은 뒤 UPSTAGE_MODE=live 로 바꾸세요.'
Write-Host '  키는 채팅이나 다른 파일에 붙여 넣지 마세요.'
