# UniLink — back up the local MySQL database with mysqldump (Windows 11).
# Uses DB_USER / DB_PASSWORD from the environment when set; otherwise the
# dev defaults from the README. Output: backups\unilink-YYYYMMDD-HHmm.sql
$root = Split-Path -Parent $PSScriptRoot
$backupDir = Join-Path $root 'backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

$dbUser = if ($env:DB_USER) { $env:DB_USER } else { 'unilink_app' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmm'
$out = Join-Path $backupDir "unilink-$stamp.sql"

if ($env:DB_PASSWORD) { $env:MYSQL_PWD = $env:DB_PASSWORD }
mysqldump -u $dbUser --single-transaction --routines unilink_dev > $out
if ($LASTEXITCODE -ne 0) { throw "mysqldump failed with exit code $LASTEXITCODE" }
Write-Host "Backup written to $out"
