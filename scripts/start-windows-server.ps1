#Requires -Version 5.1

param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'status', 'mysql-start')]
    [string]$Action = 'start',

    [Parameter()]
    [switch]$Yes,

    [Parameter()]
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
[Console]::InputEncoding  = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# ============================================================
#  默认配置（环境变量可覆盖）
# ============================================================
$MYSQL_HOST            = '127.0.0.1'
$MYSQL_PORT            = '3306'
$MYSQL_DB              = 'beidou'
$MYSQL_USER            = 'root'
$MYSQL_PASSWORD        = 'root'
$MYSQL_CONNECT_TIMEOUT = '5'
$ALLOW_EMPTY_DB        = '0'
$MYSQL_SERVICE         = ''
$WEB_PORT              = 8686
$LOGIN_PORT            = 8484
$CHANNEL_PORTS         = @(7575, 7576, 7577)
$JAVA_XMX              = '4G'
$SERVER_LOG            = ''
$SERVER_PID_FILE       = "$env:TEMP\beidou-windows-server.pid"

if ($env:MYSQL_HOST)             { $MYSQL_HOST             = $env:MYSQL_HOST }
if ($env:MYSQL_PORT)             { $MYSQL_PORT             = $env:MYSQL_PORT }
if ($env:MYSQL_DB)               { $MYSQL_DB               = $env:MYSQL_DB }
if ($env:MYSQL_USER)             { $MYSQL_USER             = $env:MYSQL_USER }
if ($env:MYSQL_PASSWORD)         { $MYSQL_PASSWORD         = $env:MYSQL_PASSWORD }
if ($env:MYSQL_CONNECT_TIMEOUT)  { $MYSQL_CONNECT_TIMEOUT  = $env:MYSQL_CONNECT_TIMEOUT }
if ($env:ALLOW_EMPTY_DB)         { $ALLOW_EMPTY_DB         = $env:ALLOW_EMPTY_DB }
if ($env:MYSQL_SERVICE)          { $MYSQL_SERVICE          = $env:MYSQL_SERVICE }
if ($env:WEB_PORT)               { $WEB_PORT               = [int]$env:WEB_PORT }
if ($env:LOGIN_PORT)             { $LOGIN_PORT             = [int]$env:LOGIN_PORT }
if ($env:CHANNEL_PORTS)          { $CHANNEL_PORTS          = @($env:CHANNEL_PORTS -split '\s+' | Where-Object { $_ } | ForEach-Object { [int]$_ }) }
if ($env:JAVA_XMX)               { $JAVA_XMX               = $env:JAVA_XMX }
if ($env:SERVER_LOG)             { $SERVER_LOG             = $env:SERVER_LOG }
if ($env:SERVER_PID_FILE)        { $SERVER_PID_FILE        = $env:SERVER_PID_FILE }

# ============================================================
#  路径推导
# ============================================================
$SCRIPT_DIR    = Split-Path -Parent $MyInvocation.MyCommand.Path
$ROOT_DIR      = Resolve-Path "$SCRIPT_DIR\.."
$SERVER_DIR    = "$ROOT_DIR\gms-server"
$JAR_PATH      = "$SERVER_DIR\target\BeiDou.jar"
$CONFIG_PATH   = "$SERVER_DIR\src\main\resources\application.yml"
$MIGRATION_DIR = "$SERVER_DIR\src\main\resources\db\migration"

if ($SERVER_LOG -eq '') {
    $SERVER_LOG = "$SERVER_DIR\logs\windows-server.log"
}

# ============================================================
#  MySQL 工具查找
# ============================================================
$MYSQL_EXE      = $null
$MYSQLADMIN_EXE = $null

