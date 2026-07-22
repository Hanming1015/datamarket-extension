# Poll all 9 services until their ports accept TCP, then smoke-test the gateway with a login.
$ports = [ordered]@{
  'gateway'      = 8080; 'auth' = 8081; 'consent' = 8082; 'dataset' = 8083;
  'access'       = 8084; 'payment' = 8085; 'billing' = 8086; 'audit' = 8087;
  'notification' = 8088
}
$deadline = (Get-Date).AddSeconds(90)
function PortUp($p) {
  try { (New-Object Net.Sockets.TcpClient).ConnectAsync('127.0.0.1', $p).Wait(400); return (Test-NetConnection -ComputerName 127.0.0.1 -Port $p -WarningAction SilentlyContinue).TcpTestSucceeded }
  catch { return $false }
}
do {
  $down = @()
  foreach ($k in $ports.Keys) { if (-not (PortUp $ports[$k])) { $down += $k } }
  if ($down.Count -eq 0) { break }
  Write-Host ("waiting: {0}" -f ($down -join ', ')) -ForegroundColor DarkGray
  Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)

Write-Host "`n--- port status ---"
foreach ($k in $ports.Keys) {
  $ok = PortUp $ports[$k]
  Write-Host ("{0,-14} :{1}  {2}" -f $k, $ports[$k], $(if ($ok) { 'UP' } else { 'DOWN' })) -ForegroundColor $(if ($ok) { 'Green' } else { 'Red' })
}

Write-Host "`n--- gateway smoke test (login via 8080) ---"
try {
  $r = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/auth/login' `
    -Headers @{ 'Content-Type' = 'application/json' } -Body '{"username":"test","password":"123456"}'
  if ($r.code -eq 200 -and $r.data.token) { Write-Host "[PASS] login through gateway OK (token issued)" -ForegroundColor Green }
  else { Write-Host "[WARN] login returned code=$($r.code) msg=$($r.message)" -ForegroundColor Yellow }
} catch { Write-Host "[FAIL] gateway login error: $($_.Exception.Message)" -ForegroundColor Red }
