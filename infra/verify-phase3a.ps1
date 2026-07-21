# Phase 3a 全链路验证:提交→审批状态机 + Feign 编排 + Redis/DB 幂等
# 全程经网关 8080。业务失败是 HTTP 200 + body.code,故一律看 .code。
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

Write-Host "`n=== 0. 账号准备 ===" -ForegroundColor Cyan
$ownerLogin = Post '/api/auth/login' @{ username='test'; password='123456' } $null
$ownerToken = $ownerLogin.data.token
$ownerId = $ownerLogin.data.user.id
Check 'owner(test) 登录' ($ownerLogin.code -eq 200 -and $ownerToken) $ownerLogin.message

# 消费者:先尝试注册(已存在则忽略),再登录
try { Post '/api/auth/register' @{ username='consumer_p3'; password='123456'; name='Consumer P3'; email='cp3@example.com' } $null | Out-Null } catch {}
$consumerLogin = Post '/api/auth/login' @{ username='consumer_p3'; password='123456' } $null
$consumerToken = $consumerLogin.data.token
$consumerId = $consumerLogin.data.user.id
Check 'consumer_p3 登录' ($consumerLogin.code -eq 200 -and $consumerToken) $consumerLogin.message
Write-Host "  ownerId=$ownerId  consumerId=$consumerId"

Write-Host "`n=== 1. owner 造数据:数据集 + 定价 + 授权规则 ===" -ForegroundColor Cyan
$ds = Post '/api/datasets' @{
    name='P3A Test Dataset'; description='phase3a'; category='test'
    fieldsSchema=@(
        @{ name='name'; type='string'; sensitive=$true },
        @{ name='age'; type='integer'; sensitive=$false },
        @{ name='email'; type='string'; sensitive=$true }
    )
} $ownerToken
$datasetId = $ds.data.id
Check '创建数据集' ($ds.code -eq 200 -and $datasetId) $ds.message

$pr = Post "/api/datasets/$datasetId/pricing" @{
    perAccessBase=10; perField=5; sensitiveFieldMultiplier=2
} $ownerToken
Check '设置定价' ($pr.code -eq 200) $pr.message

$rule = Post '/api/consent/rules' @{
    datasetId=$datasetId
    allowedRoles=@('Research Institution')
    allowedPurposes=@('Research')
    allowedFields=@('name','age','email')
    validUntil='2030-12-31'
} $ownerToken
Check '创建授权规则' ($rule.code -eq 200) $rule.message

Write-Host "`n=== 2. 消费者提交申请 → PENDING_APPROVAL(编排:3 个 Feign) ===" -ForegroundColor Cyan
$r1 = Post '/api/access' @{
    datasetId=$datasetId; consumerType='Research Institution'; purpose='Research'
    requestedFields=@('name','age')
} $consumerToken
$r1id = $r1.data.id
Check 'R1 受理为 PENDING_APPROVAL' ($r1.code -eq 200 -and $r1.data.status -eq 'PENDING_APPROVAL') "code=$($r1.code) status=$($r1.data.status)"
Check 'R1 报价 cost>0(Feign quote 生效)' ($r1.data.cost -gt 0) "cost=$($r1.data.cost)"
Check 'R1 ownerId 快照正确' ($r1.data.ownerId -eq $ownerId) "ownerId=$($r1.data.ownerId)"
Write-Host "  R1 cost=$($r1.data.cost) (预期 25.00:base10 +(age5 + name5*2=10)=25)"

Write-Host "`n=== 3. 幂等:重复提交同一数据集 → 400 ===" -ForegroundColor Cyan
$dup = Post '/api/access' @{
    datasetId=$datasetId; consumerType='Research Institution'; purpose='Research'
    requestedFields=@('name','age')
} $consumerToken
Check '重复提交被幂等挡下(code=400)' ($dup.code -eq 400) "code=$($dup.code) msg=$($dup.message)"

Write-Host "`n=== 4. owner 查待审 + 越权保护 ===" -ForegroundColor Cyan
$pending = Get2 '/api/access/pending' $ownerToken
$hasR1 = @($pending.data.records | Where-Object { $_.id -eq $r1id }).Count -eq 1
Check 'owner 待审列表含 R1' $hasR1 "total=$($pending.data.total)"
# 消费者(非 owner)尝试批准 → 404 不泄露
$badApprove = Put "/api/access/$r1id/approve" $null $consumerToken
Check '非 owner 批准 → 404' ($badApprove.code -eq 404) "code=$($badApprove.code)"

Write-Host "`n=== 5. owner 批准 R1 → GRANTED ===" -ForegroundColor Cyan
$appr = Put "/api/access/$r1id/approve" $null $ownerToken
Check 'R1 批准后 GRANTED' ($appr.code -eq 200 -and $appr.data.status -eq 'GRANTED') "status=$($appr.data.status)"
Check 'R1 approverId=owner' ($appr.data.approverId -eq $ownerId) "approverId=$($appr.data.approverId)"

Write-Host "`n=== 6. 第二笔 → owner 驳回 → REJECTED(带原因) ===" -ForegroundColor Cyan
$r2 = Post '/api/access' @{
    datasetId=$datasetId; consumerType='Research Institution'; purpose='Research'
    requestedFields=@('email')
} $consumerToken
Check 'R2 受理为 PENDING_APPROVAL' ($r2.data.status -eq 'PENDING_APPROVAL') "status=$($r2.data.status)"
$rej = Put "/api/access/$($r2.data.id)/reject" @{ reason='not this quarter' } $ownerToken
Check 'R2 驳回后 REJECTED' ($rej.code -eq 200 -and $rej.data.status -eq 'REJECTED') "status=$($rej.data.status)"
Check 'R2 驳回原因已记录' ($rej.data.denialReasons.'(owner)' -eq 'not this quarter') "reasons=$($rej.data.denialReasons | ConvertTo-Json -Compress)"

Write-Host "`n=== 7. 引擎直拒:请求未授权字段 → 直接 REJECTED(不进审批) ===" -ForegroundColor Cyan
$r3 = Post '/api/access' @{
    datasetId=$datasetId; consumerType='Research Institution'; purpose='Research'
    requestedFields=@('ssn')
} $consumerToken
Check 'R3 引擎直拒为 REJECTED' ($r3.code -eq 200 -and $r3.data.status -eq 'REJECTED') "status=$($r3.data.status)"
Check 'R3 无 approverId(未经人工)' (-not $r3.data.approverId) "approverId=$($r3.data.approverId)"

Write-Host "`n=== 8. 消费者查'我的申请' + owner 自我申请保护 ===" -ForegroundColor Cyan
$mine = Get2 '/api/access/mine' $consumerToken
Check '我的申请 >= 3 条' ($mine.data.total -ge 3) "total=$($mine.data.total)"
$self = Post '/api/access' @{
    datasetId=$datasetId; consumerType='Research Institution'; purpose='Research'
    requestedFields=@('name')
} $ownerToken
Check 'owner 申请自己的数据集 → 400' ($self.code -eq 400) "code=$($self.code) msg=$($self.message)"

Write-Host "`n================ 结果 ================" -ForegroundColor Cyan
Write-Host "PASS=$pass  FAIL=$fail" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
# 清理:删掉测试数据集(级联留痕的申请记录保留,便于你 Postman 复看)
try { Invoke-RestMethod -Method Delete -Uri "$base/api/datasets/$datasetId" -Headers @{ Authorization="Bearer $ownerToken" } | Out-Null; Write-Host "已清理测试数据集 $datasetId" } catch {}
