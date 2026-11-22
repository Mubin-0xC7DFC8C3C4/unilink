# UniLink — stop the dev servers started by start-dev.ps1 (Windows 11).
# Stops processes listening on the backend (8080) and frontend (5173) ports.
foreach ($port in 8080, 5173) {
  $owners = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique
  foreach ($pidToStop in $owners) {
    Write-Host "Stopping process $pidToStop on port $port"
    Stop-Process -Id $pidToStop -Force -ErrorAction SilentlyContinue
  }
}
Write-Host 'Dev servers stopped.'