function Find-MySQLTools {
    $mysqlCmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($mysqlCmd) {
        $script:MYSQL_EXE = $mysqlCmd.Source
        $script:MYSQLADMIN_EXE = Join-Path (Split-Path $mysqlCmd.Source) 'mysqladmin.exe'
        if (Test-Path $script:MYSQLADMIN_EXE) {
            return
        }
    }
    $searchPaths = @(
        'C:\Program Files\MySQL\MySQL Server 8.0\bin',
        'C:\Program Files\MySQL\MySQL Server 8.4\bin',
        'C:\Program Files\MySQL\MySQL Server 5.7\bin'
    )
    foreach ($searchPath in $searchPaths) {
        $testExe = Join-Path $searchPath 'mysql.exe'
        if (Test-Path $testExe) {
            $script:MYSQL_EXE = $testExe
            $script:MYSQLADMIN_EXE = Join-Path $searchPath 'mysqladmin.exe'
            return
        }
    }
    $scoopPath = "$env:USERPROFILE\scoop\apps\mysql\current\bin\mysql.exe"
    if (Test-Path $scoopPath) {
        $script:MYSQL_EXE = $scoopPath
        $script:MYSQLADMIN_EXE = Join-Path (Split-Path $scoopPath) 'mysqladmin.exe'
    }
}

# ============================================================
#  MySQL 服务名检测
# ============================================================
function Get-MySQLServiceName {
    if ($MYSQL_SERVICE -ne '') {
        return $MYSQL_SERVICE
    }
    $allServices = Get-Service
    foreach ($svc in $allServices) {
        if ($svc.Name -match '^MySQL' -or $svc.Name -match '^MariaDB') {
            return $svc.Name
        }
    }
    $fallbackNames = @('MySQL80', 'MySQL84', 'MySQL57', 'MySQL', 'MariaDB')
    foreach ($name in $fallbackNames) {
        $testSvc = Get-Service $name -ErrorAction SilentlyContinue
        if ($testSvc) {
            return $name
        }
    }
    return $null
}

# ============================================================
#  工具函数
# ============================================================
function Write-Log {
    param([string]$Message)
    $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    Write-Host "[beidou-win $ts] $Message"
}

function Write-Fail {
    param([string]$Message)
    Write-Host "[beidou-win] ERROR: $Message" -ForegroundColor Red
    exit 1
}

function Invoke-MySQL {
    param([string[]]$Arguments)
    $baseArgs = @(
        '--protocol=tcp',
        "--connect-timeout=$MYSQL_CONNECT_TIMEOUT",
        '-h', $MYSQL_HOST,
        '-P', $MYSQL_PORT,
        '-u', $MYSQL_USER
    )
    $allArgs = $baseArgs + $Arguments
    $env:MYSQL_PWD = $MYSQL_PASSWORD
    $saved = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $result = & $MYSQL_EXE $allArgs 2>$null
    $ErrorActionPreference = $saved
    $env:MYSQL_PWD = ''
    return $result
}

function Invoke-MySQLScalar {
    param([string[]]$Arguments)
    $merged = @('--batch', '--skip-column-names') + $Arguments
    $result = Invoke-MySQL -Arguments $merged
    return ($result -join '').Trim()
}

