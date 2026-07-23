# Phase 5 压测取真实简历数据(可观测性验证的收尾)。
#   A) 缓存冷/热延迟对比:先 DEL 掉 dataset::id 缓存键测冷读(走 DB),再连读测热读(走 Redis)。
#   B) 并发读压测:runspace 池并发打 GET /api/datasets/{id},算客户端 QPS;再查 Prometheus 直方图拿服务端 P50/P95/P99。
#   C) 缓存命中率:读 Redis INFO keyspace_hits/misses 前后差,算命中率。
#   D) access 全编排链路延迟:consumer 低速提交访问申请(gateway->access->Feign[dataset/consent/quote]),测 P99。
# 前置:9 服务在跑 + 观测栈(prometheus/zipkin)已起 + 已有 READY 数据集(dsId 从 %TEMP%\p5_dsid.txt 读)。
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$promBase = 'http://127.0.0.1:9090'

# 读 .env 拿 Redis 口令
$envMap = @{}
Get-Content "$PSScriptRoot\.env" | ForEach-Object { $l = $_.Trim(); if ($l -and -not $l.StartsWith('#') -and $l.Contains('=')) { $k, $v = $l.Split('=', 2); $envMap[$k.Trim()] = $v.Trim() } }
$rpass = $envMap['REDIS_PASSWORD']

$dsId = (Get-Content "$env:TEMP\p5_dsid.txt").Trim()
Write-Host "目标数据集: $dsId" -ForegroundColor Cyan

# 登录
$owner = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -Headers @{'Content-Type'='application/json'} -Body '{"username":"test","password":"123456"}'
$otok = $owner.data.token
$consumer = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -Headers @{'Content-Type'='application/json'} -Body '{"username":"consumer5d","password":"123456"}'
$ctok = $consumer.data.token

function Percentile($arr, $p) {
    $sorted = $arr | Sort-Object
    $idx = [math]::Ceiling(($p / 100.0) * $sorted.Count) - 1
    if ($idx -lt 0) { $idx = 0 }
    return $sorted[$idx]
}

# ============ A. 缓存冷/热延迟对比 ============
Write-Host "`n=== A. 缓存冷读 vs 热读延迟 ===" -ForegroundColor Cyan
$hdr = @{'Authorization'="Bearer $otok"}
# 冷读:先删缓存键(dataset::id),下一次读走 DB 并回填
docker exec synapse-redis-master redis-cli -a $rpass --no-auth-warning DEL "dataset::$dsId" | Out-Null
$cold = @()
for ($i = 0; $i -lt 5; $i++) {
    docker exec synapse-redis-master redis-cli -a $rpass --no-auth-warning DEL "dataset::$dsId" | Out-Null
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-RestMethod -Method Get -Uri "$base/api/datasets/$dsId" -Headers $hdr | Out-Null
    $sw.Stop(); $cold += $sw.Elapsed.TotalMilliseconds
}
# 热读:缓存已在,连读
$warm = @()
for ($i = 0; $i -lt 50; $i++) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-RestMethod -Method Get -Uri "$base/api/datasets/$dsId" -Headers $hdr | Out-Null
    $sw.Stop(); $warm += $sw.Elapsed.TotalMilliseconds
}
$coldAvg = [math]::Round(($cold | Measure-Object -Average).Average, 1)
$warmAvg = [math]::Round(($warm | Measure-Object -Average).Average, 1)
$warmP99 = [math]::Round((Percentile $warm 99), 1)
$drop = [math]::Round((1 - $warmAvg / $coldAvg) * 100, 1)
Write-Host ("  冷读(走DB) 均值 {0}ms | 热读(走Redis) 均值 {1}ms P99 {2}ms" -f $coldAvg, $warmAvg, $warmP99)
Write-Host ("  >>> 缓存使数据集详情读取平均延迟下降 {0}%" -f $drop) -ForegroundColor Green

# ============ C(前置). 记录 Redis keyspace 基线 ============
function RedisStat($key) {
    $line = docker exec synapse-redis-master redis-cli -a $rpass --no-auth-warning INFO stats 2>&1 | Select-String "^${key}:"
    if ($line) { return [int]($line.Line.Split(':')[1].Trim()) } else { return 0 }
}
$hits0 = RedisStat 'keyspace_hits'
$miss0 = RedisStat 'keyspace_misses'

