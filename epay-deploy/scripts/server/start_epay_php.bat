@echo off
setlocal
call "%~dp0_settings.bat"

if not exist "%PHP_DIR%\php-cgi.exe" (
  echo [error] php-cgi.exe not found. Run install_php74.bat first.
  exit /b 1
)

if not exist "%EPAY_LOG_DIR%" mkdir "%EPAY_LOG_DIR%"
if not exist "%EPAY_RUN_DIR%" mkdir "%EPAY_RUN_DIR%"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$port=%PHP_FCGI_PORT%;" ^
  "$existing=Get-NetTCPConnection -LocalAddress '%PHP_FCGI_HOST%' -LocalPort $port -State Listen -ErrorAction SilentlyContinue;" ^
  "if($existing){ Write-Host '[php] already listening on port' $port; exit 0 }" ^
  "$p=Start-Process -FilePath '%PHP_DIR%\php-cgi.exe' -ArgumentList '-b %PHP_FCGI_HOST%:%PHP_FCGI_PORT% -c \"%PHP_INI%\"' -WorkingDirectory '%EPAY_APP_ROOT%' -WindowStyle Hidden -PassThru;" ^
  "Set-Content -Encoding ASCII '%EPAY_RUN_DIR%\php-cgi.pid' $p.Id;" ^
  "Start-Sleep -Seconds 1;" ^
  "Write-Host ('[php] started pid=' + $p.Id);"

endlocal