function Test-MySQLPing {
    if (-not $MYSQLADMIN_EXE) {
        return $false
    }
    $env:MYSQL_PWD = $MYSQL_PASSWORD
    $saved = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $MYSQLADMIN_EXE --protocol=tcp --connect-timeout=$MYSQL_CONNECT_TIMEOUT `
        -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER ping 2>$null 1>$null
    $ok = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $saved
    $env:MYSQL_PWD = ''
    return $ok
}

function Test-PortListening {
    param([int]$Port)
    $netstatOut = netstat -ano 2>$null
    if ($netstatOut | Select-String ":$Port\s") {
        return $true
    }
    $tcpConn = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    if ($tcpConn) {
        return $true
    }
    return $false
}

function Test-TableExists {
    param([string]$TableName)
    $sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$MYSQL_DB' AND table_name='$TableName';"
    $result = Invoke-MySQLScalar -Arguments @('-e', $sql)
    if ($result -eq '1') {
        return $true
    }
    return $false
}

function Test-DBExists {
    $sql = "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$MYSQL_DB';"
    $result = Invoke-MySQLScalar -Arguments @('-e', $sql)
    if ($result -eq '1') {
        return $true
    }
    return $false
}

function Get-DBCount {
    param([string]$TableName)
    if (Test-TableExists $TableName) {
        return Invoke-MySQLScalar -Arguments @($MYSQL_DB, '-e', "SELECT COUNT(*) FROM $TableName;")
    }
    return 'missing'
}

function Test-ServerRunning {
    $srvPid = Get-ServerPID
    if (-not $srvPid) {
        return $false
    }
    $proc = Get-Process -Id $srvPid -ErrorAction SilentlyContinue
    if ($proc) {
        return $true
    }
    return $false
}

function Get-ServerPID {
    if (Test-Path $SERVER_PID_FILE) {
        $content = Get-Content $SERVER_PID_FILE -Raw -ErrorAction SilentlyContinue
        if ($content) {
            $srvPidVal = $content.Trim()
            if ($srvPidVal -match '^\d+$') {
                $intPid = [int]$srvPidVal
                $proc = Get-Process -Id $intPid -ErrorAction SilentlyContinue
                if ($proc -and ($proc.ProcessName -match 'java')) {
                    return $intPid
                }
            }
        }
    }
    $javaProcs = Get-Process -Name 'java' -ErrorAction SilentlyContinue
    foreach ($proc in $javaProcs) {
        $procInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($proc.Id)" -ErrorAction SilentlyContinue
        if (-not $procInfo -and (Get-Command Get-WmiObject -ErrorAction SilentlyContinue)) {
            $procInfo = Get-WmiObject Win32_Process -Filter "ProcessId=$($proc.Id)" -ErrorAction SilentlyContinue
        }
        if ($procInfo -and ($procInfo.CommandLine -match 'BeiDou\.jar|ServerApplication')) {
            return $proc.Id
        }
    }
    return $null
}

# ============================================================
#  MySQL 启停
# ============================================================
function Start-WindowsMySQL {
    if (Test-MySQLPing) {
        Write-Log "MySQL already responds on ${MYSQL_HOST}:$MYSQL_PORT"
        return
    }
    $svcName = Get-MySQLServiceName
    if (-not $svcName) {
        Write-Fail "Cannot detect MySQL Windows service. Set MYSQL_SERVICE env var."
    }
    $svc = Get-Service $svcName -ErrorAction SilentlyContinue
    if (-not $svc) {
        Write-Fail "MySQL service '$svcName' not found."
    }
    Write-Log "Starting Windows MySQL service '$svcName'..."
    Start-Service $svcName -ErrorAction Stop

    $i = 0
    while ($i -lt 30) {
        if (Test-MySQLPing) {
            break
        }
        Start-Sleep -Seconds 1
        $i++
    }
    if (-not (Test-MySQLPing)) {
        Write-Fail "MySQL did not become ready on ${MYSQL_HOST}:$MYSQL_PORT"
    }
    Write-Log "MySQL is ready."
}

function Confirm-ExistingDB {
    if (-not (Test-DBExists)) {
        Write-Fail "Database '$MYSQL_DB' does not exist. Create it manually and re-run."
    }
    $acct = Get-DBCount 'accounts'
    $char = Get-DBCount 'characters'
    Write-Log "DB '$MYSQL_DB' exists. accounts=$acct, characters=$char"

    if ($ALLOW_EMPTY_DB -ne '1') {
        if ($acct -eq 'missing' -or $char -eq 'missing') {
            Write-Fail "accounts/characters table missing. Does not look like the original DB."
        }
        if ($acct -eq '0' -and $char -eq '0') {
            Write-Fail "accounts and characters are both empty. Set ALLOW_EMPTY_DB=1 for fresh DB."
        }
    }
}

function Warn-PendingDestructiveMigration {
    $migrationFile = "$MIGRATION_DIR\V1.11.5__reset_hp_challenge_for_life_proof.sql"
    if (-not (Test-Path $migrationFile)) {
        return
    }
    if (-not (Test-TableExists 'flyway_schema_history')) {
        return
    }
    $sql = "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1.11.5' AND success=1;"
    $applied = Invoke-MySQLScalar -Arguments @($MYSQL_DB, '-e', $sql)
    if ($applied -ne '0') {
        return
    }

    Write-Host ''
    Write-Host ' ================================================================ ' -ForegroundColor Yellow
    Write-Host '  WARNING: V1.11.5 is NOT applied yet.' -ForegroundColor Yellow
    Write-Host '  Starting server will let Flyway CLEAR hp_challenge_* tables.' -ForegroundColor Yellow
    Write-Host ' ================================================================ ' -ForegroundColor Yellow
    Write-Host ''

    if (Test-TableExists 'hp_challenge_state') {
        Write-Log "Current hp_challenge_state rows: $(Get-DBCount 'hp_challenge_state')"
    }
    if (Test-TableExists 'hp_challenge_progress') {
        Write-Log "Current hp_challenge_progress rows: $(Get-DBCount 'hp_challenge_progress')"
    }
    if (Test-TableExists 'hp_challenge_reward_log') {
        Write-Log "Current hp_challenge_reward_log rows: $(Get-DBCount 'hp_challenge_reward_log')"
    }
    Write-Host ''

    if ($Yes) {
        Write-Log "-Yes passed; continuing."
        return
    }

    $answer = Read-Host "Type 'yes' to start server and allow Flyway to run pending migrations"
    if ($answer -ne 'yes') {
        Write-Fail 'Startup cancelled by user.'
    }
}

function Assert-ServerPortsFree {
    $allPorts = @($WEB_PORT, $LOGIN_PORT) + $CHANNEL_PORTS
    foreach ($port in $allPorts) {
        if (Test-PortListening $port) {
            Write-Fail "Port $port is already listening. Stop the existing server first."
        }
    }
}

# ============================================================
#  Java 查找
# ============================================================
function Find-Java {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    $bundled = "$SERVER_DIR\jdk-21.0.11+10-jre\bin\java.exe"
    if (Test-Path $bundled) {
        return $bundled
    }
    $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($onPath) {
        return $onPath.Source
    }
    return $null
}

# ============================================================
#  服务端启停
# ============================================================
function Start-Server {
    Write-Host ''
    Write-Host ' ============================================================= ' -ForegroundColor Cyan
    Write-Host '           BeiDou Server - Windows Launcher                  ' -ForegroundColor Cyan
    Write-Host ' ============================================================= ' -ForegroundColor Cyan
    Write-Host ''

    if (-not (Test-Path $JAR_PATH)) {
        Write-Fail "JAR not found: $JAR_PATH. Build first: mvn clean package -pl gms-server"
    }

    Find-MySQLTools
    if (-not $MYSQL_EXE) {
        Write-Fail 'mysql.exe not found. Install MySQL client tools or add to PATH.'
    }

    $javaExe = Find-Java
    if (-not $javaExe) {
        Write-Fail 'Java not found. Set JAVA_HOME or install JDK 21.'
    }
    Write-Log "Java: $javaExe"

    Start-WindowsMySQL
    Confirm-ExistingDB
    Warn-PendingDestructiveMigration
    Assert-ServerPortsFree

    $logDir = Split-Path $SERVER_LOG -Parent
    if (-not (Test-Path $logDir)) {
        New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    }

    Write-Log "Starting server from $SERVER_DIR"
    Write-Log "JAR: $JAR_PATH"
    Write-Log "Config: $CONFIG_PATH"
    Write-Log "Log file: $SERVER_LOG"

    '' | Out-File -FilePath $SERVER_LOG -Encoding UTF8

    $javaArgs = @(
        "-Xmx$JAVA_XMX",
        "-Dspring.config.location=$CONFIG_PATH",
        '-jar', $JAR_PATH
    )

    $proc = Start-Process -FilePath $javaExe `
        -ArgumentList $javaArgs `
        -WorkingDirectory $SERVER_DIR `
        -RedirectStandardOutput $SERVER_LOG `
        -NoNewWindow `
        -PassThru

    if (-not $proc) {
        Write-Fail 'Failed to start Java process.'
    }

    $proc.Id | Out-File -FilePath $SERVER_PID_FILE -Encoding ASCII
    Write-Log "Java PID: $($proc.Id)"

    # Wait for startup
    $i = 0
    $started = $false
    while ($i -lt 120) {
        $logContent = Get-Content $SERVER_LOG -ErrorAction SilentlyContinue -Raw
        $matchText = $logContent -match '启动完成'
        $webOk = Test-PortListening $WEB_PORT
        $loginOk = Test-PortListening $LOGIN_PORT
        if ($matchText -and $webOk -and $loginOk) {
            Start-Sleep -Seconds 2
            $webOk2 = Test-PortListening $WEB_PORT
            $loginOk2 = Test-PortListening $LOGIN_PORT
            if ($webOk2 -and $loginOk2) {
                Write-Log 'Server started successfully!'
                Write-Log "Web: http://127.0.0.1:$WEB_PORT/"
                Write-Log "Login port: $LOGIN_PORT"
                $started = $true
                break
            }
        }
        $procAlive = Get-Process -Id $proc.Id -ErrorAction SilentlyContinue
        if (-not $procAlive) {
            Write-Host ''
            Write-Host '=== Last 80 lines of server log ===' -ForegroundColor Red
            Get-Content $SERVER_LOG -Tail 80 -ErrorAction SilentlyContinue | Write-Host
            Write-Fail 'Server process exited during startup. See log above.'
        }
        Start-Sleep -Seconds 1
        $i++
    }

    if (-not $started) {
        Write-Host ''
        Write-Host '=== Last 90 lines of server log ===' -ForegroundColor Red
        Get-Content $SERVER_LOG -Tail 90 -ErrorAction SilentlyContinue | Write-Host
        Write-Fail 'Timed out waiting for server startup.'
    }
}

