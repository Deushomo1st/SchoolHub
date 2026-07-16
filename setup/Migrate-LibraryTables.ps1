# ============================================================================
# SchoolHub — Migrate Library Tables to Existing Tenant Schemas
# ============================================================================
# Runs db/library_tables.sql against every existing tenant schema.
# Safe to run multiple times — all statements use IF NOT EXISTS.
#
# Usage: pwsh -File setup/Migrate-LibraryTables.ps1
# Or add to SchoolHub-Manager.ps1 menu.
# ============================================================================
#requires -Version 5.1
$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$RefFile     = Join-Path $ProjectRoot 'active-db.properties'
$LibrarySql  = Join-Path $ProjectRoot 'db\library_tables.sql'
$DbHost      = 'localhost'
$DbPort      = 5432
$DbUser      = 'postgres'

# ---- Find psql ---------------------------------------------------------------
function Find-Psql {
    foreach ($base in @('C:\Program Files\PostgreSQL', 'C:\Program Files (x86)\PostgreSQL')) {
        if (Test-Path $base) {
            $found = Get-ChildItem $base -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $p = Join-Path $_.FullName 'bin\psql.exe'; if (Test-Path $p) { $p }
            } | Select-Object -First 1
            if ($found) { return $found }
        }
    }
    $cmd = Get-Command psql.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd }
    return $null
}

$Psql = Find-Psql
if (-not $Psql) { Write-Host "PostgreSQL (psql.exe) not found." -ForegroundColor Red; exit 1 }

if (-not $env:PGPASSWORD) { $env:PGPASSWORD = $DbUser }

# ---- Get active database -----------------------------------------------------
function Get-ActiveDb {
    if (Test-Path $RefFile) {
        foreach ($line in Get-Content -LiteralPath $RefFile) {
            if ($line -match '^\s*schoolhub\.db\.name\s*=\s*(.+)$') { return $Matches[1].Trim() }
        }
    }
    if ($env:SCHOOLHUB_DB_NAME) { return $env:SCHOOLHUB_DB_NAME }
    return 'schoolhub'
}

$db = Get-ActiveDb
Write-Host "`nMigrating library tables to existing schemas in database: $db" -ForegroundColor Cyan

# ---- Get tenant schemas ------------------------------------------------------
$schemas = & $Psql -U $DbUser -h $DbHost -p $DbPort -d $db -t -A `
    -c "SELECT schema_name FROM platform.tenant WHERE status IN ('active','pending','suspended');" 2>$null

if (-not $schemas) {
    Write-Host "No tenant schemas found." -ForegroundColor Yellow
    exit 0
}

$schemaList = @($schemas | Where-Object { $_ })
Write-Host "Found $($schemaList.Count) tenant schema(s)" -ForegroundColor Green

if (-not (Test-Path $LibrarySql)) {
    Write-Host "Library SQL file not found: $LibrarySql" -ForegroundColor Red
    exit 1
}

# ---- Migrate each schema ----------------------------------------------------
$success = 0
$failed = 0

foreach ($schema in $schemaList) {
    Write-Host "  Migrating schema: $schema" -NoNewline
    try {
        $result = & $Psql -U $DbUser -h $DbHost -p $DbPort -d $db -v ON_ERROR_STOP=1 `
            -c "SET search_path TO $schema;" `
            -f $LibrarySql 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host " ✓" -ForegroundColor Green
            $success++
        } else {
            Write-Host " ✗ (exit $LASTEXITCODE)" -ForegroundColor Red
            Write-Host "    $result" -ForegroundColor DarkGray
            $failed++
        }
    } catch {
        Write-Host " ✗ ($($_.Exception.Message))" -ForegroundColor Red
        $failed++
    }
}

Write-Host "`nMigration complete: $success succeeded, $failed failed" -ForegroundColor Cyan
if ($failed -eq 0) {
    Write-Host "All tenant schemas now have library tables." -ForegroundColor Green
} else {
    Write-Host "Some schemas failed — check output above." -ForegroundColor Yellow
}
