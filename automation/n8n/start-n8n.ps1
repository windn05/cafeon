# n8n 켜기: ERP의 local.properties에서 자동화 키를 읽어 n8n 자격 증명 · 환경 변수로 넘기고,
# 워크플로(workflows/*.json)를 가져와 켠 다음 n8n을 실행한다.
#   powershell -ExecutionPolicy Bypass -File automation\n8n\start-n8n.ps1            # 가져오기 + 실행
#   powershell -ExecutionPolicy Bypass -File automation\n8n\start-n8n.ps1 -ImportOnly # 가져오기만
#   powershell -ExecutionPolicy Bypass -File automation\n8n\start-n8n.ps1 -Run cafeonMorning001  # 워크플로 한 번 실행 (n8n이 켜져 있어도 됨)
param([switch]$ImportOnly, [string]$Run)
$ErrorActionPreference = "Stop"

$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = (Resolve-Path (Join-Path $Here "..\..")).Path
# n8n 프로그램과 데이터는 저장소 밖(사용자 폴더)에 둔다
$N8nHome = if ($env:CAFEON_N8N_HOME) { $env:CAFEON_N8N_HOME } else { Join-Path $env:LOCALAPPDATA "CafeOn\n8n" }
$N8n = Join-Path $N8nHome "node_modules\.bin\n8n.cmd"
if (-not (Test-Path $N8n)) {
  throw "n8n이 설치되어 있지 않습니다. 먼저 실행: npm install --prefix `"$N8nHome`" n8n@1.123.88"
}

function Read-Prop([string]$File, [string]$Key) {
  if (-not (Test-Path $File)) { return $null }
  $line = Get-Content $File -Encoding UTF8 | Where-Object { $_ -match "^\s*$([regex]::Escape($Key))\s*=" } | Select-Object -Last 1
  if ($line) { return ($line -split "=", 2)[1].Trim() }
  return $null
}

$Props = Join-Path $Root "erp-server\local.properties"
$ApiKey = Read-Prop $Props "automation.api-key"
$Secret = Read-Prop $Props "automation.webhook-secret"
if (-not $ApiKey -or -not $Secret) {
  throw "erp-server\local.properties에 automation.api-key와 automation.webhook-secret을 넣어 주세요 (local.properties.example 참고)."
}

$env:N8N_USER_FOLDER = Join-Path $env:LOCALAPPDATA "CafeOn\n8n-data"
$env:N8N_PORT = "5678"
$env:GENERIC_TIMEZONE = "Asia/Seoul"
$env:TZ = "Asia/Seoul"
$env:N8N_DIAGNOSTICS_ENABLED = "false"
$env:N8N_PERSONALIZATION_ENABLED = "false"
$env:N8N_VERSION_NOTIFICATIONS_ENABLED = "false"
# 웹훅 원문(raw body)을 Code 노드에서 읽어 서명을 확인하므로 바이너리를 메모리에 둔다
$env:N8N_DEFAULT_BINARY_DATA_MODE = "default"
# Code 노드: 서명 계산(crypto) · 환경 변수($env) 사용 허용
$env:NODE_FUNCTION_ALLOW_BUILTIN = "crypto"
$env:N8N_BLOCK_ENV_ACCESS_IN_NODE = "false"
$env:N8N_RUNNERS_ENABLED = "false"
# 워크플로가 쓰는 값
if (-not $env:ERP_API_URL) { $env:ERP_API_URL = "http://localhost:8080/api/erp/v1" }
if (-not $env:AUTO_APPROVE_LIMIT) { $env:AUTO_APPROVE_LIMIT = "300000" }
$env:ERP_WEBHOOK_SECRET = $Secret

New-Item -ItemType Directory -Force -Path $env:N8N_USER_FOLDER | Out-Null

# 수동 실행: 이미 가져온 워크플로를 한 번 돌리고 끝낸다 (예약 시각을 기다리지 않고 확인할 때)
if ($Run) {
  & $N8n execute --id="$Run"
  return
}

# ERP 자동화 키 → n8n 자격 증명 (n8n이 자기 암호화 키로 저장). 임시 파일은 바로 지운다
$Tmp = Join-Path $env:N8N_USER_FOLDER "erp-credential.tmp.json"
$Cred = @{ id = "cafeonErpKey0001"; name = "ERP 자동화 키"; type = "httpHeaderAuth"; data = @{ name = "X-Automation-Key"; value = $ApiKey } }
$Json = ConvertTo-Json -InputObject @($Cred) -Depth 5
[IO.File]::WriteAllText($Tmp, $Json, (New-Object Text.UTF8Encoding $false))
try {
  & $N8n import:credentials --input="$Tmp"
} finally {
  Remove-Item $Tmp -Force -ErrorAction SilentlyContinue
}

# 가져오면 꺼진 상태가 되므로 다시 켠다 (n8n이 시작하면서 웹훅 · 일정을 등록)
& $N8n import:workflow --separate --input="$(Join-Path $Here 'workflows')"
& $N8n update:workflow --all --active=true

if ($ImportOnly) { return }
& $N8n start