# ============ B. 并发读压测(runspace 池) ============
Write-Host "`n=== B. 并发读压测 GET /api/datasets/{id} ===" -ForegroundColor Cyan
$workers = 8
$perWorker = 400
$total = $workers * $perWorker
$pool = [runspacefactory]::CreateRunspacePool(1, $workers); $pool.Open()
$jobs = @()
$sb = {
    param($base, $dsId, $tok, $n)
    $h = @{'Authorization'="Bearer $tok"}
    $ok = 0
    for ($i = 0; $i -lt $n; $i++) {
        try { Invoke-RestMethod -Method Get -Uri "$base/api/datasets/$dsId" -Headers $h -TimeoutSec 10 | Out-Null; $ok++ } catch {}
    }
    return $ok
}
$swAll = [System.Diagnostics.Stopwatch]::StartNew()
for ($w = 0; $w -lt $workers; $w++) {
    $ps = [powershell]::Create(); $ps.RunspacePool = $pool
    $ps.AddScript($sb).AddArgument($base).AddArgument($dsId).AddArgument($otok).AddArgument($perWorker) | Out-Null
    $jobs += [pscustomobject]@{ ps = $ps; handle = $ps.BeginInvoke() }
}
$okTotal = 0
foreach ($j in $jobs) { $res = $j.ps.EndInvoke($j.handle); $okTotal += [int]$res[0]; $j.ps.Dispose() }
$swAll.Stop(); $pool.Close()
$elapsed = $swAll.Elapsed.TotalSeconds
$qps = [math]::Round($okTotal / $elapsed, 0)
Write-Host ("  {0} 请求 / {1}s / {2} 并发 -> 客户端吞吐 {3} req/s (成功 {4})" -f $total, [math]::Round($elapsed,1), $workers, $qps, $okTotal)

# ============ C. 缓存命中率 ============
$hits1 = RedisStat 'keyspace_hits'
$miss1 = RedisStat 'keyspace_misses'
$dh = $hits1 - $hits0; $dm = $miss1 - $miss0
$hitRate = if (($dh + $dm) -gt 0) { [math]::Round($dh * 100.0 / ($dh + $dm), 1) } else { 0 }
Write-Host "`n=== C. 缓存命中率(压测期间 Redis keyspace 增量)===" -ForegroundColor Cyan
Write-Host ("  hits+{0} misses+{1} -> 命中率 {2}%" -f $dh, $dm, $hitRate) -ForegroundColor Green

# ============ D. access 全编排链路延迟(低速避开限流)============
Write-Host "`n=== D. access 全编排链路延迟(gateway->access->Feign x3)===" -ForegroundColor Cyan
$ch = @{'Content-Type'='application/json'; 'Authorization'="Bearer $ctok"}
$body = ('{{"datasetId":"{0}","consumerType":"Research Institution","purpose":"Research","requestedFields":["name","email"]}}' -f $dsId)
$lat = @(); $okA = 0
for ($i = 0; $i -lt 30; $i++) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try { $r = Invoke-RestMethod -Method Post -Uri "$base/api/access" -Headers $ch -Body $body -TimeoutSec 10; if ($r.code -in 200,409) { $okA++ } } catch {}
    $sw.Stop(); $lat += $sw.Elapsed.TotalMilliseconds
    Start-Sleep -Milliseconds 250   # ~4 QPS,低于 access:create 的 5 QPS 限流
}
$aP50 = [math]::Round((Percentile $lat 50),1); $aP99 = [math]::Round((Percentile $lat 99),1)
Write-Host ("  {0} 次编排 (成功/重复 {1}) -> 端到端 P50 {2}ms P99 {3}ms" -f $lat.Count, $okA, $aP50, $aP99)

# ============ 服务端 Prometheus P99(读路径)============
Write-Host "`n=== 服务端 Prometheus 直方图分位(dataset 读路径)===" -ForegroundColor Cyan
Start-Sleep -Seconds 6   # 等 Prometheus 抓到最新样本
function PromQ($q) {
    $r = Invoke-RestMethod ("{0}/api/v1/query?query={1}" -f $promBase, [uri]::EscapeDataString($q)) -TimeoutSec 8
    if ($r.data.result.Count -gt 0) { return [double]$r.data.result[0].value[1] } else { return $null }
}
foreach ($p in @(0.5, 0.95, 0.99)) {
    $q = "histogram_quantile($p, sum(rate(http_server_requests_seconds_bucket{service=`"synapse-dataset-service`",uri=`"/api/datasets/{id}`"}[5m])) by (le))"
    $v = PromQ $q
    if ($v -ne $null) { Write-Host ("  dataset GET /{{id}} P{0} = {1} ms" -f ($p*100), [math]::Round($v*1000,1)) }
}

Write-Host "`n================ Phase 5 简历数据汇总 ================" -ForegroundColor Cyan
Write-Host ("  缓存热读均值 {0}ms(冷读 {1}ms,降 {2}%)" -f $warmAvg, $coldAvg, $drop)
Write-Host ("  读路径客户端吞吐 ~{0} req/s（{1} 并发）" -f $qps, $workers)
Write-Host ("  压测期缓存命中率 {0}%" -f $hitRate)
Write-Host ("  access 全编排 P99 {0}ms" -f $aP99)
