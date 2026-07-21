# Phase 3b 全链路验证:审批事件 -> RabbitMQ topic 扇出 -> billing/audit 异步消费。
# 全程经网关 8080。消费是异步的,故断言用轮询(WaitUntil)等最终一致。
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$pass = 0; $fail = 0
function Check($name, $cond, $detail) {
    if ($cond) { Write-Host "  [PASS] $name" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  [FAIL] $name -- $detail" -ForegroundColor Red; $script:fail++ }
}
function Post($path, $body, $token) {
    $h = @{ 'Content-Type' = 'application/json' }
    if ($token) { $h['Authorization'] = "Bearer $token" }
    Invoke-RestMethod -Method Post -Uri "$base$path" -Headers $h -Body ($body | ConvertTo-Json -Depth 8)
}
function Put($path, $body, $token) {
    $h = @{ 'Content-Type' = 'application/json' }
    if ($token) { $h['Authorization'] = "Bearer $token" }
    $b = if ($body) { $body | ConvertTo-Json -Depth 8 } else { '{}' }
    Invoke-RestMethod -Method Put -Uri "$base$path" -Headers $h -Body $b
}
function Get2($path, $token) {
    $h = @{}; if ($token) { $h['Authorization'] = "Bearer $token" }
    Invoke-RestMethod -Method Get -Uri "$base$path" -Headers $h
}
# 轮询直到条件成立(异步最终一致);超时返回最后一次取到的值
function WaitUntil($fetch, $cond, $tries = 20, $ms = 500) {
    $last = $null
    for ($i = 0; $i -lt $tries; $i++) {
        $last = & $fetch
        if (& $cond $last) { return $last }
        Start-Sleep -Milliseconds $ms
    }
    return $last
}

Write-Host "`n=== 0. 账号准备 ===" -ForegroundColor Cyan
$ownerLogin = Post '/api/auth/login' @{ username = 'test'; password = '123456' } $null
$ownerToken = $ownerLogin.data.token
$ownerId = $ownerLogin.data.user.id
Check 'owner(test) 登录' ($ownerLogin.code -eq 200 -and $ownerToken) $ownerLogin.message

try { Post '/api/auth/register' @{ username = 'consumer_p3b'; password = '123456'; name = 'Consumer P3b'; email = 'cp3b@example.com' } $null | Out-Null } catch {}
$consumerLogin = Post '/api/auth/login' @{ username = 'consumer_p3b'; password = '123456' } $null
$consumerToken = $consumerLogin.data.token
$consumerId = $consumerLogin.data.user.id
Check 'consumer_p3b 登录' ($consumerLogin.code -eq 200 -and $consumerToken) $consumerLogin.message
Write-Host "  ownerId=$ownerId  consumerId=$consumerId"

Write-Host "`n=== 1. owner 造数据:数据集 + 定价 + 授权规则 ===" -ForegroundColor Cyan
$ds = Post '/api/datasets' @{
    name = 'P3B Test Dataset'; description = 'phase3b'; category = 'test'
    fieldsSchema = @(
        @{ name = 'name'; type = 'string'; sensitive = $true },
        @{ name = 'age'; type = 'integer'; sensitive = $false },
        @{ name = 'email'; type = 'string'; sensitive = $true }
    )
} $ownerToken
$datasetId = $ds.data.id
Check '创建数据集' ($ds.code -eq 200 -and $datasetId) $ds.message
$pr = Post "/api/datasets/$datasetId/pricing" @{ perAccessBase = 10; perField = 5; sensitiveFieldMultiplier = 2 } $ownerToken
Check '设置定价' ($pr.code -eq 200) $pr.message
$rule = Post '/api/consent/rules' @{
    datasetId = $datasetId; allowedRoles = @('Research Institution'); allowedPurposes = @('Research')
    allowedFields = @('name', 'age', 'email'); validUntil = '2030-12-31'
} $ownerToken
Check '创建授权规则' ($rule.code -eq 200) $rule.message

Write-Host "`n=== 2. 提交并批准 R1 -> 应产生 billing + audit 各一条 ===" -ForegroundColor Cyan
$r1 = Post '/api/access' @{ datasetId = $datasetId; consumerType = 'Research Institution'; purpose = 'Research'; requestedFields = @('name', 'age') } $consumerToken
$r1id = $r1.data.id
Check 'R1 受理为 PENDING_APPROVAL' ($r1.data.status -eq 'PENDING_APPROVAL') "status=$($r1.data.status)"
$appr = Put "/api/access/$r1id/approve" $null $ownerToken
Check 'R1 批准 -> GRANTED' ($appr.data.status -eq 'GRANTED') "status=$($appr.data.status)"

Write-Host "  等 billing 异步入账 ..." -ForegroundColor DarkGray
$bill = WaitUntil { Get2 '/api/billing/mine' $consumerToken } { param($x) @($x.data.records | Where-Object { $_.accessRequestId -eq $r1id }).Count -ge 1 }
$b1 = @($bill.data.records | Where-Object { $_.accessRequestId -eq $r1id })[0]
Check 'billing 收到 access.approved 并入账' ($null -ne $b1) "records=$($bill.data.total)"
Check 'billing 费用与审批一致(25.00)' ($null -ne $b1 -and [decimal]$b1.cost -eq [decimal]$appr.data.cost) "billCost=$($b1.cost) apprCost=$($appr.data.cost)"
Check 'billing 初始状态 UNPAID' ($null -ne $b1 -and $b1.paymentStatus -eq 'UNPAID') "paymentStatus=$($b1.paymentStatus)"

Write-Host "  等 audit 异步落日志 ..." -ForegroundColor DarkGray
$aud = WaitUntil { Get2 '/api/audit/mine' $consumerToken } { param($x) @($x.data.records | Where-Object { $_.action -eq 'ACCESS_APPROVED' -and $_.datasetId -eq $datasetId }).Count -ge 1 }
$hasApproved = @($aud.data.records | Where-Object { $_.action -eq 'ACCESS_APPROVED' -and $_.datasetId -eq $datasetId }).Count -ge 1
Check 'audit 收到 access.approved(扇出:同一事件 billing/audit 各一份)' $hasApproved "records=$($aud.data.total)"

Write-Host "`n=== 3. 提交并驳回 R2 -> 只进 audit,不进 billing ===" -ForegroundColor Cyan
$r2 = Post '/api/access' @{ datasetId = $datasetId; consumerType = 'Research Institution'; purpose = 'Research'; requestedFields = @('email') } $consumerToken
$r2id = $r2.data.id
$rej = Put "/api/access/$r2id/reject" @{ reason = 'not this quarter' } $ownerToken
Check 'R2 驳回 -> REJECTED' ($rej.data.status -eq 'REJECTED') "status=$($rej.data.status)"

Write-Host "  等 audit 落 REJECTED ..." -ForegroundColor DarkGray
$aud2 = WaitUntil { Get2 '/api/audit/mine' $consumerToken } { param($x) @($x.data.records | Where-Object { $_.action -eq 'ACCESS_REJECTED' }).Count -ge 1 }
$hasRejected = @($aud2.data.records | Where-Object { $_.action -eq 'ACCESS_REJECTED' }).Count -ge 1
Check 'audit 收到 access.rejected(通配 access.# 生效)' $hasRejected "records=$($aud2.data.total)"

# billing 不绑 access.rejected:给足时间后确认 R2 未入账
Start-Sleep -Milliseconds 2000
$bill2 = Get2 '/api/billing/mine' $consumerToken
$r2Billed = @($bill2.data.records | Where-Object { $_.accessRequestId -eq $r2id }).Count
Check 'billing 未对驳回入账(路由隔离)' ($r2Billed -eq 0) "r2Billed=$r2Billed"

Write-Host "`n=== 4. 幂等:再次拉取,R1 账单仍只有一条(消费端 accessRequestId 去重)===" -ForegroundColor Cyan
$bill3 = Get2 '/api/billing/mine' $consumerToken
$r1Count = @($bill3.data.records | Where-Object { $_.accessRequestId -eq $r1id }).Count
Check 'R1 账单恰好一条(无重复入账)' ($r1Count -eq 1) "r1Count=$r1Count"

Write-Host "`n================ 结果 ================" -ForegroundColor Cyan
Write-Host "PASS=$pass  FAIL=$fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
try { Invoke-RestMethod -Method Delete -Uri "$base/api/datasets/$datasetId" -Headers @{ Authorization = "Bearer $ownerToken" } | Out-Null; Write-Host "已清理测试数据集 $datasetId" } catch {}
