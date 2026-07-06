@echo off
setlocal
call "%~dp0_settings.bat"

echo [check] PHP binary
if exist "%PHP_DIR%\php.exe" (
  "%PHP_DIR%\php.exe" -v
) else (
  echo [fail] PHP not found: %PHP_DIR%\php.exe
)

echo.
echo [check] PHP-CGI process
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$pidFile='%EPAY_RUN_DIR%\php-cgi.pid';" ^
  "if(Test-Path $pidFile){$pid=Get-Content $pidFile; Get-Process -Id $pid -ErrorAction SilentlyContinue | Format-Table Id,ProcessName,StartTime -AutoSize}else{Write-Host '[warn] pid file not found'}"

echo.
echo [check] FastCGI port %PHP_FCGI_PORT%
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Test-NetConnection -ComputerName %PHP_FCGI_HOST% -Port %PHP_FCGI_PORT% | Select-Object ComputerName,RemotePort,TcpTestSucceeded | Format-List"

echo.
echo [check] Nginx proxy http://%EPAY_DOMAIN%/
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "try{$r=Invoke-WebRequest -Uri 'http://%EPAY_DOMAIN%/' -UseBasicParsing -TimeoutSec 10; Write-Host ('[ok] status=' + [int]$r.StatusCode)}catch{Write-Host ('[fail] ' + $_.Exception.Message)}"

echo.
echo [check] Epay install/admin endpoints
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "foreach($u in @('http://%EPAY_DOMAIN%/install','http://%EPAY_DOMAIN%/admin')){try{$r=Invoke-WebRequest -Uri $u -UseBasicParsing -TimeoutSec 10; Write-Host ('[ok] ' + $u + ' status=' + [int]$r.StatusCode)}catch{Write-Host ('[warn] ' + $u + ' ' + $_.Exception.Message)}}"

echo.
echo [check] DropAI notify endpoint
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "try{$r=Invoke-WebRequest -Uri 'https://%DROP_AI_DOMAIN%/api/recharge/notify' -Method GET -UseBasicParsing -TimeoutSec 10; Write-Host ('[ok] DropAI notify reachable, status=' + [int]$r.StatusCode)}catch{Write-Host ('[warn] DropAI notify check: ' + $_.Exception.Message)}"

endlocal

