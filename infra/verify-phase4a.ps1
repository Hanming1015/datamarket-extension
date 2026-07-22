# Phase 4a-1 验证:access-service 的 Sentinel 服务级弹性。
#   A) 流控:access:create 受 Nacos 下发的 QPS 规则(count=5)保护,突发流量返回 code=429。
#   B) 熔断降级:杀掉 dataset-service,提交申请走 Feign fallback 返回 code=503(快速失败不雪崩),
#      随后自动重启 dataset-service 恢复集群。
# 规则源是 Nacos(synapse-access-service-flow-rules.json / -degrade-rules.json),不依赖 Dashboard。
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$pass = 0; $fail = 0
function Check($name, $cond, $detail) {
    if ($cond) { Write-Host "  [PASS] $name" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  [FAIL] $name -- $detail" -ForegroundColor Red; $script:fail++ }
}

Write-Host "`n=== 0. 登录 ===" -ForegroundColor Cyan
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -Headers @{ 'Content-Type' = 'application/json' } -Body '{"username":"test","password":"123456"}'
$h = @{ 'Content-Type' = 'application/json'; 'Authorization' = "Bearer $($login.data.token)" }
Check '登录拿到 token' ($login.code -eq 200 -and $login.data.token) 'login'
# 用不存在的 datasetId:既能进入 Sentinel 资源触发流控,又不会真的落库污染数据。
$body = '{"datasetId":"nonexistent-ds","consumerType":"Research Institution","purpose":"Research","requestedFields":["name"]}'

Write-Host "`n=== A. 流控(突发 30 连发,QPS>5 应被限流)===" -ForegroundColor Cyan
$codes = @{}
for ($i = 0; $i -lt 30; $i++) {
    try { $r = Invoke-RestMethod -Method Post -Uri "$base/api/access" -Headers $h -Body $body -TimeoutSec 5; $c = "$($r.code)" }
    catch { $c = "HTTP_$($_.Exception.Response.StatusCode.value__)" }
    if ($codes.ContainsKey($c)) { $codes[$c]++ } else { $codes[$c] = 1 }
}
$codes.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Host ("    code {0} -> {1}" -f $_.Name, $_.Value) }
Check '突发流量触发 429 限流' ($codes.ContainsKey('429') -and $codes['429'] -gt 0) "codes=$($codes.Keys -join ',')"
Check '限流未吞掉全部(仍有请求放行)' (($codes.Keys | Where-Object { $_ -ne '429' }).Count -gt 0) 'all throttled?'

Write-Host "`n=== B. 熔断降级(杀 dataset-service -> Feign fallback 503)===" -ForegroundColor Cyan
Start-Sleep -Seconds 2   # 让上一波流控窗口(1s)清空
$pid8083 = (Get-NetTCPConnection -LocalPort 8083 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1).OwningProcess
if ($pid8083) { Stop-Process -Id $pid8083 -Force; Write-Host "    killed dataset-service (pid=$pid8083)" -ForegroundColor Yellow }
Start-Sleep -Seconds 3
$deg = Invoke-RestMethod -Method Post -Uri "$base/api/access" -Headers $h -Body $body -TimeoutSec 8
Check '下游宕机 -> 优雅降级 code=503' ($deg.code -eq 503) "code=$($deg.code) msg=$($deg.message)"

# 快速失败:断路器/无实例短路后,后续请求延迟应显著低于首个探测请求
$lat = @()
for ($i = 0; $i -lt 5; $i++) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try { Invoke-RestMethod -Method Post -Uri "$base/api/access" -Headers $h -Body $body -TimeoutSec 8 | Out-Null } catch {}
    $sw.Stop(); $lat += $sw.ElapsedMilliseconds
    Start-Sleep -Milliseconds 250
}
Write-Host ("    后续 5 次延迟(ms): {0}" -f ($lat -join ', '))
Check '快速失败(后续请求延迟 < 200ms)' (($lat | Measure-Object -Average).Average -lt 200) "avg=$([int]($lat | Measure-Object -Average).Average)ms"

Write-Host "`n=== C. 恢复 dataset-service ===" -ForegroundColor Cyan
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$jar = "C:\Users\19831\Desktop\datamarket - extension\services\synapse-dataset-service\target\synapse-dataset-service-0.0.1-SNAPSHOT.jar"
$log = "$PSScriptRoot\logs\synapse-dataset-service.log"
Start-Process -FilePath $java -ArgumentList '-jar', ('"{0}"' -f $jar) -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden
$up = $false
for ($i = 0; $i -lt 20; $i++) { Start-Sleep -Seconds 3; if (Get-NetTCPConnection -LocalPort 8083 -State Listen -ErrorAction SilentlyContinue) { $up = $true; break } }
Check 'dataset-service 已重启(8083 监听)' $up 'port 8083 down'

Write-Host "`n================ 结果 ================" -ForegroundColor Cyan
Write-Host "PASS=$pass  FAIL=$fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
Write-Host "注:恢复后 Nacos 需 ~15s 重新发现实例,正常链路方可再用。" -ForegroundColor DarkGray
