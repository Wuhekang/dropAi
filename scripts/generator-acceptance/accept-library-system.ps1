param(
    [string]$ProjectRoot = "C:\Users\Administrator\Documents\dropAi\work\generated\library-system",
    [int]$MySqlPort = 33317,
    [int]$BackendPort = 18080,
    [int]$FrontendPort = 14173
)

$ErrorActionPreference = "Stop"
$startedAt = Get-Date
$runId = $startedAt.ToString("yyyyMMdd-HHmmss")
$acceptRoot = Join-Path "C:\Users\Administrator\Documents\dropAi\work\acceptance" $runId
$mysqlData = Join-Path $acceptRoot "mysql-data"
$mysqlLog = Join-Path $acceptRoot "mysql.log"
$backendLog = Join-Path $acceptRoot "backend.log"
$frontendLog = Join-Path $acceptRoot "frontend.log"
$reportPath = Join-Path $ProjectRoot "acceptance-report.json"
$mysqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin"
$backendProcess = $null
$frontendProcess = $null
$mysqlProcess = $null

$report = [ordered]@{
    project = "library-system"
    runId = $runId
    backend = "PENDING"
    frontend = "PENDING"
    sql = "PENDING"
    crud = "PENDING"
    login = "PENDING"
    browser = "PENDING"
    durationSeconds = 0
    endpoints = [ordered]@{ backend = "http://127.0.0.1:$BackendPort"; frontend = "http://127.0.0.1:$FrontendPort" }
    logs = [ordered]@{ mysql = $mysqlLog; backend = $backendLog; frontend = $frontendLog }
    error = $null
}

function Wait-Http([string]$Url, [int]$Attempts = 90) {
    for ($i = 0; $i -lt $Attempts; $i++) {
        try { return Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2 }
        catch { Start-Sleep -Milliseconds 500 }
    }
    throw "HTTP endpoint did not become ready: $Url"
}

