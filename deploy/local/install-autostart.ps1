param(
    [switch]$Remove,
    [switch]$Highest
)

# 登录自启：把 start-erp.ps1 注册为登录时运行的计划任务（隐藏窗口，脚本幂等）。
# 默认用当前用户权限（Limited），不需要管理员；加 -Highest 且以管理员运行时才用最高权限。
# 注册失败时自动回退到"启动"文件夹快捷方式。用 -Remove 撤销。

$ErrorActionPreference = 'Stop'
$taskName   = 'SiyuERP-AutoStart'
$startScript = Join-Path $PSScriptRoot 'start-erp.ps1'
$shortcutName = '思雨食品ERP.lnk'

if (-not (Test-Path -LiteralPath $startScript)) {
    throw ("找不到 {0}，请确认仓库路径" -f $startScript)
}

# 必须用系统自带解释器：沙箱内的 pwsh.exe 在重启/沙箱回收后会失效
$psExe = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
if (-not (Test-Path -LiteralPath $psExe)) {
    $candidates = @('C:\Program Files\PowerShell\7\pwsh.exe') + @((Get-Command pwsh -ErrorAction SilentlyContinue).Source)
    $psExe = $candidates | Where-Object { $_ -and (Test-Path -LiteralPath $_) -and ($_ -notlike '*sandbox_runtime*') } | Select-Object -First 1
}
if (-not $psExe -or -not (Test-Path -LiteralPath $psExe)) { throw '未找到可用的 PowerShell 解释器' }

$startupDir = [Environment]::GetFolderPath('Startup')
$shortcutPath = Join-Path $startupDir $shortcutName

function Remove-Autostart {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $shortcutPath) { Remove-Item -LiteralPath $shortcutPath -Force }
    Write-Host ("已移除自启：计划任务 {0} / 快捷方式 {1}" -f $taskName, $shortcutName)
}

if ($Remove) { Remove-Autostart; exit 0 }

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
$runLevel = if ($Highest -and $isAdmin) { 'Highest' } else { 'Limited' }
if ($Highest -and -not $isAdmin) {
    Write-Host '提示：当前不是管理员，-Highest 已降级为 Limited（ERP 各组件均不需要提权，功能不受影响）。' -ForegroundColor Yellow
}

$registered = $false
try {
    $action = New-ScheduledTaskAction -Execute $psExe -Argument ('-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "{0}"' -f $startScript)
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable `
        -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 2 -RestartInterval (New-TimeSpan -Minutes 1)
    $principal = New-ScheduledTaskPrincipal -UserId ($env:USERDOMAIN + '\' + $env:USERNAME) -LogonType Interactive -RunLevel $runLevel

    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal `
        -Description '思雨食品 ERP 登录后自动拉起：MariaDB/Redis/后端/Caddy/隧道/发布 Pages' -Force | Out-Null
    $registered = $true
    Write-Host ("已注册计划任务 {0}（{1}）-> {2}" -f $taskName, $runLevel, $startScript) -ForegroundColor Green
} catch {
    Write-Host ('计划任务注册失败：' + $_.Exception.Message) -ForegroundColor Yellow
}

if (-not $registered) {
    $shell = New-Object -ComObject WScript.Shell
    $lnk = $shell.CreateShortcut($shortcutPath)
    $lnk.TargetPath = $psExe
    $lnk.Arguments = ('-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "{0}"' -f $startScript)
    $lnk.WorkingDirectory = (Split-Path -Parent $startScript)
    $lnk.WindowStyle = 7
    $lnk.Description = '思雨食品 ERP 登录后自动拉起'
    $lnk.Save()
    Write-Host ("已创建启动文件夹快捷方式 ${startupDir}\{0}" -f $shortcutName) -ForegroundColor Green
}

Write-Host ''
Write-Host '验证：'
if ($registered) { Write-Host ("  schtasks /Query /TN {0}" -f $taskName) }
Write-Host ("  Get-ChildItem '{0}'" -f $startupDir)
Write-Host '立即跑一次（验证效果）：'
if ($registered) { Write-Host ("  schtasks /Run /TN {0}" -f $taskName) }
Write-Host '撤销：'
Write-Host '  pwsh -File deploy\local\install-autostart.ps1 -Remove'
