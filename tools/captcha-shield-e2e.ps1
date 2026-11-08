param(
    [ValidateSet('configure', 'show', 'ips', 'driver', 'edit', 'browser', 'flow')]
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

function Get-ConfigPath {
    $base = if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { $HOME }
    return Join-Path $base 'RhythmCaptchaShieldE2E\config.json'
}

function Edit-Config {
    $configPath = Get-ConfigPath
    if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
        throw "找不到配置文件：$configPath。请先运行配置向导。"
    }
    Write-Host "正在打开配置文件：$configPath" -ForegroundColor Cyan
    Write-Host '密码字段只保存为当前 Windows 用户 DPAPI 密文；不要删除 passwordProtected、ipPlan 或账号字段。' -ForegroundColor Yellow
    Start-Process -FilePath 'notepad.exe' -ArgumentList @($configPath) -Wait
}

function Show-Menu {
    Clear-Host
    Write-Host '============================================' -ForegroundColor Cyan
    Write-Host '  Rhythm 验证码盾自动化验收工具' -ForegroundColor Cyan
    Write-Host '============================================' -ForegroundColor Cyan
    Write-Host '阶段 1 配置账号和 IP；业务流程使用普通 Chrome + DevTools Protocol，并在 /test 暂停。' -ForegroundColor Yellow
    Write-Host ''
    Write-Host '  1. 配置向导（首次使用或修改配置）'
    Write-Host '  2. 查看已保存配置'
    Write-Host '  3. 查看本机网卡 IP'
    Write-Host '  4. 设置 ChromeDriver 路径'
    Write-Host '  5. 打开配置文件编辑'
    Write-Host '  6. 基础浏览器流程（ChromeDriver，兼容入口）'
    Write-Host '  7. 第一阶段完整业务流程'
    Write-Host '  0. 退出'
    Write-Host ''
}

try {
    if ($Command) {
        if ($Command -eq 'edit') {
            Edit-Config
        } else {
            Invoke-TestTool -Action $Command
        }
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
                Invoke-TestTool -Action 'driver'
                Write-Host ''
                Read-Host 'ChromeDriver 路径设置完成，按 Enter 返回菜单' | Out-Null
            }
            '5' {
                Edit-Config
                Write-Host ''
                Read-Host '配置文件编辑完成，按 Enter 返回菜单' | Out-Null
            }
            '6' {
                Invoke-TestTool -Action 'browser'
                Write-Host ''
                Read-Host '阶段 2 浏览器流程完成，按 Enter 返回菜单' | Out-Null
            }
            '7' {
                Invoke-TestTool -Action 'flow'
                Write-Host ''
                Read-Host '第一阶段完整业务流程完成，按 Enter 返回菜单' | Out-Null
            }
            '0' { break }
            default {
                Write-Host '请输入 1、2、3、4、5、6、7 或 0。' -ForegroundColor Red
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
