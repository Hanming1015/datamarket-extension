# Phase 3c-1 验证:审批→PENDING_PAYMENT→(Mock 支付)→payment.succeeded 扇出→GRANTED + 账单 PAID。
# 重点:outbox 驱动最终一致。业务大多经网关 8080;webhook 直连 payment-service 8085(模拟收银台回调,不走网关)。
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$paySvc = 'http://127.0.0.1:8085'
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
$ownerToken = $ownerLogin.data.token; $ownerId = $ownerLogin.data.user.id
Check 'owner(test) 登录' ($ownerLogin.code -eq 200 -and $ownerToken) $ownerLogin.message
try { Post '/api/auth/register' @{ username = 'consumer_c1'; password = '123456'; name = 'Consumer C1'; email = 'cc1@example.com' } $null | Out-Null } catch {}
$consumerLogin = Post '/api/auth/login' @{ username = 'consumer_c1'; password = '123456' } $null
$consumerToken = $consumerLogin.data.token; $consumerId = $consumerLogin.data.user.id
Check 'consumer_c1 登录' ($consumerLogin.code -eq 200 -and $consumerToken) $consumerLogin.message

Write-Host "`n=== 1. owner 造数据 + 消费者提交 ===" -ForegroundColor Cyan
$ds = Post '/api/datasets' @{
    name = 'P3C1 Dataset'; description = 'phase3c1'; category = 'test'
    fieldsSchema = @(@{ name = 'name'; type = 'string'; sensitive = $true }, @{ name = 'age'; type = 'integer'; sensitive = $false })
} $ownerToken
$datasetId = $ds.data.id
Post "/api/datasets/$datasetId/pricing" @{ perAccessBase = 10; perField = 5; sensitiveFieldMultiplier = 2 } $ownerToken | Out-Null
Post '/api/consent/rules' @{ datasetId = $datasetId; allowedRoles = @('Research Institution'); allowedPurposes = @('Research'); allowedFields = @('name', 'age'); validUntil = '2030-12-31' } $ownerToken | Out-Null
$r1 = Post '/api/access' @{ datasetId = $datasetId; consumerType = 'Research Institution'; purpose = 'Research'; requestedFields = @('name', 'age') } $consumerToken
$r1id = $r1.data.id
Check 'R1 PENDING_APPROVAL' ($r1.data.status -eq 'PENDING_APPROVAL') "status=$($r1.data.status)"
Write-Host "  R1=$r1id cost=$($r1.data.cost)"

Write-Host "`n=== 2. owner 批准 -> PENDING_PAYMENT(不再直接 GRANTED)===" -ForegroundColor Cyan
$appr = Put "/api/access/$r1id/approve" $null $ownerToken
Check 'R1 批准后 = PENDING_PAYMENT' ($appr.data.status -eq 'PENDING_PAYMENT') "status=$($appr.data.status)"
# outbox 驱动:billing 应异步入账一条 UNPAID
$bill = WaitUntil { Get2 '/api/billing/mine' $consumerToken } { param($x) @($x.data.records | Where-Object { $_.accessRequestId -eq $r1id }).Count -ge 1 }
$b1 = @($bill.data.records | Where-Object { $_.accessRequestId -eq $r1id })[0]
Check 'billing 入账 UNPAID(outbox 送达)' ($null -ne $b1 -and $b1.paymentStatus -eq 'UNPAID') "paymentStatus=$($b1.paymentStatus)"

Write-Host "`n=== 3. 消费者发起支付,拿收银台 ===" -ForegroundColor Cyan
$pay = Post '/api/payments' @{ accessRequestId = $r1id } $consumerToken
$sid = $pay.data.sessionId
Check '建单成功(UNPAID + 有 sessionId)' ($pay.code -eq 200 -and $pay.data.status -eq 'UNPAID' -and $sid) "code=$($pay.code) sid=$sid"
Check '支付金额 == 审批费用' ([decimal]$pay.data.amount -eq [decimal]$appr.data.cost) "amount=$($pay.data.amount) cost=$($appr.data.cost)"
# 付款前:access 仍 PENDING_PAYMENT
$before = Get2 "/api/access/$r1id" $consumerToken
Check '付款前 access 仍 PENDING_PAYMENT' ($before.data.status -eq 'PENDING_PAYMENT') "status=$($before.data.status)"

Write-Host "`n=== 4. 模拟收银台回调(直连 8085 webhook,不走网关)===" -ForegroundColor Cyan
$wh = Invoke-RestMethod -Method Post -Uri "$paySvc/webhooks/payment/mock?sessionId=$sid"
Check 'webhook 回调 200' ($wh.code -eq 200) "code=$($wh.code)"
# payment.succeeded 扇出 -> access GRANTED
$granted = WaitUntil { Get2 "/api/access/$r1id" $consumerToken } { param($x) $x.data.status -eq 'GRANTED' }
Check 'access 经 payment.succeeded -> GRANTED' ($granted.data.status -eq 'GRANTED') "status=$($granted.data.status)"
# billing 对账 -> PAID
$paidBill = WaitUntil { Get2 '/api/billing/mine' $consumerToken } { param($x) @($x.data.records | Where-Object { $_.accessRequestId -eq $r1id -and $_.paymentStatus -eq 'PAID' }).Count -ge 1 }
$pb = @($paidBill.data.records | Where-Object { $_.accessRequestId -eq $r1id })[0]
Check 'billing 对账为 PAID' ($pb.paymentStatus -eq 'PAID') "paymentStatus=$($pb.paymentStatus)"
# payment 订单 PAID
$myPay = Get2 '/api/payments/mine' $consumerToken
$po = @($myPay.data.records | Where-Object { $_.accessRequestId -eq $r1id })[0]
Check 'payment 订单 = PAID' ($po.status -eq 'PAID') "status=$($po.status)"

Write-Host "`n=== 5. 幂等:重复回调 + 重复建单 ===" -ForegroundColor Cyan
$wh2 = Invoke-RestMethod -Method Post -Uri "$paySvc/webhooks/payment/mock?sessionId=$sid"
Check '重复 webhook 不报错(幂等)' ($wh2.code -eq 200) "code=$($wh2.code)"
$again = Get2 "/api/access/$r1id" $consumerToken
Check '重复回调后 access 仍 GRANTED(未越界流转)' ($again.data.status -eq 'GRANTED') "status=$($again.data.status)"
$myPay2 = Get2 '/api/payments/mine' $consumerToken
$poCount = @($myPay2.data.records | Where-Object { $_.accessRequestId -eq $r1id }).Count
Check 'payment 订单仍恰好一个' ($poCount -eq 1) "count=$poCount"
$dupPay = Post '/api/payments' @{ accessRequestId = $r1id } $consumerToken
Check '已支付后再建单 -> 400' ($dupPay.code -eq 400) "code=$($dupPay.code) msg=$($dupPay.message)"

Write-Host "`n=== 6. 越权:他人对该申请建单 -> 404 ===" -ForegroundColor Cyan
# owner(非 requester)尝试对该申请建单
try { $bad = Post '/api/payments' @{ accessRequestId = $r1id } $ownerToken } catch { $bad = @{ code = -1 } }
Check '非 requester 建单 -> 404' ($bad.code -eq 404) "code=$($bad.code)"

Write-Host "`n================ 结果 ================" -ForegroundColor Cyan
Write-Host "PASS=$pass  FAIL=$fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
try { Invoke-RestMethod -Method Delete -Uri "$base/api/datasets/$datasetId" -Headers @{ Authorization = "Bearer $ownerToken" } | Out-Null; Write-Host "已清理测试数据集 $datasetId" } catch {}
