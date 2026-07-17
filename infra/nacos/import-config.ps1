# =====================================================================
# 把 infra/nacos/config/*.yaml 推送到 Nacos 配置中心(幂等,可重复跑)。
# 用法:在 infra/ 或本目录下先确保容器已起、.env 已配好,然后:
#   powershell -ExecutionPolicy Bypass -File .\nacos\import-config.ps1
# 做的事:① 读 .env  ② 登录 Nacos 拿 accessToken
#         ③ 逐个把 config/*.yaml 里的 __VAR__ 用 .env 值替换后发布(Group=SYNAPSE_GROUP)
# =====================================================================
$ErrorActionPreference = 'Stop'

$here      = Split-Path -Parent $MyInvocation.MyCommand.Path      # infra/nacos
$infraDir  = Split-Path -Parent $here                            # infra
$envFile   = Join-Path $infraDir '.env'
$configDir = Join-Path $here 'config'
$group     = 'SYNAPSE_GROUP'
$nacosBase = 'http://127.0.0.1:8848/nacos'

if (-not (Test-Path $envFile)) { throw ".env 不存在:$envFile(先 cp .env.example .env 并填好)" }

# ① 读 .env 成哈希表(忽略注释与空行)
$envMap = @{}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
        $k, $v = $line.Split('=', 2)
        $envMap[$k.Trim()] = $v.Trim()
    }
}

$nacosUser = if ($envMap.ContainsKey('NACOS_USERNAME')) { $envMap['NACOS_USERNAME'] } else { 'nacos' }
$nacosPass = $envMap['NACOS_PASSWORD']
if (-not $nacosPass) { throw '.env 里缺少 NACOS_PASSWORD' }

# ② 登录拿 accessToken
Write-Host "登录 Nacos ($nacosUser) ..." -ForegroundColor Cyan
$login = Invoke-RestMethod -Method Post -Uri "$nacosBase/v1/auth/login" `
    -Body @{ username = $nacosUser; password = $nacosPass }
$token = $login.accessToken
if (-not $token) { throw 'Nacos 登录失败:未拿到 accessToken(检查用户名/密码或容器是否就绪)' }
Write-Host 'accessToken 获取成功' -ForegroundColor Green

# ③ 逐个发布配置
Get-ChildItem -Path $configDir -Filter '*.yaml' | ForEach-Object {
    $dataId  = $_.Name
    # 必须显式按 UTF-8 读,否则 PS 5.1 会用 GBK 解码 yaml 里的中文注释导致乱码
    $content = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)

    # 把所有 __VAR__ 占位符替换为 .env 中的值
    foreach ($k in $envMap.Keys) {
        $content = $content.Replace("__${k}__", $envMap[$k])
    }
    if ($content -match '__[A-Z0-9_]+__') {
        Write-Warning "$dataId 仍有未替换占位符,请检查 .env 是否缺项"
    }

    $resp = Invoke-RestMethod -Method Post -Uri "$nacosBase/v1/cs/configs?accessToken=$token" `
        -Body @{ dataId = $dataId; group = $group; type = 'yaml'; content = $content }
    if ("$resp" -eq 'true') {
        Write-Host "  ✔ 已发布 $dataId (group=$group)" -ForegroundColor Green
    } else {
        Write-Warning "  发布 $dataId 返回:$resp"
    }
}

Write-Host '全部配置推送完成。' -ForegroundColor Cyan
