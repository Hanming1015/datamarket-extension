# Phase 3c-2 验证:notification-service 消费 access.# + payment.# 落站内通知。
# 批准/驳回 -> APPROVAL 通知;支付成功 -> PAYMENT 通知。全程经网关 8080;webhook 直连 8085。
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$paySvc = 'http://127.0.0.1:8085'
$pass = 0; $fail = 0
function Check($name, $cond, $detail) {
    if ($cond) { Write-Host "  [PASS] $name" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  [FAIL] $name -- $detail" -ForegroundColor Red; $script:fail++ }
}
function Post($path, $body, $token) {
    $h = @{ 'Content-Type' = 'application/json' }; if ($token) { $h['Authorization'] = "Bearer $token" }
    Invoke-RestMethod -Method Post -Uri "$base$path" -Headers $h -Body ($body | ConvertTo-Json -Depth 8)
}
function Put($path, $body, $token) {
    $h = @{ 'Content-Type' = 'application/json' }; if ($token) { $h['Authorization'] = "Bearer $token" }
    $b = if ($body) { $body | ConvertTo-Json -Depth 8 } else { '{}' }
    Invoke-RestMethod -Method Put -Uri "$base$path" -Headers $h -Body $b
}
function Get2($path, $token) {
    $h = @{}; if ($token) { $h['Authorization'] = "Bearer $token" }
    Invoke-RestMethod -Method Get -Uri "$base$path" -Headers $h
}
function WaitUntil($fetch, $cond, $tries = 20, $ms = 500) {
    $last = $null
    for ($i = 0; $i -lt $tries; $i++) { $last = & $fetch; if (& $cond $last) { return $last }; Start-Sleep -Milliseconds $ms }
    return $last
}
# PS 5.1 的 Invoke-RestMethod 把无 charset 的 UTF-8 响应体按 Latin-1 解码,导致中文乱码。
# 这里把"被当成 Latin-1 的 UTF-8 字节"重新按 UTF-8 解出来,修复中文比较。
function Utf8($s) {
    if ($null -eq $s) { return $s }
    return [System.Text.Encoding]::UTF8.GetString([System.Text.Encoding]::GetEncoding(28591).GetBytes($s))
}

Write-Host "`n=== 0. 账号 + 造数据 ===" -ForegroundColor Cyan
$ownerLogin = Post '/api/auth/login' @{ username = 'test'; password = '123456' } $null
$ownerToken = $ownerLogin.data.token
try { Post '/api/auth/register' @{ username = 'consumer_c2'; password = '123456'; name = 'Consumer C2'; email = 'cc2@example.com' } $null | Out-Null } catch {}
$consumerLogin = Post '/api/auth/login' @{ username = 'consumer_c2'; password = '123456' } $null
$consumerToken = $consumerLogin.data.token; $consumerId = $consumerLogin.data.user.id
Check 'owner + consumer_c2 登录' ($ownerLogin.code -eq 200 -and $consumerLogin.code -eq 200) 'login'
$ds = Post '/api/datasets' @{ name = 'P3C2 Dataset'; description = 'c2'; category = 'test'
    fieldsSchema = @(@{ name = 'name'; type = 'string'; sensitive = $true }, @{ name = 'age'; type = 'integer'; sensitive = $false }) } $ownerToken
$datasetId = $ds.data.id
Post "/api/datasets/$datasetId/pricing" @{ perAccessBase = 10; perField = 5; sensitiveFieldMultiplier = 2 } $ownerToken | Out-Null
Post '/api/consent/rules' @{ datasetId = $datasetId; allowedRoles = @('Research Institution'); allowedPurposes = @('Research'); allowedFields = @('name', 'age'); validUntil = '2030-12-31' } $ownerToken | Out-Null

Write-Host "`n=== 1. R1 批准 -> APPROVAL 通知 ===" -ForegroundColor Cyan
$r1 = Post '/api/access' @{ datasetId = $datasetId; consumerType = 'Research Institution'; purpose = 'Research'; requestedFields = @('name', 'age') } $consumerToken
$r1id = $r1.data.id
Put "/api/access/$r1id/approve" $null $ownerToken | Out-Null
$n1 = WaitUntil { Get2 '/api/notifications/mine' $consumerToken } { param($x) @($x.data.records | Where-Object { $_.refId -eq $r1id -and $_.type -eq 'APPROVAL' }).Count -ge 1 }
$appNote = @($n1.data.records | Where-Object { $_.refId -eq $r1id -and $_.type -eq 'APPROVAL' })[0]
Check '批准产生 APPROVAL 通知' ($null -ne $appNote -and (Utf8 $appNote.title) -like '*批准*') "title=$(Utf8 $appNote.title)"

Write-Host "`n=== 2. R1 支付 -> PAYMENT 通知 ===" -ForegroundColor Cyan
$pay = Post '/api/payments' @{ accessRequestId = $r1id } $consumerToken
Invoke-RestMethod -Method Post -Uri "$paySvc/webhooks/payment/mock?sessionId=$($pay.data.sessionId)" | Out-Null
$n2 = WaitUntil { Get2 '/api/notifications/mine' $consumerToken } { param($x) @($x.data.records | Where-Object { $_.refId -eq $r1id -and $_.type -eq 'PAYMENT' }).Count -ge 1 }
$payNote = @($n2.data.records | Where-Object { $_.refId -eq $r1id -and $_.type -eq 'PAYMENT' })[0]
Check '支付成功产生 PAYMENT 通知' ($null -ne $payNote -and (Utf8 $payNote.title) -like '*支付成功*') "title=$(Utf8 $payNote.title)"

Write-Host "`n=== 3. R2 驳回 -> APPROVAL(驳回)通知 ===" -ForegroundColor Cyan
$r2 = Post '/api/access' @{ datasetId = $datasetId; consumerType = 'Research Institution'; purpose = 'Research'; requestedFields = @('name') } $consumerToken
$r2id = $r2.data.id
Put "/api/access/$r2id/reject" @{ reason = 'no' } $ownerToken | Out-Null
$n3 = WaitUntil { Get2 '/api/notifications/mine' $consumerToken } { param($x) @($x.data.records | Where-Object { $_.refId -eq $r2id -and $_.type -eq 'APPROVAL' }).Count -ge 1 }
$rejNote = @($n3.data.records | Where-Object { $_.refId -eq $r2id -and $_.type -eq 'APPROVAL' })[0]
Check '驳回产生 APPROVAL 通知(驳回文案)' ($null -ne $rejNote -and (Utf8 $rejNote.title) -like '*驳回*') "title=$(Utf8 $rejNote.title)"

Write-Host "`n=== 4. 幂等 + 已读 ===" -ForegroundColor Cyan
Start-Sleep -Milliseconds 1500
$all = Get2 '/api/notifications/mine' $consumerToken
$c1 = @($all.data.records | Where-Object { $_.refId -eq $r1id -and $_.type -eq 'APPROVAL' }).Count
$cp = @($all.data.records | Where-Object { $_.refId -eq $r1id -and $_.type -eq 'PAYMENT' }).Count
Check 'R1 APPROVAL 恰好一条(refId+type 幂等)' ($c1 -eq 1) "count=$c1"
Check 'R1 PAYMENT 恰好一条' ($cp -eq 1) "count=$cp"
Check '通知总数 >= 3' ($all.data.total -ge 3) "total=$($all.data.total)"
# 标记已读
Put "/api/notifications/$($appNote.id)/read" $null $consumerToken | Out-Null
$after = Get2 '/api/notifications/mine' $consumerToken
$read = @($after.data.records | Where-Object { $_.id -eq $appNote.id })[0]
Check '标记已读生效(isRead=true)' ($read.isRead -eq $true) "isRead=$($read.isRead)"

Write-Host "`n================ 结果 ================" -ForegroundColor Cyan
Write-Host "PASS=$pass  FAIL=$fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
try { Invoke-RestMethod -Method Delete -Uri "$base/api/datasets/$datasetId" -Headers @{ Authorization = "Bearer $ownerToken" } | Out-Null; Write-Host "已清理测试数据集 $datasetId" } catch {}
