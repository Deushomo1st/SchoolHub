# ============================================================================
# SchoolHub Manager — Unified Launcher
# ============================================================================
# One entry point. Replaces Start-SchoolHub.cmd + Manage-Database.cmd.
# Double-click SchoolHub-Manager.cmd in the project root.
#
#  1) Start services    5) Super-admins
#  2) Stop services     6) Debug
#  3) Switch database   7) New laptop setup
#  4) Database tools
# ============================================================================
#requires -Version 5.1
$ErrorActionPreference = 'Stop'

# ---- Constants -------------------------------------------------------------
$ProjectRoot  = Split-Path -Parent $PSScriptRoot
$RefFile      = Join-Path $ProjectRoot 'active-db.properties'
$SecretsFile  = Join-Path $ProjectRoot '.schoolhub_secrets.txt'
$PidsFile     = Join-Path $ProjectRoot '.schoolhub_pids.txt'
$PlatformSql  = Join-Path $ProjectRoot 'db\00_platform.sql'
$TenantSql    = Join-Path $ProjectRoot 'db\tenant_template.sql'
$DbHost       = 'localhost'
$DbPort       = 5432
$DbUser       = 'postgres'
$GatewayUrl   = 'http://localhost:9000'

$Services = @(
    @{ Name = 'AuthService';   Port = 9001; Health = '/health' }
    @{ Name = 'TenantService'; Port = 9002 }
    @{ Name = 'SchoolService'; Port = 9003 }
    @{ Name = 'ApiGateway';    Port = 9000; Health = '/health/auth' }
)

# ---- PostgreSQL helpers ----------------------------------------------------
function Find-Psql {
    # Scan PostgreSQL install dirs first
    foreach ($base in @('C:\Program Files\PostgreSQL', 'C:\Program Files (x86)\PostgreSQL')) {
        if (Test-Path $base) {
            $found = Get-ChildItem $base -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $p = Join-Path $_.FullName 'bin\psql.exe'; if (Test-Path $p) { $p }
            } | Select-Object -First 1
            if ($found) { return $found }
        }
    }
    # Fallback: PATH
    $cmd = Get-Command psql.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd }
    return $null
}

$Psql = Find-Psql
if (-not $Psql) { Write-Host "PostgreSQL (psql.exe) not found. Install PostgreSQL 14+ and retry." -ForegroundColor Red; exit 1 }

if (-not $env:PGPASSWORD) { $env:PGPASSWORD = $DbUser }
function Test-PgAuth {
    & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'postgres' -c 'SELECT 1' -t -A *>$null
    return ($LASTEXITCODE -eq 0)
}
function Ensure-PgAuth {
    if (Test-PgAuth) { return $true }
    $pw = Read-Host "Postgres password for '$DbUser'" -AsSecureString
    $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($pw))
    $result = & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'postgres' -c 'SELECT 1' 2>&1
    if ($LASTEXITCODE -eq 0) { return $true }
    Write-Host "  psql: $Psql" -ForegroundColor DarkGray
    Write-Host "  exit code: $LASTEXITCODE" -ForegroundColor Red
    if ($result) { Write-Host "  $result" -ForegroundColor Red }
    Write-Host "  Auth failed — check that PostgreSQL is running and password is correct." -ForegroundColor Red
    return $false
}

# ---- Active-db.properties -------------------------------------------------
function Get-ActiveDb {
    if (Test-Path $RefFile) {
        foreach ($line in Get-Content -LiteralPath $RefFile) {
            if ($line -match '^\s*schoolhub\.db\.name\s*=\s*(.+)$') { return $Matches[1].Trim() }
        }
    }
    if ($env:SCHOOLHUB_DB_NAME) { return $env:SCHOOLHUB_DB_NAME }
    return 'schoolhub'
}
function Set-ActiveDb($name) {
@"
# SchoolHub - active database. Managed by setup/SchoolHub-Manager.ps1.
# Every service reads this at boot via spring.config.import. Last set: $(Get-Date -Format o)
schoolhub.db.name=$name
"@ | Set-Content -LiteralPath $RefFile -NoNewline
}

function Get-Databases {
    $raw = & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'postgres' -t -A `
                   -c "SELECT datname FROM pg_database WHERE datistemplate=false AND datname<>'postgres' ORDER BY datname" 2>$null
    return @($raw | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}
function Disconnect-Db($name) {
    & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'postgres' -t -A -c `
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$name' AND pid<>pg_backend_pid();" *>$null
}
function Ensure-Pgcrypto($db) {
    & $Psql -U $DbUser -h $DbHost -p $DbPort -d $db -c "CREATE EXTENSION IF NOT EXISTS pgcrypto;" *>$null
}

# ---- Shared UI helpers ----------------------------------------------------
function Press-Enter { Write-Host ""; Read-Host "Press Enter to continue" | Out-Null }
function Read-PlainPassword($prompt, [bool]$Visible) {
    if ($Visible) { return Read-Host $prompt }
    $s = Read-Host $prompt -AsSecureString
    return [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}
function Pick-Database($prompt) {
    $dbs = Get-Databases
    if (-not $dbs.Count) { Write-Host "No databases found on this server." -ForegroundColor Yellow; return $null }
    Write-Host ""
    for ($i = 0; $i -lt $dbs.Count; $i++) { Write-Host ("  [{0}] {1}" -f ($i + 1), $dbs[$i]) }
    $pick = Read-Host $prompt
    if ($pick -match '^\d+$' -and [int]$pick -ge 1 -and [int]$pick -le $dbs.Count) { return $dbs[[int]$pick - 1] }
    Write-Host "Invalid choice." -ForegroundColor Yellow; return $null
}
function New-Secret {
    $bytes = [byte[]]::new(32)
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes)
}
function Wait-Url($url, $timeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
            if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { return $true }
        } catch { Start-Sleep -Seconds 1 }
    }
    return $false
}

