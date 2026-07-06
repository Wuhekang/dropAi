@echo off
setlocal
call "%~dp0_settings.bat"

if not exist "%PHP_DIR%\php.exe" (
  echo [error] php.exe not found. Run install_php74.bat first.
  exit /b 1
)

if not exist "%EPAY_APP_ROOT%\cron.php" (
  echo [error] cron.php not found: %EPAY_APP_ROOT%\cron.php
  exit /b 1
)

if not exist "%EPAY_LOG_DIR%" mkdir "%EPAY_LOG_DIR%"

cd /d "%EPAY_APP_ROOT%"
"%PHP_DIR%\php.exe" "%EPAY_APP_ROOT%\cron.php" >> "%EPAY_LOG_DIR%\cron.log" 2>&1
endlocal

