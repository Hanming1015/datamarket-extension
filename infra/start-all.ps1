# Start all 9 Synapse backend services from their built jars.
# Uses JAVA_HOME\bin\java.exe on purpose: the PATH `java` is a broken Oracle javapath stub.
# Logs -> infra/logs/<service>.log ; each service runs as a detached process.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot          # repo root
$svcDir = Join-Path $root 'services'
$logDir = Join-Path $PSScriptRoot 'logs'
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path $java)) { throw "java.exe not found at $java (check JAVA_HOME)" }

# order: gateway first is fine (it just won't route until services register), but we start
# infra-dependent leaf services first, gateway last, so the whole set converges quickly.
$services = @(
  @{ name = 'synapse-auth-service';         port = 8081 },
  @{ name = 'synapse-consent-service';      port = 8082 },
  @{ name = 'synapse-dataset-service';      port = 8083 },
  @{ name = 'synapse-access-service';       port = 8084 },
  @{ name = 'synapse-payment-service';      port = 8085 },
  @{ name = 'synapse-billing-service';      port = 8086 },
  @{ name = 'synapse-audit-service';        port = 8087 },
  @{ name = 'synapse-notification-service'; port = 8088 },
  @{ name = 'synapse-gateway';              port = 8080 }
)

foreach ($s in $services) {
  $jar = Get-ChildItem (Join-Path $svcDir "$($s.name)\target\*.jar") |
    Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*.original' } |
    Select-Object -First 1
  if (-not $jar) { Write-Host "[SKIP] $($s.name): no jar" -ForegroundColor Yellow; continue }
  $log = Join-Path $logDir "$($s.name).log"
  # jar path contains a space ("datamarket - extension"); Start-Process joins -ArgumentList
  # with spaces and does NOT quote, so the jar arg must carry its own embedded quotes.
  Start-Process -FilePath $java `
    -ArgumentList '-jar', ('"{0}"' -f $jar.FullName) `
    -RedirectStandardOutput $log `
    -RedirectStandardError "$log.err" `
    -WindowStyle Hidden
  Write-Host ("[UP ] {0,-32} :{1}  -> {2}" -f $s.name, $s.port, $log) -ForegroundColor Green
  Start-Sleep -Milliseconds 800
}

Write-Host "`nAll launch commands issued. Services need ~20-40s to register with Nacos." -ForegroundColor Cyan
Write-Host "Run infra\health-check.ps1 to poll readiness." -ForegroundColor Cyan
