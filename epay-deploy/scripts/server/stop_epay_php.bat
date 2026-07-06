@echo off
setlocal
call "%~dp0_settings.bat"

set "PID_FILE=%EPAY_RUN_DIR%\php-cgi.pid"
if exist "%PID_FILE%" (
  for /f %%P in (%PID_FILE%) do set "PHP_PID=%%P"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$pid='%PHP_PID%';" ^
    "$p=Get-Process -Id $pid -ErrorAction SilentlyContinue;" ^
    "if($p){ Stop-Process -Id $pid -Force; Write-Host '[php] stopped pid=' $pid } else { Write-Host '[php] pid not running' }"
  del "%PID_FILE%" >nul 2>nul
) else (
  echo [php] pid file not found. Nothing stopped.
)

endlocal

