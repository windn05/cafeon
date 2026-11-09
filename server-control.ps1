# CafeOn 서버 켜기 · 끄기
# 더블클릭은 "서버관리.bat". 명령줄에서는: powershell -File server-control.ps1 -Action start|stop|status
param([ValidateSet("gui", "start", "stop", "status")][string]$Action = "gui")

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
# 로그는 문서 폴더 밖에 둔다 (Windows '제어된 폴더 액세스'가 cmd의 문서 폴더 쓰기를 막는다)
$LogDir = Join-Path $env:LOCALAPPDATA "CafeOn\logs"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$PidFile = Join-Path $LogDir "pids.json"

$Servers = @(
  [pscustomobject]@{
    Key = "erp"; Name = "본사 ERP 서버"; Port = 8080
    Cmd = "`"$Root\erp-server\gradlew.bat`" -p `"$Root\erp-server`" bootRun --no-daemon"
  },
  [pscustomobject]@{
    Key = "pos"; Name = "매장 POS 서버"; Port = 8081
    Cmd = "`"$Root\pos-server\gradlew.bat`" -p `"$Root\pos-server`" bootRun --no-daemon"
  },
  [pscustomobject]@{
    Key = "front"; Name = "화면 (웹)"; Port = 3000
    Cmd = "set `"BROWSER=none`" && set `"PORT=3000`" && set `"HOST=127.0.0.1`" && npm --prefix `"$Root\web`" start"
  }
)
$AppUrl = "http://localhost:3000"
$Starting = @{} # 켜는 중인 서버: key → 시작 시각

# ----- 상태 -----
function Test-Listening([int]$Port) {
  [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Get-DbStatus {
  $svc = Get-Service -Name "MySQL84" -ErrorAction SilentlyContinue
  if (-not $svc) { return "없음" }
  if ($svc.Status -eq "Running") { return "실행 중" }
  return "꺼짐"
}

# 켠 프로세스 번호를 시작 시각과 같이 적어 둔다 (재부팅 뒤 같은 번호의 다른 프로그램을 끄지 않게)
function Read-Pids {
  if (-not (Test-Path $PidFile)) { return @{} }
  try {
    $obj = Get-Content $PidFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $map = @{}
    $obj.PSObject.Properties | ForEach-Object { $map[$_.Name] = $_.Value }
    return $map
  } catch { return @{} }
}

function Save-Pid([string]$Key, $Proc) {
  $map = Read-Pids
  $map[$Key] = [pscustomobject]@{ Id = $Proc.Id; Started = $Proc.StartTime.ToString("o") }
  ($map | ConvertTo-Json) | Set-Content -Path $PidFile -Encoding UTF8
}

function Stop-Tree([int]$ProcessId) {
  & taskkill.exe /PID $ProcessId /T /F 2>$null | Out-Null
}

# ----- 켜기 · 끄기 -----
function Start-One($s) {
  if (Test-Listening $s.Port) { return "이미 켜져 있음" }
  if ($Starting.ContainsKey($s.Key)) { return "켜는 중" }
  $log = Join-Path $LogDir "$($s.Key).log"
  $proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/s /c `"$($s.Cmd) > `"$log`" 2>&1`"" `
    -WorkingDirectory $Root -WindowStyle Hidden -PassThru
  Save-Pid $s.Key $proc
  $Starting[$s.Key] = Get-Date
  return "켜는 중"
}

function Stop-One($s) {
  $Starting.Remove($s.Key)
  $saved = (Read-Pids)[$s.Key]
  if ($saved) {
    $p = Get-Process -Id $saved.Id -ErrorAction SilentlyContinue
    if ($p -and $p.StartTime.ToString("o") -eq $saved.Started) { Stop-Tree $saved.Id }
  }
  # 다른 곳에서 켠 서버도 끌 수 있게, 그 포트를 듣고 있는 프로세스까지 끈다
  Get-NetTCPConnection -LocalPort $s.Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object { if ($_ -gt 4) { Stop-Tree $_ } }
  return "꺼짐"
}

function Get-State($s) {
  if (Test-Listening $s.Port) { $Starting.Remove($s.Key); return "on" }
  if ($Starting.ContainsKey($s.Key)) {
    if (((Get-Date) - $Starting[$s.Key]).TotalSeconds -gt 240) { return "fail" }
    return "starting"
  }
  return "off"
}

# ----- 명령줄 모드 -----
if ($Action -ne "gui") {
  foreach ($s in $Servers) {
    $r = switch ($Action) {
      "start" { Start-One $s }
      "stop" { Stop-One $s }
      "status" { if (Test-Listening $s.Port) { "켜짐" } else { "꺼짐" } }
    }
    Write-Output ("{0} ({1}): {2}" -f $s.Name, $s.Port, $r)
  }
  Write-Output ("DB MySQL 8.4 (3307): {0}" -f (Get-DbStatus))
  return
}

# ----- 창 -----
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$font = New-Object System.Drawing.Font("Malgun Gothic", 10)
$bold = New-Object System.Drawing.Font("Malgun Gothic", 10, [System.Drawing.FontStyle]::Bold)
$colors = @{
  on       = [System.Drawing.Color]::FromArgb(46, 125, 50)
  off      = [System.Drawing.Color]::FromArgb(130, 130, 130)
  starting = [System.Drawing.Color]::FromArgb(191, 120, 0)
  fail     = [System.Drawing.Color]::FromArgb(198, 40, 40)
}
$texts = @{ on = "● 실행 중"; off = "● 꺼짐"; starting = "● 켜는 중…"; fail = "● 안 켜짐 (로그 확인)" }

$form = New-Object System.Windows.Forms.Form
$form.Text = "CafeOn 서버"
$form.Font = $font
$form.ClientSize = New-Object System.Drawing.Size(490, 290)
$form.FormBorderStyle = "FixedSingle"
$form.MaximizeBox = $false
$form.StartPosition = "CenterScreen"
$form.BackColor = [System.Drawing.Color]::White

$rows = @{}
$y = 20
foreach ($s in $Servers) {
  $name = New-Object System.Windows.Forms.Label
  $name.Text = "$($s.Name)  :$($s.Port)"
  $name.Font = $bold
  $name.Location = New-Object System.Drawing.Point(20, ($y + 4))
  $name.AutoSize = $true
  $form.Controls.Add($name)

  $state = New-Object System.Windows.Forms.Label
  $state.Location = New-Object System.Drawing.Point(200, ($y + 4))
  $state.Size = New-Object System.Drawing.Size(150, 22)
  $form.Controls.Add($state)

  $btn = New-Object System.Windows.Forms.Button
  $btn.Location = New-Object System.Drawing.Point(360, $y)
  $btn.Size = New-Object System.Drawing.Size(80, 30)
  $btn.Tag = $s.Key
  $btn.Add_Click({
      $target = $Servers | Where-Object { $_.Key -eq $this.Tag }
      if ((Get-State $target) -eq "off") { Start-One $target | Out-Null } else { Stop-One $target | Out-Null }
      Update-View
    })
  $form.Controls.Add($btn)

  $rows[$s.Key] = @{ State = $state; Button = $btn }
  $y += 40
}

$db = New-Object System.Windows.Forms.Label
$db.Location = New-Object System.Drawing.Point(20, ($y + 4))
$db.AutoSize = $true
$db.ForeColor = $colors.off
$form.Controls.Add($db)
$y += 40

function New-Button([string]$Text, [int]$X, [int]$W) {
  $b = New-Object System.Windows.Forms.Button
  $b.Text = $Text
  $b.Location = New-Object System.Drawing.Point($X, $y)
  $b.Size = New-Object System.Drawing.Size($W, 36)
  $form.Controls.Add($b)
  return $b
}

$startAll = New-Button "모두 켜기" 20 100
$stopAll = New-Button "모두 끄기" 128 100
$openApp = New-Button "화면 열기" 236 100
$openLog = New-Button "로그" 344 96
$startAll.Font = $bold

$hint = New-Object System.Windows.Forms.Label
$hint.Text = "창을 닫아도 서버는 켜진 채로 남습니다. 처음 켤 때는 1~2분 걸립니다."
$hint.ForeColor = $colors.off
$hint.Location = New-Object System.Drawing.Point(20, ($y + 50))
$hint.AutoSize = $true
$form.Controls.Add($hint)

function Update-View {
  foreach ($s in $Servers) {
    $st = Get-State $s
    $row = $rows[$s.Key]
    $row.State.Text = $texts[$st]
    $row.State.ForeColor = $colors[$st]
    $row.Button.Text = if ($st -eq "off") { "켜기" } else { "끄기" }
  }
  $dbState = Get-DbStatus
  $db.Text = "DB  MySQL 8.4 :3307   $dbState"
  $db.ForeColor = if ($dbState -eq "실행 중") { $colors.on } else { $colors.fail }
}

$startAll.Add_Click({ foreach ($s in $Servers) { Start-One $s | Out-Null }; Update-View })
$stopAll.Add_Click({
    $form.Cursor = "WaitCursor"
    foreach ($s in $Servers) { Stop-One $s | Out-Null }
    $form.Cursor = "Default"
    Update-View
  })
$openApp.Add_Click({ Start-Process $AppUrl })
$openLog.Add_Click({ Start-Process explorer.exe $LogDir })

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 2000
$timer.Add_Tick({ Update-View })
$timer.Start()

Update-View
[void]$form.ShowDialog()
$timer.Stop()