try {
    New-Item -ItemType Directory -Path $mysqlData -Force | Out-Null
    & (Join-Path $mysqlBin "mysqld.exe") --no-defaults --initialize-insecure "--datadir=$mysqlData"
    if ($LASTEXITCODE -ne 0) { throw "MySQL initialization failed" }

    $mysqlArgs = @("--no-defaults", "--datadir=$mysqlData", "--port=$MySqlPort", "--bind-address=127.0.0.1", "--skip-log-bin", "--log-error=$mysqlLog")
    $mysqlProcess = Start-Process -FilePath (Join-Path $mysqlBin "mysqld.exe") -ArgumentList $mysqlArgs -WindowStyle Hidden -PassThru
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        & (Join-Path $mysqlBin "mysqladmin.exe") --no-defaults --protocol=tcp --connect-timeout=2 --host=127.0.0.1 "--port=$MySqlPort" --user=root ping 2>$null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "Temporary MySQL did not start" }

    $sqlPath = (Join-Path $ProjectRoot "sql\database.sql").Replace("\", "/")
    & (Join-Path $mysqlBin "mysql.exe") --no-defaults --protocol=tcp --host=127.0.0.1 "--port=$MySqlPort" --user=root -e "source $sqlPath"
    if ($LASTEXITCODE -ne 0) { throw "database.sql execution failed" }
    $tableCount = & (Join-Path $mysqlBin "mysql.exe") --no-defaults --protocol=tcp --host=127.0.0.1 "--port=$MySqlPort" --user=root -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='library_system' AND table_name IN ('sys_user','sys_role','sys_permission','sys_menu','book');"
    if ([int]$tableCount -ne 5) { throw "Expected five initialized tables, found $tableCount" }
    $report.sql = "PASS"

    $oldDbUrl = $env:DB_URL; $oldDbUser = $env:DB_USERNAME; $oldDbPassword = $env:DB_PASSWORD; $oldServerPort = $env:SERVER_PORT
    $env:DB_URL = "jdbc:mysql://127.0.0.1:$MySqlPort/library_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
    $env:DB_USERNAME = "root"; $env:DB_PASSWORD = ""; $env:SERVER_PORT = "$BackendPort"
    $backendProcess = Start-Process -FilePath "mvn.cmd" -ArgumentList @("spring-boot:run") -WorkingDirectory (Join-Path $ProjectRoot "backend") -RedirectStandardOutput $backendLog -RedirectStandardError ($backendLog + ".err") -WindowStyle Hidden -PassThru
    $env:DB_URL = $oldDbUrl; $env:DB_USERNAME = $oldDbUser; $env:DB_PASSWORD = $oldDbPassword; $env:SERVER_PORT = $oldServerPort
    Wait-Http "http://127.0.0.1:$BackendPort/api/health" | Out-Null
    $report.backend = "PASS"

    $loginBody = @{ username = "admin"; password = "Admin123!" } | ConvertTo-Json -Compress
    $loginResult = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$BackendPort/api/auth/login" -ContentType "application/json" -Body $loginBody
    $token = $loginResult.data.token
    if ([string]::IsNullOrWhiteSpace($token)) { throw "Login did not return a JWT" }
    $report.login = "PASS"
    $headers = @{ Authorization = "Bearer $token" }

    $book = @{ name = "Runtime Acceptance"; isbn = "9780000000001"; author = "Dokiai"; status = "AVAILABLE" } | ConvertTo-Json -Compress
    $created = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$BackendPort/api/book" -Headers $headers -ContentType "application/json" -Body $book
    $id = [long]$created.data
    $page = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$BackendPort/api/book/page?pageNum=1&pageSize=10" -Headers $headers
    if ($page.data.total -lt 1) { throw "Created book did not appear in page API" }
    $updatedBook = @{ id = $id; name = "Runtime Acceptance Updated"; isbn = "9780000000001"; author = "Dokiai"; status = "AVAILABLE" } | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Put -Uri "http://127.0.0.1:$BackendPort/api/book" -Headers $headers -ContentType "application/json" -Body $updatedBook | Out-Null
    $detail = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$BackendPort/api/book/$id" -Headers $headers
    if ($detail.data.name -ne "Runtime Acceptance Updated") { throw "Book update was not persisted" }
    Invoke-RestMethod -Method Delete -Uri "http://127.0.0.1:$BackendPort/api/book/$id" -Headers $headers | Out-Null
    $report.crud = "PASS"

    Push-Location (Join-Path $ProjectRoot "frontend")
    try { npm install; if ($LASTEXITCODE -ne 0) { throw "npm install failed" }; npm run build; if ($LASTEXITCODE -ne 0) { throw "frontend build failed" } }
    finally { Pop-Location }
    $oldFrontendBackend = $env:VITE_BACKEND_URL
    $env:VITE_BACKEND_URL = "http://127.0.0.1:$BackendPort"
    $frontendProcess = Start-Process -FilePath "npm.cmd" -ArgumentList @("run", "dev", "--", "--host", "127.0.0.1", "--port", "$FrontendPort") -WorkingDirectory (Join-Path $ProjectRoot "frontend") -RedirectStandardOutput $frontendLog -RedirectStandardError ($frontendLog + ".err") -WindowStyle Hidden -PassThru
    $env:VITE_BACKEND_URL = $oldFrontendBackend
    Wait-Http "http://127.0.0.1:$FrontendPort/login" | Out-Null
    $report.frontend = "PASS"
}
catch {
    $report.error = $_.Exception.Message
    throw
}
finally {
    $report.durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
    $report | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 -LiteralPath $reportPath
    if ($frontendProcess -and -not $frontendProcess.HasExited) { Stop-Process -Id $frontendProcess.Id -Force -ErrorAction SilentlyContinue }
    if ($backendProcess -and -not $backendProcess.HasExited) { Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue }
    if ($mysqlProcess -and -not $mysqlProcess.HasExited) { & (Join-Path $mysqlBin "mysqladmin.exe") --no-defaults --protocol=tcp --connect-timeout=3 --host=127.0.0.1 "--port=$MySqlPort" --user=root shutdown 2>$null }
}

Get-Content -Raw -LiteralPath $reportPath
