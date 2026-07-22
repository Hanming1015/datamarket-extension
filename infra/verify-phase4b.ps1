# Phase 4b 验证:access-service 多副本 + 客户端负载均衡 + 故障摘除。
#   A) 起第 2 个 access 实例(8094,同 jar,--server.port 覆盖),两实例同名注册进 Nacos。
#   B) 经网关反复调 /api/access/whoami,端口交替 => LoadBalancer 轮询生效。
#   C) 杀掉 8094,等 Nacos 健康检查剔除 + LB 刷新,流量应全走存活实例且零错误。
# 结束后 8094 保持关闭(集群回到单 access 实例的稳态)。
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$pass = 0; $fail = 0
function Check($name, $cond, $detail) {
    if ($cond) { Write-Host "  [PASS] $name" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  [FAIL] $name -- $detail" -ForegroundColor Red; $script:fail++ }
}
function Token() {
    $r = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -Headers @{ 'Content-Type' = 'application/json' } -Body '{"username":"test","password":"123456"}'
    return $r.data.token
}

Write-Host "`n=== A. 起第 2 个 access 实例(8094)===" -ForegroundColor Cyan
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$jar = "C:\Users\19831\Desktop\datamarket - extension\services\synapse-access-service\target\synapse-access-service-0.0.1-SNAPSHOT.jar"
$logdir = "$PSScriptRoot\logs"
Start-Process -FilePath $java -ArgumentList '-jar', ('"{0}"' -f $jar), '--server.port=8094' `
    -RedirectStandardOutput "$logdir\synapse-access-service-8094.log" -RedirectStandardError "$logdir\synapse-access-service-8094.log.err" -WindowStyle Hidden
$up = $false
for ($i = 0; $i -lt 20; $i++) { Start-Sleep -Seconds 3; if (Get-NetTCPConnection -LocalPort 8094 -State Listen -ErrorAction SilentlyContinue) { $up = $true; break } }
Check '8094 实例已监听' $up 'port 8094 down'
Write-Host "    等 15s 让两实例注册 + 网关 LB 发现..." -ForegroundColor DarkGray
Start-Sleep -Seconds 15

Write-Host "`n=== B. 负载均衡轮询(间隔 150ms 避开网关 10 QPS 限流)===" -ForegroundColor Cyan
$h = @{ 'Authorization' = "Bearer $(Token)" }
$seq = @(); $tally = @{}
for ($i = 0; $i -lt 12; $i++) {
    try { $r = Invoke-RestMethod -Method Get -Uri "$base/api/access/whoami" -Headers $h -TimeoutSec 5; $p = $r.data.port } catch { $p = 'ERR' }
    $seq += $p; if ($tally.ContainsKey($p)) { $tally[$p]++ } else { $tally[$p] = 1 }
    Start-Sleep -Milliseconds 150
}
Write-Host ("    序列: {0}" -f ($seq -join ' '))
Check 'LoadBalancer 把请求分散到两实例' ($tally.Keys.Count -ge 2) "hit ports = $($tally.Keys -join ',')"
Check '两实例都被命中(8084 + 8094)' ($tally.ContainsKey('8084') -and $tally.ContainsKey('8094')) "$($tally.Keys -join ',')"

Write-Host "`n=== C. 故障摘除(杀 8094)===" -ForegroundColor Cyan
$pid8094 = (Get-NetTCPConnection -LocalPort 8094 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1).OwningProcess
if ($pid8094) { Stop-Process -Id $pid8094 -Force; Write-Host "    killed 8094 (pid=$pid8094)" -ForegroundColor Yellow }
Write-Host "    等 30s 让 Nacos 剔除死实例 + LB 刷新..." -ForegroundColor DarkGray
Start-Sleep -Seconds 30
$h = @{ 'Authorization' = "Bearer $(Token)" }
$seq2 = @(); $err = 0
for ($i = 0; $i -lt 12; $i++) {
    try { $r = Invoke-RestMethod -Method Get -Uri "$base/api/access/whoami" -Headers $h -TimeoutSec 5; $seq2 += $r.data.port } catch { $err++; $seq2 += 'ERR' }
    Start-Sleep -Milliseconds 150
}
Write-Host ("    序列: {0}" -f ($seq2 -join ' '))
$uniq = $seq2 | Select-Object -Unique
Check '剔除后零错误' ($err -eq 0) "errors=$err"
Check '流量全部落到存活实例 8084' (($uniq -contains '8084') -and -not ($uniq -contains '8094')) "unique=$($uniq -join ',')"

Write-Host "`n================ 结果 ================" -ForegroundColor Cyan
Write-Host "PASS=$pass  FAIL=$fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
Write-Host "8094 已关闭,集群回到单 access 实例稳态。" -ForegroundColor DarkGray
