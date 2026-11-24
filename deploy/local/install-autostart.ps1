param(
    [switch]$Remove
)

# 开机/登录自启：把 start-erp.ps1 注册为登录时运行的计划任务（隐藏窗口）。
# 需要管理员权限；用 -Remove 可撤销。

$ErrorActionPreference = 'Stop'
$taskName = 'SiyuERP-AutoStart'
$script   = Join-Path $PSScriptRoot 'start-erp.ps1'

if ($Remove) {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host ("已移除计划任务 {0}" -f $taskName)
    exit 0
}

$psExe = (Get-Command pwsh -ErrorAction SilentlyContinue).Source
if (-not $psExe) { $psExe = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe' }

$action = New-ScheduledTaskAction -Execute $psExe -Argument ('-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "{0}"' -f $script)
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 2 -RestartInterval (New-TimeSpan -Minutes 1)
$principal = New-ScheduledTaskPrincipal -UserId ($env:USERDOMAIN + '\' + $env:USERNAME) -LogonType Interactive -RunLevel Highest

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description '思雨食品 ERP 登录后自动拉起数据库/后端/网关/隧道' -Force | Out-Null
Write-Host ("已注册计划任务 {0} -> {1}" -f $taskName, $script)
Write-Host '验证：schtasks /Query /TN SiyuERP-AutoStart'
Write-Host '立即跑一次：schtasks /Run /TN SiyuERP-AutoStart'
