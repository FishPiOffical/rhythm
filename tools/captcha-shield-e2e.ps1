param(
    [ValidateSet('configure', 'show', 'ips', 'browser')]
    [string]$Command
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$nodeScript = Join-Path $root 'tools\captcha-shield-e2e.mjs'

function Invoke-TestTool {
    param([Parameter(Mandatory = $true)][string]$Action)

    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        throw '未找到 Node.js。请先安装 Node.js 22 或更高版本。'
    }
    if (-not (Test-Path -LiteralPath $nodeScript -PathType Leaf)) {
        throw "找不到工具文件：$nodeScript"
    }

    Push-Location -LiteralPath $root
    try {
        & node $nodeScript $Action
        if ($LASTEXITCODE -ne 0) {
            throw "配置工具退出码：$LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

function Show-Menu {
    Clear-Host
    Write-Host '============================================' -ForegroundColor Cyan
    Write-Host '  Rhythm 验证码盾自动化验收工具' -ForegroundColor Cyan
    Write-Host '============================================' -ForegroundColor Cyan
    Write-Host '阶段 1 配置账号和 IP；阶段 2 使用 ChromeDriver 打开真实浏览器并在 /test 暂停。' -ForegroundColor Yellow
    Write-Host ''
    Write-Host '  1. 配置向导（首次使用或修改配置）'
    Write-Host '  2. 查看已保存配置'
    Write-Host '  3. 查看本机网卡 IP'
    Write-Host '  4. 阶段 2：浏览器流程（遇到极验时手动完成）'
    Write-Host '  0. 退出'
    Write-Host ''
}

try {
    if ($Command) {
        Invoke-TestTool -Action $Command
        exit 0
    }

    while ($true) {
        Show-Menu
        $choice = Read-Host '请输入编号'
        switch ($choice) {
            '1' {
                Invoke-TestTool -Action 'configure'
                Write-Host ''
                Read-Host '阶段 1 配置完成，按 Enter 返回菜单' | Out-Null
            }
            '2' {
                Invoke-TestTool -Action 'show'
                Write-Host ''
                Read-Host '按 Enter 返回菜单' | Out-Null
            }
            '3' {
                Invoke-TestTool -Action 'ips'
                Write-Host ''
                Read-Host '按 Enter 返回菜单' | Out-Null
            }
            '4' {
                Invoke-TestTool -Action 'browser'
                Write-Host ''
                Read-Host '阶段 2 浏览器流程完成，按 Enter 返回菜单' | Out-Null
            }
            '0' { break }
            default {
                Write-Host '请输入 1、2、3、4 或 0。' -ForegroundColor Red
                Start-Sleep -Seconds 1
            }
        }
    }
}
catch {
    Write-Host "`n失败：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host '按 Enter 退出。' -ForegroundColor Yellow
    Read-Host | Out-Null
    exit 1
}
