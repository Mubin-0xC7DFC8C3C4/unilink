# UniLink — start the backend and frontend dev servers (Windows 11).
# Opens two PowerShell windows so each server's logs stay visible.
# Required environment variables must already be set (backend/.env.example).
$root = Split-Path -Parent $PSScriptRoot

Start-Process powershell -ArgumentList '-NoExit', '-Command',
  "cd '$root\backend'; .\mvnw.cmd spring-boot:run"
Start-Process powershell -ArgumentList '-NoExit', '-Command',
  "cd '$root\frontend'; npm run dev"

Write-Host 'Backend:  http://localhost:8080/api/health'
Write-Host 'Student:  http://localhost:5173'
Write-Host 'Admin:    http://localhost:5173/admin/login'
