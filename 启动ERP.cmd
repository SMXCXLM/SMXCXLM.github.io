@echo off
chcp 65001 >nul
setlocal
set "PS=pwsh"
where pwsh >nul 2>nul || set "PS=powershell"
"%PS%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy\local\start-erp.ps1" %*
if "%~1"=="" pause
endlocal
