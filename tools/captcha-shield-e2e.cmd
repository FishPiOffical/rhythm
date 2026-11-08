@echo off
setlocal
cd /d "%~dp0.."
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0captcha-shield-e2e.ps1" %*
if errorlevel 1 (
  echo.
  echo ≈‰÷√π§æﬂ÷¥–– ß∞‹°£
  pause
)
endlocal