# ---- Check helpers (shared by pre-flight + debug menu) --------------------
function Check-Pg { return Test-PgAuth }
function Check-DbExists($db) {
    if (-not (Test-PgAuth)) { return $false }
    $r = & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'postgres' -t -A -c "SELECT 1 FROM pg_database WHERE datname='$db'" 2>$null
    if (-not $r) { return $false }
    return ($r.Trim() -eq '1')
}
function Check-Schema($db) {
    if (-not (Test-PgAuth)) { return $false }
    $tables = @('app_user', 'tenant', 'activity_event')
    foreach ($t in $tables) {
        $r = & $Psql -U $DbUser -h $DbHost -p $DbPort -d $db -t -A -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='platform' AND table_name='$t'" 2>$null
        if (-not $r -or ([int]$r.Trim()) -eq 0) { return $false }
    }
    return $true
}
function Check-Secret {
    if (-not (Test-Path $SecretsFile))   { return 'missing' }
    $raw = (Get-Content -Raw -LiteralPath $SecretsFile).Trim()
    if (-not $raw)                       { return 'empty' }
    $val = $raw -split '=' | Select-Object -Last 1
    if ($val.Length -lt 32)              { return 'tooshort' }
    try { [Convert]::FromBase64String($val) | Out-Null } catch { return 'badbase64' }
    if ($env:SCHOOLHUB_JWT_SECRET -and $env:SCHOOLHUB_JWT_SECRET -ne $val) { return 'envmismatch' }
    return 'ok'
}
function Check-Ports {
    $conflicts = @()
    foreach ($s in $Services) {
        $c = netstat -ano 2>$null | Select-String ":$($s.Port)\s" | Select-String LISTENING
        if ($c) { $conflicts += "$($s.Name):$($s.Port)" }
    }
    return $conflicts
}
function Check-Java {
    try { $v = & java -version 2>&1 | Select-Object -First 1; return "Java: $v" } catch { return $null }
}
function Check-Maven {
    $m = Join-Path $ProjectRoot 'AuthService\mvnw.cmd'
    return Test-Path $m
}
function Get-RunningProcs {
    try {
        Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction Stop | ForEach-Object {
            $cmd = $_.CommandLine
            if ($cmd -notmatch 'schoolhub|spring-boot') { return }
            $port = ''; if ($cmd -match '--server\.port=(\d+)') { $port = $Matches[1] }
            $svc = ''
            if    ($cmd -match 'AuthServiceApplication')    { $svc = 'AuthService' }
            elseif ($cmd -match 'TenantServiceApplication') { $svc = 'TenantService' }
            elseif ($cmd -match 'SchoolServiceApplication') { $svc = 'SchoolService' }
            elseif ($cmd -match 'ApiGatewayApplication')    { $svc = 'ApiGateway' }
            elseif ($cmd -match 'SchoolHub-(\w+)')          { $svc = $Matches[1] }
            [PSCustomObject]@{ PID = $_.ProcessId; Service = $svc; Port = $port }
        }
    } catch { return @() }
}
function Check-SuperAdmins($db) {
    Ensure-Pgcrypto $db | Out-Null
    $r = & $Psql -U $DbUser -h $DbHost -p $DbPort -d $db -t -A -c `
        "SELECT email, first_name, last_name FROM platform.app_user au JOIN platform.role r ON r.id=au.role_id WHERE r.name='PLATFORM_OWNER' ORDER BY email" 2>$null
    return @($r | Where-Object { $_ } | ForEach-Object { $f = $_ -split '\|'; [PSCustomObject]@{ Email = $f[0]; Name = "$($f[1]) $($f[2])" } })
}

# ---- Pre-flight (runs before launch, reports blocking issues) -------------
function Invoke-PreFlight($db) {
    Write-Host "`n  Pre-flight check..." -ForegroundColor Cyan
    $ok = $true
    function P($label, $cond, $failMsg) { if ($cond) { Write-Host "    [PASS] $label" -ForegroundColor Green } else { Write-Host "    [FAIL] $label — $failMsg" -ForegroundColor Red; Set-Variable -Name ok -Value $false -Scope 1 } }
    P 'Postgres' (Check-Pg) 'Run option 6→2 to diagnose.'
    P "Database '$db'" (Check-DbExists $db) "Create it (option 4) or switch (option 3)."
    $s = Check-Secret
    P 'JWT secret' ($s -eq 'ok') "Secret issue: $s. Use 6→7 to regenerate."
    $c = Check-Ports
    P 'Ports 9000-9003' (-not $c.Count) "Conflicts: $($c -join ', '). Stop stale services (6→8)."
    P 'Java' ([bool](Check-Java)) 'Install Java 21 and ensure java is on PATH.'
    P 'Maven wrapper' (Check-Maven) 'Missing mvnw.cmd — project may be corrupted.'
    if ($ok) { Write-Host "  Pre-flight: all clear" -ForegroundColor Green }
    # Bonus: check if the default "schoolhub" fallback DB exists for IDE runs
    if (-not (Check-DbExists 'schoolhub')) {
        Write-Host "  [NOTE] Safety-net DB 'schoolhub' doesn't exist — it's the fallback when active-db.properties isn't found." -ForegroundColor Yellow
        $create = Read-Host "  Create it now? (y/n)"
        if ($create -eq 'y') {
            & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'postgres' -c "CREATE DATABASE schoolhub;"
            & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'schoolhub' -v ON_ERROR_STOP=1 -f $PlatformSql
            Write-Host "  [OK] 'schoolhub' created with schema." -ForegroundColor Green
        }
    }
    return $ok
}

