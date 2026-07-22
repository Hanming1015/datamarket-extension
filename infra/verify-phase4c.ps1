# Phase 4c 验证:Redis Sentinel 高可用(1 主 2 从 3 哨兵)+ 主库故障自动转移 + 应用无感恢复。
#   A) 拓扑:哨兵识别 mymaster + 2 从 + 2 其他哨兵,quorum=2。
#   B) 故障转移:停主库 -> 哨兵在 down-after(5s)后选举提升一个从库为新主。
#   C) 应用存活:转移后经网关提交访问申请(走 Redis 幂等锁),返回正常业务码即证明 Lettuce
#      经哨兵通知重连到新主库(无 500 Redis 异常)。
#   D) 自愈:重启老主库,它作为从库重新加入,回到 1 主 2 从。
# 依赖 infra/.env 的 REDIS_PASSWORD;地址统一走 .env 的 REDIS_ANNOUNCE_IP(宿主+容器都可达)。
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$pass = 0; $fail = 0
function Check($name, $cond, $detail) {
    if ($cond) { Write-Host "  [PASS] $name" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  [FAIL] $name -- $detail" -ForegroundColor Red; $script:fail++ }
}
$envMap = @{}
Get-Content "$PSScriptRoot\.env" | ForEach-Object { $l = $_.Trim(); if ($l -and -not $l.StartsWith('#') -and $l.Contains('=')) { $k, $v = $l.Split('=', 2); $envMap[$k.Trim()] = $v.Trim() } }
$rpass = $envMap['REDIS_PASSWORD']
function Sentinel($cmd) { return docker exec synapse-redis-sentinel-1 redis-cli -p 26379 $cmd.Split(' ') 2>&1 }

Write-Host "`n=== A. 哨兵拓扑 ===" -ForegroundColor Cyan
$m = docker exec synapse-redis-sentinel-1 redis-cli -p 26379 sentinel master mymaster 2>&1
$kv = @{}; for ($i = 0; $i -lt $m.Count; $i += 2) { $kv[$m[$i]] = $m[$i + 1] }
Write-Host ("    master={0}:{1} slaves={2} other-sentinels={3} quorum={4}" -f $kv['ip'], $kv['port'], $kv['num-slaves'], $kv['num-other-sentinels'], $kv['quorum'])
Check '哨兵监控到 2 从' ("$($kv['num-slaves'])" -eq '2') "num-slaves=$($kv['num-slaves'])"
Check '哨兵集群 3 节点(2 其他)' ("$($kv['num-other-sentinels'])" -eq '2') "num-other=$($kv['num-other-sentinels'])"

Write-Host "`n=== B. 主库故障转移 ===" -ForegroundColor Cyan
$before = docker exec synapse-redis-sentinel-1 redis-cli -p 26379 sentinel get-master-addr-by-name mymaster 2>&1
$masterCid = docker ps --filter "publish=$($before[1])" --filter "name=synapse-redis" --format "{{.Names}}" 2>&1 | Select-Object -First 1
Write-Host ("    当前主库 {0}:{1}(容器 {2}),停掉它..." -f $before[0], $before[1], $masterCid) -ForegroundColor Yellow
docker stop $masterCid 2>&1 | Out-Null
$new = $null
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 2
    $a = docker exec synapse-redis-sentinel-1 redis-cli -p 26379 sentinel get-master-addr-by-name mymaster 2>&1
    if ("$($a[1])" -ne "$($before[1])") { $new = $a; break }
}
Check '哨兵完成故障转移(选出新主)' ($null -ne $new) 'no failover'
if ($new) { Write-Host ("    新主库 {0}:{1}" -f $new[0], $new[1]) -ForegroundColor Green }

Write-Host "`n=== C. 应用无感恢复 ===" -ForegroundColor Cyan
Start-Sleep -Seconds 6
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -Headers @{ 'Content-Type' = 'application/json' } -Body '{"username":"test","password":"123456"}'
$h = @{ 'Content-Type' = 'application/json'; 'Authorization' = "Bearer $($login.data.token)" }
$body = '{"datasetId":"failover-probe","consumerType":"Research Institution","purpose":"Research","requestedFields":["name"]}'
$ok = 0
for ($i = 0; $i -lt 3; $i++) {
    try { $r = Invoke-RestMethod -Method Post -Uri "$base/api/access" -Headers $h -Body $body -TimeoutSec 8; if ($r.code -eq 404) { $ok++ } } catch {}
    Start-Sleep -Milliseconds 500
}
Check '转移后应用 Redis 幂等锁正常(经哨兵重连新主)' ($ok -ge 1) "ok=$ok/3"

Write-Host "`n=== D. 自愈:重启老主库作为从库回归 ===" -ForegroundColor Cyan
docker start $masterCid 2>&1 | Out-Null
$rejoined = $false
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Seconds 2
    $role = docker exec $masterCid redis-cli -a $rpass --no-auth-warning info replication 2>&1 | Select-String 'role:' | ForEach-Object { $_.Line.Trim() }
    if ("$role" -match 'role:slave') { $rejoined = $true; break }
}
Check '老主库以从库身份重新加入(自愈)' $rejoined 'still master / not rejoined'

Write-Host "`n================ 结果 ================" -ForegroundColor Cyan
Write-Host "PASS=$pass  FAIL=$fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