function Stop-Server {
    if (-not (Test-ServerRunning)) {
        Write-Log 'No server process is running.'
        return
    }

    $srvPid = Get-ServerPID
    Write-Log "Stopping server PID $srvPid"

    Stop-Process -Id $srvPid -ErrorAction SilentlyContinue

    $waited = 0
    while ($waited -lt 20) {
        $alive = Get-Process -Id $srvPid -ErrorAction SilentlyContinue
        if (-not $alive) {
            break
        }
        Start-Sleep -Seconds 1
        $waited++
    }

    $alive = Get-Process -Id $srvPid -ErrorAction SilentlyContinue
    if ($alive) {
        $alive.Kill()
    }

    if (Test-Path $SERVER_PID_FILE) {
        Remove-Item $SERVER_PID_FILE -Force -ErrorAction SilentlyContinue
    }
    Write-Log 'Server stopped.'
}

function Show-Status {
    Write-Host ''
    Write-Host '==================== BeiDou Server Status ====================' -ForegroundColor Cyan

    Write-Host ''
    Write-Host '-- MySQL --' -ForegroundColor DarkYellow
    Find-MySQLTools
    if ($MYSQL_EXE) {
        if (Test-MySQLPing) {
            $ver = Invoke-MySQLScalar -Arguments @('-e', 'SELECT VERSION();')
            Write-Host "  Status  : running on ${MYSQL_HOST}:$MYSQL_PORT (version $ver)"
            if (Test-DBExists) {
                Write-Host "  DB      : $MYSQL_DB"
                $acct = Get-DBCount 'accounts'
                $char = Get-DBCount 'characters'
                Write-Host "  accounts : $acct"
                Write-Host "  characters: $char"
                if (Test-TableExists 'flyway_schema_history') {
                    Write-Host '  Recent Flyway migrations:'
                    $rows = Invoke-MySQL -Arguments @(
                        $MYSQL_DB, '-e',
                        'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;'
                    )
                    foreach ($row in $rows) {
                        Write-Host "    $row"
                    }
                }
            }
            else {
                Write-Host "  DB '$MYSQL_DB': missing" -ForegroundColor Red
            }
        }
        else {
            Write-Host "  MySQL: not responding on ${MYSQL_HOST}:$MYSQL_PORT" -ForegroundColor Red
        }
    }
    else {
        Write-Host '  MySQL tools not found in PATH' -ForegroundColor DarkGray
    }

    Write-Host ''
    Write-Host '-- Server --' -ForegroundColor DarkYellow
    if (Test-ServerRunning) {
        $spid = Get-ServerPID
        Write-Host "  PID: $spid" -ForegroundColor Green
    }
    else {
        Write-Host '  Status: not running' -ForegroundColor DarkGray
    }

    Write-Host ''
    Write-Host '-- Ports --' -ForegroundColor DarkYellow
    $portList = @(
        @{Label='MySQL';   Port=[int]$MYSQL_PORT},
        @{Label='Web';     Port=$WEB_PORT},
        @{Label='Login';   Port=$LOGIN_PORT}
    )
    $chIdx = 1
    foreach ($cp in $CHANNEL_PORTS) {
        $portList += @{Label="Channel$chIdx"; Port=[int]$cp}
        $chIdx++
    }
    foreach ($entry in $portList) {
        $listening = Test-PortListening $entry.Port
        if ($listening) {
            $display = 'LISTENING'
            $color = 'Green'
        }
        else {
            $display = '-'
            $color = 'DarkGray'
        }
        $labelPadded = $entry.Label.PadRight(10)
        $portPadded = (":$($entry.Port)").PadRight(7)
        Write-Host "  $labelPadded $portPadded $display" -ForegroundColor $color
    }
    Write-Host ''
}