# ---- Service launcher (internal) ------------------------------------------
function Compile-All {
    Write-Host "  Compiling..." -ForegroundColor Cyan
    foreach ($s in $Services) {
        $mvnw = Join-Path $ProjectRoot "$($s.Name)\mvnw.cmd"
        & $mvnw -f (Join-Path $ProjectRoot "$($s.Name)\pom.xml") -DskipTests -q compile
        if ($LASTEXITCODE -ne 0) { Write-Host "  FAILED: $($s.Name) did not compile." -ForegroundColor Red; return $false }
    }
    Write-Host "  Compiled." -ForegroundColor Green; return $true
}
function Launch-All {
    # Write PIDs placeholder
    "# SchoolHub PIDs — last launch $(Get-Date -Format o)" | Set-Content -LiteralPath $PidsFile -NoNewline
    foreach ($s in $Services) {
        $dir = Join-Path $ProjectRoot $s.Name
        $mvnw = Join-Path $dir 'mvnw.cmd'
        $cmd = "title SchoolHub-$($s.Name) & set SCHOOLHUB_JWT_SECRET=$env:SCHOOLHUB_JWT_SECRET&& set SCHOOLHUB_DB_NAME=$env:SCHOOLHUB_DB_NAME&& `"$mvnw`" -DskipTests spring-boot:run"
        $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/k', $cmd -WorkingDirectory $dir -PassThru
        Write-Host "  $($s.Name) launched (PID $($proc.Id), port $($s.Port))" -ForegroundColor Green
        Start-Sleep -Milliseconds 400
    }
    Write-Host "  Waiting for AuthService (9001)..." -ForegroundColor Cyan
    if (-not (Wait-Url 'http://localhost:9001/health' 150)) { Write-Host "  WARNING: AuthService did not respond in time." -ForegroundColor Yellow }
    Write-Host "  Waiting for ApiGateway (9000)..." -ForegroundColor Cyan
    if (-not (Wait-Url 'http://localhost:9000/health/auth' 90)) { Write-Host "  WARNING: ApiGateway did not respond in time." -ForegroundColor Yellow }
    Write-Host "  All services launched." -ForegroundColor Green
}

# ---- Seed demo school (idempotent) -----------------------------------------
function Seed-DemoSchool {
    function Post($url, $tok, $obj) {
        $h = @{}; if ($tok) { $h['Authorization'] = "Bearer $tok" }
        return Invoke-RestMethod -Method Post $url -Headers $h -ContentType 'application/json' -Body ($obj | ConvertTo-Json -Depth 6)
    }
    function Token($e, $p) {
        # lock=true: PLATFORM_OWNER logins are refused without the padlock flag; other roles ignore it.
        return (Invoke-RestMethod -Method Post "$GatewayUrl/api/v1/auth/login" -ContentType 'application/json' -Body (@{ email = $e; password = $p; lock = $true } | ConvertTo-Json)).accessToken
    }
    try {
        Post "$GatewayUrl/api/v1/tenants/signup" $null @{ schoolName='Demo Secondary School'; code='demo'; templateKey='nigerian_secondary'; planName='Standard'; adminEmail='admin@demo.school'; adminPassword='Demo12345!'; adminFirstName='Amaka'; adminLastName='Admin' } | Out-Null
    } catch {
        Write-Host "  Demo school already present — skipping seed." -ForegroundColor Yellow
        return
    }
    try { Post "$GatewayUrl/api/v1/tenants/moderators" (Token 'owner@schoolhub.local' 'Owner123!') @{ email='mod@schoolhub.local'; password='Mod12345!'; firstName='Moses'; lastName='Mod' } | Out-Null } catch {}
    $adm = Token 'admin@demo.school' 'Demo12345!'
    Post "$GatewayUrl/api/v1/staff" $adm @{ email='principal@demo.school'; password='Princ12345!'; firstName='Pauline'; lastName='Principal'; role='PRINCIPAL' } | Out-Null
    Post "$GatewayUrl/api/v1/staff" $adm @{ email='bursar@demo.school'; password='Burs12345!'; firstName='Bola'; lastName='Bursar'; role='BURSAR' } | Out-Null
    $math = Post "$GatewayUrl/api/v1/subjects" $adm @{ name='Mathematics'; code='MTH' }
    $eng  = Post "$GatewayUrl/api/v1/subjects" $adm @{ name='English'; code='ENG' }
    $tch  = Post "$GatewayUrl/api/v1/teachers" $adm @{ staffNo='STF-001'; firstName='Tunde'; lastName='Bello'; email='teacher@demo.school'; loginPassword='Teach12345!' }
    $cls  = Post "$GatewayUrl/api/v1/classes" $adm @{ name='JSS1A'; levelLabel='JSS1'; classTeacherId=$tch.id }
    Post "$GatewayUrl/api/v1/class-subjects" $adm @{ classId=$cls.id; subjectId=$math.id; teacherId=$tch.id } | Out-Null
    Post "$GatewayUrl/api/v1/class-subjects" $adm @{ classId=$cls.id; subjectId=$eng.id; teacherId=$tch.id } | Out-Null
    $s1 = Post "$GatewayUrl/api/v1/students" $adm @{ admissionNo='ADM-001'; firstName='Chidi'; lastName='Eze'; classId=$cls.id; email='student@demo.school'; loginPassword='Stud12345!' }
    $s2 = Post "$GatewayUrl/api/v1/students" $adm @{ admissionNo='ADM-002'; firstName='Bisi'; lastName='Ade'; classId=$cls.id }
    $asm = Post "$GatewayUrl/api/v1/assessments" $adm @{ classSubjectId=(Post "$GatewayUrl/api/v1/class-subjects" $adm @{ classId=$cls.id; subjectId=$math.id; teacherId=$tch.id } | Select-Object -ExpandProperty id); title='First CA'; term='Term 1'; maxScore=20 }
    Post "$GatewayUrl/api/v1/results" $adm @{ assessmentId=$asm.id; studentId=$s1.id; score=17 } | Out-Null
    Post "$GatewayUrl/api/v1/results" $adm @{ assessmentId=$asm.id; studentId=$s2.id; score=14 } | Out-Null
    Post "$GatewayUrl/api/v1/attendance" $adm @{ studentId=$s1.id; classId=$cls.id; status='present' } | Out-Null
    Post "$GatewayUrl/api/v1/attendance" $adm @{ studentId=$s2.id; classId=$cls.id; status='late' } | Out-Null
    Post "$GatewayUrl/api/v1/guardians" $adm @{ firstName='Ngozi'; lastName='Eze'; email='parent@demo.school'; loginPassword='Paren12345!'; studentIds=@($s1.id); relationship='Mother' } | Out-Null
    Post "$GatewayUrl/api/v1/events" $adm @{ title='Resumption Day'; eventType='event'; audience='all'; startDate=(Get-Date).ToString('yyyy-MM-dd') } | Out-Null
    Post "$GatewayUrl/api/v1/events" $adm @{ title='Midterm Exams'; eventType='exam'; audience='students'; startDate=(Get-Date).AddDays(21).ToString('yyyy-MM-dd') } | Out-Null
    Write-Host "  Demo school seeded." -ForegroundColor Green
}

# ===========================================================================
# MENU ACTIONS
# ===========================================================================

function Launch-One($svc) {
    $dir = Join-Path $ProjectRoot $svc.Name
    $mvnw = Join-Path $dir 'mvnw.cmd'
    $cmd = "title SchoolHub-$($svc.Name) & set SCHOOLHUB_JWT_SECRET=$env:SCHOOLHUB_JWT_SECRET&& set SCHOOLHUB_DB_NAME=$env:SCHOOLHUB_DB_NAME&& `"$mvnw`" -DskipTests spring-boot:run"
    $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/k', $cmd -WorkingDirectory $dir -PassThru
    Write-Host "    $($svc.Name) launched (PID $($proc.Id), port $($svc.Port))" -ForegroundColor Green
    Start-Sleep -Milliseconds 400
}
function Compile-One($svc) {
    $mvnw = Join-Path $ProjectRoot "$($svc.Name)\mvnw.cmd"
    & $mvnw -f (Join-Path $ProjectRoot "$($svc.Name)\pom.xml") -DskipTests -q compile
    return ($LASTEXITCODE -eq 0)
}

# ---- 1) Start services (smart: show status, accept modifiers) --------------
function Action-StartServices {
    $db = Get-ActiveDb
    Write-Host "`n  Services — Active DB: $db" -ForegroundColor Cyan

    # Show current status
    $running = @(Get-RunningProcs)
    $runningMap = @{}
    foreach ($r in $running) { if ($r.Service) { $runningMap[$r.Service] = $r } }

    Write-Host ""
    for ($i = 0; $i -lt $Services.Count; $i++) {
        $s = $Services[$i]
        $r = $runningMap[$s.Name]
        if ($r) { Write-Host "    [$($i+1)] $($s.Name) :$($s.Port) — RUNNING (PID $($r.PID))" -ForegroundColor Green }
        else     { Write-Host "    [$($i+1)] $($s.Name) :$($s.Port) — stopped" -ForegroundColor DarkGray }
    }
    Write-Host "    [A] Start all stopped"
    if ($running.Count) { Write-Host "    [A]r Restart ALL" ; Write-Host "    [A]s Stop ALL" }
    Write-Host "    [Enter] Back"
    Write-Host ""
    Write-Host "  number = start  |  number+r = restart  |  number+s = stop" -ForegroundColor DarkGray
    $ans = Read-Host "  Choose"

    if (-not $ans) { return }

    # Parse input
    $action = 'start'
    $target = $null
    if ($ans -eq 'A' -or $ans -eq 'a') { $target = 'all'; $action = 'start' }
    elseif ($ans -match '^(?i)Ar$')     { $target = 'all'; $action = 'restart' }
    elseif ($ans -match '^(?i)As$')     { $target = 'all'; $action = 'stop' }
    elseif ($ans -match '^(\d+)([rs])?$') {
        $idx = [int]$Matches[1] - 1
        if ($idx -lt 0 -or $idx -ge $Services.Count) { Write-Host "  Invalid number." -ForegroundColor Yellow; Press-Enter; return }
        $target = $Services[$idx]
        if ($Matches[2] -eq 'r') { $action = 'restart' }
        elseif ($Matches[2] -eq 's') { $action = 'stop' }
        else { $action = 'start' }
    }
    else { Write-Host "  ?" -ForegroundColor Yellow; Press-Enter; return }

    # "Stop" path — no compile, no pre-flight, just kill
    if ($action -eq 'stop') {
        $toStop = if ($target -eq 'all') { @($running) } else { @($runningMap[$target.Name]) | Where-Object { $_ } }
        if (-not $toStop.Count) { Write-Host "  Nothing to stop." -ForegroundColor Yellow; Press-Enter; return }
        foreach ($p in $toStop) {
            try { Stop-Process -Id $p.PID -Force -ErrorAction Stop; Write-Host "  Stopped $($p.Service) (PID $($p.PID))" -ForegroundColor Green }
            catch { Write-Host "  Failed: PID $($p.PID) — $_" -ForegroundColor Red }
        }
        Press-Enter; return
    }

    # Start / restart — do pre-flight + compile first
    if (-not (Ensure-PgAuth)) { Press-Enter; return }

    # Pre-flight (only if we're starting something new, not restarting already-running)
    $needsStart = if ($target -eq 'all') { $Services | Where-Object { -not $runningMap[$_.Name] } } else { @($target) }
    $needsRestart = if ($target -eq 'all' -and $action -eq 'restart') { $Services } elseif ($action -eq 'restart') { @($target) } else { @() }
    if ($needsStart.Count -or $needsRestart.Count) {
        if (-not (Invoke-PreFlight $db)) {
            $ans2 = Read-Host "`n  Pre-flight found issues. Continue anyway? (y/n)"
            if ($ans2 -ne 'y') { return }
        }
    }

    # Compile
    $toCompile = @()
    if ($needsStart.Count) { $toCompile += $needsStart }
    if ($needsRestart.Count) { $toCompile += $needsRestart }
    $toCompile = @($toCompile | Sort-Object Name -Unique)
    if ($toCompile.Count) {
        $skip = Read-Host "  Compile before launch? (y/n, default y)"
        if ($skip -ne 'n') {
            foreach ($s in $toCompile) {
                Write-Host "  Compiling $($s.Name)..."
                if (-not (Compile-One $s)) { Write-Host "  FAILED: $($s.Name) did not compile." -ForegroundColor Red; Press-Enter; return }
            }
            Write-Host "  Compiled." -ForegroundColor Green
        }
    }

    # Secret
    $env:SCHOOLHUB_DB_NAME = $db
    if (Test-Path $SecretsFile) {
        Get-Content $SecretsFile | ForEach-Object { if ($_ -match '^([A-Z_]+)=(.+)$') { Set-Item -Path "env:$($Matches[1])" -Value $Matches[2] } }
    } else {
        $env:SCHOOLHUB_JWT_SECRET = New-Secret
        "SCHOOLHUB_JWT_SECRET=$env:SCHOOLHUB_JWT_SECRET" | Set-Content -LiteralPath $SecretsFile -NoNewline
        Write-Host "  Generated new JWT secret → .schoolhub_secrets.txt" -ForegroundColor Green
    }

    # Restart: stop first, then start
    if ($action -eq 'restart') {
        $toKill = if ($target -eq 'all') { @($running) } else { @($runningMap[$target.Name]) | Where-Object { $_ } }
        foreach ($p in $toKill) {
            try { Stop-Process -Id $p.PID -Force -ErrorAction Stop; Write-Host "  Stopped $($p.Service) (PID $($p.PID))" -ForegroundColor Green }
            catch { Write-Host "  Failed to stop PID $($p.PID) — $_" -ForegroundColor Red }
        }
        Start-Sleep -Seconds 1
    }

    # Launch
    $toLaunch = if ($target -eq 'all') { $Services } else { @($target) }
    $launched = 0
    foreach ($s in $toLaunch) {
        if ($runningMap[$s.Name] -and $action -ne 'restart') {
            Write-Host "    $($s.Name) — already running, skipped" -ForegroundColor Yellow
            continue
        }
        Launch-One $s
        $launched++
    }
    if (-not $launched) { Write-Host "  Nothing to launch." -ForegroundColor Yellow; Press-Enter; return }

    # Health checks (only for what we launched)
    foreach ($s in $Services) {
        if ($s.Health -and ($toLaunch | Where-Object { $_.Name -eq $s.Name })) {
            Write-Host "  Waiting for $($s.Name) ($($s.Port))..." -ForegroundColor Cyan
            if (-not (Wait-Url "http://localhost:$($s.Port)$($s.Health)" 150)) {
                Write-Host "  WARNING: $($s.Name) did not respond in time." -ForegroundColor Yellow
            }
        }
    }

    # Seed
    if ($toLaunch | Where-Object { $_.Name -eq 'ApiGateway' }) {
        $seed = Read-Host "`n  Seed demo data? (y/n, default n)"
        if ($seed -eq 'y') {
            if (Wait-Url "$GatewayUrl/health/auth" 60) { Seed-DemoSchool }
            else { Write-Host "  Gateway not reachable — cannot seed." -ForegroundColor Yellow }
        }
    }

    Press-Enter
}

# ---- 2) Stop services -----------------------------------------------------
function Action-StopServices {
    $procs = @(Get-RunningProcs)
    if (-not $procs.Count) { Write-Host "`n  No SchoolHub processes detected." -ForegroundColor Yellow; Press-Enter; return }
    Write-Host "`n  Running SchoolHub processes:" -ForegroundColor Cyan
    for ($i = 0; $i -lt $procs.Count; $i++) {
        Write-Host "    [$($i+1)] PID $($procs[$i].PID)  $($procs[$i].Service)  :$($procs[$i].Port)"
    }
    Write-Host "    [A] Kill ALL"
    Write-Host "    [Enter] Cancel"
    $ans = Read-Host "`n  Choose"
    if (-not $ans) { return }
    if ($ans -eq 'A' -or $ans -eq 'a') {
        $killed = 0
        foreach ($p in $procs) {
            try { Stop-Process -Id $p.PID -Force -ErrorAction Stop; $killed++; Write-Host "  Killed $($p.Service) (PID $($p.PID))" -ForegroundColor Green }
            catch { Write-Host "  Failed: PID $($p.PID) — $_" -ForegroundColor Red }
        }
        Write-Host "  Killed $killed / $($procs.Count)." -ForegroundColor Green
    }
    elseif ($ans -match '^\d+$') {
        $idx = [int]$ans - 1
        if ($idx -ge 0 -and $idx -lt $procs.Count) {
            $p = $procs[$idx]
            try { Stop-Process -Id $p.PID -Force -ErrorAction Stop; Write-Host "  Killed $($p.Service) (PID $($p.PID))" -ForegroundColor Green }
            catch { Write-Host "  Failed: $_" -ForegroundColor Red }
        } else { Write-Host "  Invalid number." -ForegroundColor Yellow }
    }
    else { Write-Host "  ?" -ForegroundColor Yellow }
    if (Test-Path $PidsFile) { Remove-Item $PidsFile -ErrorAction SilentlyContinue }
    Press-Enter
}

# ---- 3) Switch database ---------------------------------------------------
function Action-SwitchDb {
    Write-Host ""; $chosen = Pick-Database "Select a number (or Enter to cancel)"
    if (-not $chosen) { return }
    Set-ActiveDb $chosen
    Write-Host "Active database: $chosen" -ForegroundColor Green
    if (Check-Schema $chosen) { Write-Host "  Schema: applied" -ForegroundColor Green }
    else { Write-Host "  Schema: NOT applied — run option 7 or apply db/00_platform.sql manually" -ForegroundColor Yellow }
    Press-Enter
}

# ---- 4) Database tools (sub-menu) -----------------------------------------
function Action-DbTools {
    while ($true) {
        Write-Host "`n  Database Tools — Active DB: $(Get-ActiveDb)" -ForegroundColor Cyan
        Write-Host "    1) Create database"
        Write-Host "    2) Delete database"
        Write-Host "    3) Rename database"
        Write-Host "    b) Back"
        $ch = Read-Host "  Choose"
        switch ($ch) {
            '1' { Action-CreateDb }
            '2' { Action-DeleteDb }
            '3' { Action-RenameDb }
            'b' { return }
            default { Write-Host "  ?" -ForegroundColor Yellow }
        }
    }
}
function Action-CreateDb {
    if (-not (Ensure-PgAuth)) { Press-Enter; return }
    $name = (Read-Host "  New database name").Trim()
    if (-not $name) { Write-Host "  No name given." -ForegroundColor Yellow; return }
    if ((Get-Databases) -contains $name) { Write-Host "  '$name' already exists." -ForegroundColor Yellow; return }
    & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'postgres' -c "CREATE DATABASE `"$name`";"
    if ($LASTEXITCODE -ne 0) { Write-Host "  Create failed." -ForegroundColor Red; return }
    Write-Host "  Created '$name'." -ForegroundColor Green
    if ((Read-Host "  Apply platform schema now? (y/n)") -eq 'y') {
        & $Psql -U $DbUser -h $DbHost -p $DbPort -d $name -v ON_ERROR_STOP=1 -f $PlatformSql
    }
    if ((Read-Host "  Make '$name' the active database? (y/n)") -eq 'y') { Set-ActiveDb $name }
}
function Action-DeleteDb {
    if (-not (Ensure-PgAuth)) { Press-Enter; return }
    $chosen = Pick-Database "Select a number to DELETE (or Enter to cancel)"
    if (-not $chosen) { return }
    if ((Read-Host "  Type '$chosen' to confirm deletion") -ne $chosen) { Write-Host "  Cancelled." -ForegroundColor Yellow; return }
    Disconnect-Db $chosen
    & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'postgres' -c "DROP DATABASE `"$chosen`";"
    if ($LASTEXITCODE -ne 0) { Write-Host "  Delete failed." -ForegroundColor Red; return }
    Write-Host "  Deleted '$chosen'." -ForegroundColor Green
    if ((Get-ActiveDb) -eq $chosen) { Write-Host "  That was the active DB — switch to another (option 3)." -ForegroundColor Yellow }
}
function Action-RenameDb {
    if (-not (Ensure-PgAuth)) { Press-Enter; return }
    $chosen = Pick-Database "Select a number to RENAME (or Enter to cancel)"
    if (-not $chosen) { return }
    if ((Read-Host "  Type '$chosen' to confirm") -ne $chosen) { Write-Host "  Cancelled." -ForegroundColor Yellow; return }
    $newName = (Read-Host "  New name").Trim()
    if (-not $newName) { Write-Host "  No name given." -ForegroundColor Yellow; return }
    if ((Get-Databases) -contains $newName) { Write-Host "  '$newName' already exists." -ForegroundColor Yellow; return }
    Disconnect-Db $chosen
    & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'postgres' -c "ALTER DATABASE `"$chosen`" RENAME TO `"$newName`";"
    if ($LASTEXITCODE -ne 0) { Write-Host "  Rename failed." -ForegroundColor Red; return }
    Write-Host "  Renamed '$chosen' → '$newName'." -ForegroundColor Green
    if ((Get-ActiveDb) -eq $chosen) { Set-ActiveDb $newName; Write-Host "  Active DB updated." -ForegroundColor Green }
}

# ---- 5) Super-admins (sub-menu) -------------------------------------------
function Action-SuperAdmins {
    while ($true) {
        $db = Get-ActiveDb
        Write-Host "`n  Super-Admins — Active DB: $db" -ForegroundColor Cyan
        Write-Host "    1) List super-admins"
        Write-Host "    2) Create super-admin"
        Write-Host "    3) Delete super-admin"
        Write-Host "    b) Back"
        $ch = Read-Host "  Choose"
        switch ($ch) {
            '1' { Action-ListSuperAdmins }
            '2' { Action-CreateSuperAdmin }
            '3' { Action-DeleteSuperAdmin }
            'b' { return }
            default { Write-Host "  ?" -ForegroundColor Yellow }
        }
    }
}
function Action-ListSuperAdmins {
    $db = Get-ActiveDb
    if (-not (Ensure-PgAuth)) { Press-Enter; return }
    if (-not (Check-DbExists $db)) { Write-Host "  Database '$db' does not exist." -ForegroundColor Yellow; Press-Enter; return }
    if (-not (Check-Schema $db)) { Write-Host "  Schema not applied to '$db'." -ForegroundColor Yellow; Press-Enter; return }
    $admins = Check-SuperAdmins $db
    if (-not $admins.Count) { Write-Host "  No super-admins in '$db'." -ForegroundColor Yellow }
    else { $admins | ForEach-Object { Write-Host "  $($_.Name)  <$($_.Email)>" } }
    Press-Enter
}
function Action-CreateSuperAdmin {
    $db = Get-ActiveDb
    if (-not (Ensure-PgAuth)) { Press-Enter; return }
    if (-not (Check-DbExists $db)) { Write-Host "  Database '$db' does not exist." -ForegroundColor Yellow; Press-Enter; return }
    if (-not (Check-Schema $db)) { Write-Host "  Schema not applied to '$db' — run option 7 first." -ForegroundColor Yellow; Press-Enter; return }
    Ensure-Pgcrypto $db
    $email = (Read-Host "  Email").Trim()
    if (-not $email) { Write-Host "  Email required." -ForegroundColor Yellow; return }
    $eEsc = $email.Replace("'", "''")
    $exists = (& $Psql -U $DbUser -h $DbHost -p $DbPort -d $db -t -A -c "SELECT count(*) FROM platform.app_user WHERE email='$eEsc';").Trim()
    if ($exists -ne '0') { Write-Host "  '$email' is already registered." -ForegroundColor Yellow; return }
    $first = (Read-Host "  First name").Trim()
    $last  = (Read-Host "  Last name").Trim()
    if (-not $first -or -not $last) { Write-Host "  First and last name required." -ForegroundColor Yellow; return }
    $visible = (Read-Host "  Show password as you type? (y/n)") -eq 'y'
    $pw1 = Read-PlainPassword "  Password (min 8)" $visible
    if ($pw1.Length -lt 8) { Write-Host "  Too short." -ForegroundColor Yellow; return }
    $pw2 = Read-PlainPassword "  Confirm password" $visible
    if ($pw1 -ne $pw2) { Write-Host "  Passwords do not match." -ForegroundColor Yellow; return }
    $f = $first.Replace("'", "''"); $l = $last.Replace("'", "''"); $p = $pw1.Replace("'", "''")
    & $Psql -U $DbUser -h $DbHost -p $DbPort -d $db -v ON_ERROR_STOP=1 -c @"
INSERT INTO platform.app_user (email, password_hash, first_name, last_name, role_id, account_status)
VALUES ('$eEsc', crypt('$p', gen_salt('bf', 10)), '$f', '$l',
        (SELECT id FROM platform.role WHERE name='PLATFORM_OWNER'), 'active');
"@
    if ($LASTEXITCODE -ne 0) { Write-Host "  Create failed." -ForegroundColor Red; return }
    Write-Host "  Created '$email' in '$db'." -ForegroundColor Green
    Press-Enter
}
function Action-DeleteSuperAdmin {
    $db = Get-ActiveDb
    if (-not (Ensure-PgAuth)) { Press-Enter; return }
    Ensure-Pgcrypto $db
    $admins = Check-SuperAdmins $db
    if (-not $admins.Count) { Write-Host "  No super-admins in '$db'." -ForegroundColor Yellow; Press-Enter; return }
    if ($admins.Count -eq 1) { Write-Host "  '$($admins[0].Email)' is the ONLY super-admin — refusing to delete the last one." -ForegroundColor Red; Press-Enter; return }
    Write-Host ""
    for ($i = 0; $i -lt $admins.Count; $i++) { Write-Host "  [$($i+1)] $($admins[$i].Name)  <$($admins[$i].Email)>" }
    $pick = Read-Host "  Select a number to DELETE (or Enter to cancel)"
    if (-not ($pick -match '^\d+$' -and [int]$pick -ge 1 -and [int]$pick -le $admins.Count)) { Write-Host "  Cancelled." -ForegroundColor Yellow; return }
    $target = $admins[[int]$pick - 1]
    $visible = (Read-Host "  Show password as you type? (y/n)") -eq 'y'
    $pw = Read-PlainPassword "  Enter password for '$($target.Email)' to confirm deletion" $visible
    $pEsc = $pw.Replace("'", "''")
    $ok = (& $Psql -U $DbUser -h $DbHost -p $DbPort -d $db -t -A -c "SELECT (password_hash = crypt('$pEsc', password_hash)) FROM platform.app_user WHERE email='$($target.Email.Replace("'","''"))';").Trim()
    if ($ok -ne 't') { Write-Host "  Wrong password — cancelled." -ForegroundColor Red; Press-Enter; return }
    & $Psql -U $DbUser -h $DbHost -p $DbPort -d $db -c "DELETE FROM platform.app_user WHERE email='$($target.Email.Replace("'","''"))';"
    if ($LASTEXITCODE -ne 0) { Write-Host "  Delete failed." -ForegroundColor Red; return }
    Write-Host "  Deleted '$($target.Email)'." -ForegroundColor Green
    Press-Enter
}

# ---- 6) Debug (sub-menu) --------------------------------------------------
function Action-Debug {
    while ($true) {
        $db = Get-ActiveDb
        Write-Host "`n  Debug — Active DB: $db" -ForegroundColor Cyan
        Write-Host "    1) Full diagnostic scan"
        Write-Host "    2) Postgres connection test"
        Write-Host "    3) DB exists + schema check"
        Write-Host "    4) JWT secret check"
        Write-Host "    5) Port conflict scan"
        Write-Host "    6) Java / Maven check"
        Write-Host "    7) Regenerate JWT secret"
        Write-Host "    8) Kill stale services (force)"
        Write-Host "    b) Back"
        $ch = Read-Host "  Choose"
        switch ($ch) {
            '1' { Action-FullDiagnostic; Press-Enter }
            '2' { Action-CheckPostgres; Press-Enter }
            '3' { Action-CheckDbSchema; Press-Enter }
            '4' { Action-CheckJwtSecret; Press-Enter }
            '5' { Action-CheckPorts; Press-Enter }
            '6' { Action-CheckJavaMaven; Press-Enter }
            '7' { Action-RegenerateSecret }
            '8' { Action-KillStale }
            'b' { return }
            default { Write-Host "  ?" -ForegroundColor Yellow }
        }
    }
}
function P($label, $cond, $failMsg) {
    if ($cond) { Write-Host "    [PASS] $label" -ForegroundColor Green }
    else       { Write-Host "    [FAIL] $label — $failMsg" -ForegroundColor Red }
}
function Action-FullDiagnostic {
    $db = Get-ActiveDb
    Write-Host "`n  Full Diagnostic — Target DB: $db" -ForegroundColor Cyan
    if (-not (Ensure-PgAuth)) { Press-Enter; return }
    Write-Host ""
    P 'Postgres reachable' (Check-Pg) 'Check PostgreSQL service, port, firewall.'
    P "Database '$db' exists" (Check-DbExists $db) "Create it (option 4) or switch (option 3)."
    P 'Schema applied' (Check-Schema $db) "Run option 7 or apply db/00_platform.sql."
    $s = Check-Secret
    $secretOk = $s -eq 'ok'
    P 'JWT secret valid' $secretOk "Issue: $s. Use option 6→7 to regenerate."
    $c = Check-Ports
    $portsOk = -not $c.Count
    P 'Ports 9000-9003 free' $portsOk "Conflicts: $($c -join ', ')"
    $j = Check-Java; P 'Java on PATH' ([bool]$j) "Install Java 21."
    if ($j) { Write-Host "    [INFO] $j" -ForegroundColor DarkGray }
    P 'Maven wrapper present' (Check-Maven) 'Project may be corrupted.'
    $admins = Check-SuperAdmins $db
    if ($admins.Count) {
        Write-Host "    [INFO] $($admins.Count) super-admin(s):" -ForegroundColor DarkGray
        $admins | ForEach-Object { Write-Host "           $($_.Email)" -ForegroundColor DarkGray }
    } else { Write-Host "    [WARN] No super-admins — run option 5→2 to create one." -ForegroundColor Yellow }
    $procs = Get-RunningProcs
    if ($procs.Count) {
        Write-Host "    [INFO] Running services:" -ForegroundColor DarkGray
        $procs | ForEach-Object { Write-Host "           PID $($_.PID)  $($_.Service)  :$($_.Port)" -ForegroundColor DarkGray }
    } else { Write-Host "    [INFO] No services running." -ForegroundColor DarkGray }
}
function Action-CheckPostgres {
    Write-Host ""; if (-not (Ensure-PgAuth)) { Press-Enter; return }
    Write-Host "  Postgres: REACHABLE (localhost:5432)" -ForegroundColor Green
    Press-Enter
}
function Action-CheckDbSchema {
    $db = Get-ActiveDb; Write-Host ""
    if (-not (Ensure-PgAuth)) { Press-Enter; return }
    if (Check-DbExists $db) {
        Write-Host "  Database '$db': EXISTS" -ForegroundColor Green
        if (Check-Schema $db) { Write-Host "  Schema: APPLIED (platform.app_user present)" -ForegroundColor Green }
        else { Write-Host "  Schema: MISSING" -ForegroundColor Red }
    } else { Write-Host "  Database '$db': NOT FOUND" -ForegroundColor Red }
    Press-Enter
}
function Action-CheckJwtSecret {
    Write-Host ""
    $s = Check-Secret
    switch ($s) {
        'ok'          { Write-Host "  JWT secret: VALID (file exists, 32+ bytes, valid base64, env matches file)" -ForegroundColor Green }
        'missing'     { Write-Host "  JWT secret: MISSING — .schoolhub_secrets.txt not found" -ForegroundColor Red }
        'empty'       { Write-Host "  JWT secret: EMPTY — file exists but has no content" -ForegroundColor Red }
        'tooshort'    { Write-Host "  JWT secret: TOO SHORT — must be at least 32 bytes of base64" -ForegroundColor Red }
        'badbase64'   { Write-Host "  JWT secret: CORRUPTED — not valid base64" -ForegroundColor Red }
        'envmismatch' { Write-Host "  JWT secret: MISMATCH — env var differs from file" -ForegroundColor Red }
    }
    if (Test-Path $SecretsFile) {
        $raw = Get-Content -Raw -LiteralPath $SecretsFile
        Write-Host "  File: $SecretsFile ($($raw.Trim().Length) bytes)" -ForegroundColor DarkGray
    }
    Press-Enter
}
function Action-CheckPorts {
    Write-Host ""
    $c = Check-Ports
    if ($c.Count) { $c | ForEach-Object { Write-Host "  [IN USE] $_" -ForegroundColor Red } }
    else { Write-Host "  Ports 9000-9003: all free" -ForegroundColor Green }
    Press-Enter
}
function Action-CheckJavaMaven {
    Write-Host ""
    $j = Check-Java; if ($j) { Write-Host "  $j" -ForegroundColor Green } else { Write-Host "  Java: NOT FOUND on PATH" -ForegroundColor Red }
    if (Check-Maven) { Write-Host "  Maven wrapper: FOUND" -ForegroundColor Green } else { Write-Host "  Maven wrapper: MISSING" -ForegroundColor Red }
    Press-Enter
}
function Action-RegenerateSecret {
    Write-Host ""
    if (Test-Path $SecretsFile) {
        Write-Host "  WARNING: Regenerating the JWT secret invalidates ALL existing tokens." -ForegroundColor Yellow
        Write-Host "  All users will be forced to log in again." -ForegroundColor Yellow
        if ((Read-Host "  Type 'yes' to confirm") -ne 'yes') { Write-Host "  Cancelled."; return }
    }
    $env:SCHOOLHUB_JWT_SECRET = New-Secret
    "SCHOOLHUB_JWT_SECRET=$env:SCHOOLHUB_JWT_SECRET" | Set-Content -LiteralPath $SecretsFile -NoNewline
    Write-Host "  New JWT secret generated ($($env:SCHOOLHUB_JWT_SECRET.Length) bytes)." -ForegroundColor Green
    Write-Host "  Restart services (option 1) to pick it up." -ForegroundColor Yellow
    Press-Enter
}
function Action-KillStale {
    $procs = @(Get-RunningProcs)
    if (-not $procs.Count) { Write-Host "`n  No SchoolHub processes detected." -ForegroundColor Yellow; Press-Enter; return }
    Write-Host "`n  Stale processes:" -ForegroundColor Cyan
    for ($i = 0; $i -lt $procs.Count; $i++) {
        Write-Host "    [$($i+1)] PID $($procs[$i].PID)  $($procs[$i].Service)  :$($procs[$i].Port)"
    }
    Write-Host "    [A] Force-kill ALL"
    Write-Host "    [Enter] Cancel"
    $ans = Read-Host "`n  Choose"
    if (-not $ans) { return }
    if ($ans -eq 'A' -or $ans -eq 'a') {
        $killed = 0
        foreach ($p in $procs) {
            try { Stop-Process -Id $p.PID -Force -ErrorAction Stop; $killed++; Write-Host "  Killed $($p.Service) (PID $($p.PID))" -ForegroundColor Green }
            catch { Write-Host "  Failed: PID $($p.PID)" -ForegroundColor Red }
        }
        Write-Host "  Killed $killed / $($procs.Count)." -ForegroundColor Green
    }
    elseif ($ans -match '^\d+$') {
        $idx = [int]$ans - 1
        if ($idx -ge 0 -and $idx -lt $procs.Count) {
            $p = $procs[$idx]
            try { Stop-Process -Id $p.PID -Force -ErrorAction Stop; Write-Host "  Killed $($p.Service) (PID $($p.PID))" -ForegroundColor Green }
            catch { Write-Host "  Failed: $_" -ForegroundColor Red }
        } else { Write-Host "  Invalid number." -ForegroundColor Yellow }
    }
    else { Write-Host "  ?" -ForegroundColor Yellow }
    Press-Enter
}

# ---- 7) New laptop setup --------------------------------------------------
function Action-NewLaptopSetup {
    Write-Host "`n  New Laptop Setup" -ForegroundColor Cyan
    Write-Host "  This walks through the full first-run pipeline with checkpoints.`n"

    # Step 1: Find Postgres
    Write-Host "  Step 1/6 — Find PostgreSQL..." -ForegroundColor Cyan
    $p = Find-Psql
    if (-not $p) {
        Write-Host "    PostgreSQL not found. Install it, then re-run." -ForegroundColor Red
        Start-Process 'https://www.postgresql.org/download/windows/'
        Press-Enter; return
    }
    Write-Host "    [OK] $p" -ForegroundColor Green

    # Step 2: Connect
    Write-Host "  Step 2/6 — Connect to PostgreSQL..." -ForegroundColor Cyan
    if (-not (Ensure-PgAuth)) { Write-Host "    Cannot connect. Check password / service." -ForegroundColor Red; Press-Enter; return }
    Write-Host "    [OK] localhost:5432" -ForegroundColor Green

    # Step 3: Create database
    Write-Host "  Step 3/6 — Database..." -ForegroundColor Cyan
    $db = Read-Host "    Database name (default: schoolhub)"
    if (-not $db) { $db = 'schoolhub' }
    if (Check-DbExists $db) {
        Write-Host "    [EXISTS] '$db' already exists." -ForegroundColor Yellow
    } else {
        & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'postgres' -c "CREATE DATABASE `"$db`";"
        if ($LASTEXITCODE -ne 0) { Write-Host "    Create failed." -ForegroundColor Red; Press-Enter; return }
        Write-Host "    [CREATED] '$db'" -ForegroundColor Green
    }

    # Step 4: Apply schema
    Write-Host "  Step 4/6 — Apply schema..." -ForegroundColor Cyan
    if (-not (Test-Path $PlatformSql)) { Write-Host "    Missing: $PlatformSql" -ForegroundColor Red; Press-Enter; return }
    if (Check-Schema $db) {
        Write-Host "    [EXISTS] Schema already applied." -ForegroundColor Yellow
    } else {
        & $Psql -U $DbUser -h $DbHost -p $DbPort -d $db -v ON_ERROR_STOP=1 -f $PlatformSql
        if ($LASTEXITCODE -ne 0) { Write-Host "    Schema apply failed." -ForegroundColor Red; Press-Enter; return }
        Write-Host "    [OK] Platform schema applied." -ForegroundColor Green
    }

    # Step 5: JWT secret
    Write-Host "  Step 5/6 — JWT secret..." -ForegroundColor Cyan
    if ((Check-Secret) -eq 'ok') {
        Write-Host "    [EXISTS] Valid secret found." -ForegroundColor Yellow
    } else {
        $env:SCHOOLHUB_JWT_SECRET = New-Secret
        "SCHOOLHUB_JWT_SECRET=$env:SCHOOLHUB_JWT_SECRET" | Set-Content -LiteralPath $SecretsFile -NoNewline
        Write-Host "    [GENERATED] .schoolhub_secrets.txt ($($env:SCHOOLHUB_JWT_SECRET.Length) bytes)" -ForegroundColor Green
    }

    # Step 6: Bootstrap owner
    Write-Host "  Step 6/6 — Bootstrap platform owner..." -ForegroundColor Cyan
    $admins = Check-SuperAdmins $db
    if ($admins.Count) {
        Write-Host "    [EXISTS] $($admins.Count) super-admin(s) already present:" -ForegroundColor Yellow
        $admins | ForEach-Object { Write-Host "      $($_.Email)" -ForegroundColor Yellow }
    } else {
        $email = Read-Host "    Owner email (default: owner@schoolhub.local)"
        if (-not $email) { $email = 'owner@schoolhub.local' }
        $pw = Read-Host "    Owner password (default: Owner123!)"
        if (-not $pw) { $pw = 'Owner123!' }
        $first = 'Platform'; $last = 'Owner'
        Ensure-Pgcrypto $db
        $eEsc = $email.Replace("'", "''"); $pEsc = $pw.Replace("'", "''")
        & $Psql -U $DbUser -h $DbHost -p $DbPort -d $db -v ON_ERROR_STOP=1 -c @"
INSERT INTO platform.app_user (email, password_hash, first_name, last_name, role_id, account_status)
VALUES ('$eEsc', crypt('$pEsc', gen_salt('bf', 10)), '$first', '$last',
        (SELECT id FROM platform.role WHERE name='PLATFORM_OWNER'), 'active');
"@
        if ($LASTEXITCODE -ne 0) { Write-Host "    Bootstrap failed." -ForegroundColor Red; Press-Enter; return }
        Write-Host "    [CREATED] $email" -ForegroundColor Green
    }

    # Set active DB
    Set-ActiveDb $db
    $env:SCHOOLHUB_DB_NAME = $db

    # Also create "schoolhub" as the IDE fallback database
    if (-not (Check-DbExists 'schoolhub')) {
        if ((Read-Host "`n  Also create 'schoolhub' as the IDE fallback database? (y/n, default y)") -ne 'n') {
            & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'postgres' -c "CREATE DATABASE schoolhub;"
            & $Psql -U $DbUser -h $DbHost -p $DbPort -d 'schoolhub' -v ON_ERROR_STOP=1 -f $PlatformSql
            Write-Host "    [OK] 'schoolhub' created — IDE runs will work." -ForegroundColor Green
        }
    }

    Write-Host "`n  Setup complete — Active DB: $db" -ForegroundColor Green

    # Offer seed + compile
    if ((Read-Host "`n  Seed demo data? (requires services running) (y/n)") -eq 'y') {
        if (Wait-Url "$GatewayUrl/health/auth" 10) { Seed-DemoSchool }
        else { Write-Host "  Services not running — seed skipped. Run option 1 first, then seed via option 1's prompt." -ForegroundColor Yellow }
    }
    if ((Read-Host "  Compile & launch now? (y/n)") -eq 'y') {
        if (Invoke-PreFlight $db) {
            if (Compile-All) { Launch-All }
        }
    }
    Press-Enter
}

# ===========================================================================
# MAIN MENU
# ===========================================================================
$env:SCHOOLHUB_DB_NAME = Get-ActiveDb  # sync env var with active-db.properties
while ($true) {
    Clear-Host
    Write-Host "==============================================" -ForegroundColor Cyan
    Write-Host " SchoolHub Manager" -ForegroundColor Cyan
    Write-Host " Active DB: $(Get-ActiveDb)" -ForegroundColor DarkGray
    Write-Host "==============================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  1) Start / Stop / Restart"
    Write-Host "  2) Stop services"
    Write-Host "  3) Switch database"
    Write-Host "  4) Database tools"
    Write-Host "  5) Super-admins"
    Write-Host "  6) Debug"
    Write-Host "  7) New laptop setup"
    Write-Host ""
    Write-Host "  q) Quit"
    Write-Host ""
    $ch = Read-Host "Choose"
    try {
        switch ($ch) {
            '1' { Action-StartServices }
            '2' { Action-StopServices }
            '3' { Action-SwitchDb }
            '4' { Action-DbTools }
            '5' { Action-SuperAdmins }
            '6' { Action-Debug }
            '7' { Action-NewLaptopSetup }
            'q' { Write-Host "Bye." -ForegroundColor Cyan; exit 0 }
            default { Write-Host "?" -ForegroundColor Yellow; Start-Sleep -Milliseconds 600 }
        }
    } catch {
        Write-Host "`n  ERROR: $_" -ForegroundColor Red
        Write-Host "  The menu recovered. You can continue or choose Debug (6) to diagnose." -ForegroundColor Yellow
        Press-Enter
    }
}
