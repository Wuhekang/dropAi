$ErrorActionPreference = "Stop"
$run = "C:\Users\Administrator\Documents\dropAi\work\acceptance\20260811-185646"
$mysql = Start-Process -FilePath "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe" -ArgumentList @("--no-defaults", "--datadir=$run\mysql-data", "--port=33317", "--bind-address=127.0.0.1", "--skip-log-bin", "--log-error=$run\mysql-browser.log") -WindowStyle Hidden -PassThru
$ready = $false
for ($i = 0; $i -lt 40; $i++) {
    & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqladmin.exe" --no-defaults --protocol=tcp --connect-timeout=2 --host=127.0.0.1 --port=33317 --user=root ping 2>$null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Milliseconds 500
}
if (-not $ready) { throw "MySQL browser acceptance start failed" }

$env:DB_URL = "jdbc:mysql://127.0.0.1:33317/library_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = ""
$env:SERVER_PORT = "18080"
$backend = Start-Process -FilePath "mvn.cmd" -ArgumentList @("spring-boot:run") -WorkingDirectory "C:\Users\Administrator\Documents\dropAi\work\generated\library-system\backend" -RedirectStandardOutput "$run\backend-browser.log" -RedirectStandardError "$run\backend-browser.err.log" -WindowStyle Hidden -PassThru

$env:VITE_BACKEND_URL = "http://127.0.0.1:18080"
$frontend = Start-Process -FilePath "npm.cmd" -ArgumentList @("run", "dev", "--", "--host", "127.0.0.1", "--port", "14173") -WorkingDirectory "C:\Users\Administrator\Documents\dropAi\work\generated\library-system\frontend" -RedirectStandardOutput "$run\frontend-browser.log" -RedirectStandardError "$run\frontend-browser.err.log" -WindowStyle Hidden -PassThru

$backendReady = $false
$frontendReady = $false
for ($i = 0; $i -lt 60; $i++) {
    try { $backendReady = (Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:18080/api/health" -TimeoutSec 2).StatusCode -eq 200 } catch {}
    try { $frontendReady = (Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:14173/login" -TimeoutSec 2).StatusCode -eq 200 } catch {}
    if ($backendReady -and $frontendReady) { break }
    Start-Sleep -Milliseconds 500
}
if (-not $backendReady -or -not $frontendReady) { throw "Browser acceptance services did not become ready" }

@{ mysqlPid = $mysql.Id; backendPid = $backend.Id; frontendPid = $frontend.Id } | ConvertTo-Json | Set-Content -Encoding UTF8 "$run\browser-processes.json"
Get-Content -Raw "$run\browser-processes.json"