# ============================================================
#  帮助
# ============================================================
function Show-Help {
    Write-Host @'

BeiDou Server - Windows Launcher

Usage:  .\start-windows-server.ps1 [start|stop|status|mysql-start] [-Yes] [-Help]

Actions:
  start        Start MySQL, validate DB, check migrations, start server
  stop         Stop the server process (MySQL stays running)
  status       Show MySQL, DB, server process, and port status
  mysql-start  Start Windows MySQL only and validate DB

Options:
  -Yes         Skip migration confirmation prompt

Environment variables:
  MYSQL_HOST, MYSQL_PORT, MYSQL_DB, MYSQL_USER, MYSQL_PASSWORD
  MYSQL_SERVICE  (auto-detected if not set)
  ALLOW_EMPTY_DB (default 0)
  WEB_PORT (8686), LOGIN_PORT (8484)
  CHANNEL_PORTS (7575 7576 7577)
  JAVA_XMX (4G)
  SERVER_LOG, SERVER_PID_FILE
'@
}

# ============================================================
#  主入口
# ============================================================
if ($Help) {
    Show-Help
    exit 0
}

$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
$isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if ($Action -eq 'start') {
    if (-not $isAdmin) {
        Write-Host '[beidou-win] WARNING: Not running as Administrator.' -ForegroundColor Yellow
        Write-Host "[beidou-win] MySQL service start may fail. Re-run as Administrator if needed." -ForegroundColor Yellow
        Write-Host ''
    }
    Start-Server
}
elseif ($Action -eq 'stop') {
    Stop-Server
}
elseif ($Action -eq 'status') {
    Show-Status
}
elseif ($Action -eq 'mysql-start') {
    if (-not $MYSQL_EXE) {
        Find-MySQLTools
    }
    Start-WindowsMySQL
    Confirm-ExistingDB
    Write-Log "MySQL is ready. DB '$MYSQL_DB' verified."
}
