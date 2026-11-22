# UniLink — one-time local setup (Windows 11).
# Checks prerequisites and installs frontend dependencies.
# Database creation is a manual one-off: see README.md section 8.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Write-Host 'Checking prerequisites...'
foreach ($cmd in 'java', 'node', 'npm', 'mysql') {
  if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
    throw "Missing prerequisite: $cmd (see README.md section 6)"
  }
}
java -version
node --version

Write-Host 'Installing frontend dependencies...'
Push-Location (Join-Path $root 'frontend')
npm install
Pop-Location

Write-Host ''
Write-Host 'Setup complete. Next:'
Write-Host '  1. Create the database and users (README.md section 8).'
Write-Host '  2. Set the environment variables from backend\.env.example'
Write-Host '     (DB passwords, ENCRYPTION_KEY, INITIAL_ADMIN_*).'
Write-Host '  3. Run scripts\start-dev.ps1'
