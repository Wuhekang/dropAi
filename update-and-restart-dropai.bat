@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "ROOT_DIR=%~dp0"
set "BACKEND_DIR=%ROOT_DIR%backend"
set "TARGET_COMMIT=28b92976"
set "EXPECTED_GATEWAY=https://pay.dropai-demo.com/submit.php"

echo ========================================
echo  DropAI update, build and restart
echo ========================================
echo Root: %ROOT_DIR%
echo.

cd /d "%ROOT_DIR%" || goto :fail

where git >nul 2>nul || (
  echo [ERROR] Git is not available in PATH.
  goto :fail
)

where mvn >nul 2>nul || (
  echo [ERROR] Maven is not available in PATH.
  goto :fail
)

git rev-parse --is-inside-work-tree >nul 2>nul || (
  echo [ERROR] This directory is not a Git repository.
  goto :fail
)

git diff --quiet || (
  echo [ERROR] Tracked files have local changes. Update stopped to protect server configuration.
  git status --short
  goto :fail
)

git diff --cached --quiet || (
  echo [ERROR] Staged changes exist. Update stopped to protect server configuration.
  git status --short
  goto :fail
)

echo [1/6] Fetching origin/main...
set "FETCH_OK=0"
for /L %%N in (1,1,3) do (
  if "!FETCH_OK!"=="0" echo Attempt %%N of 3...
  git -c http.version=HTTP/1.1 fetch origin main && set "FETCH_OK=1" && goto :fetch_done
  timeout /t 3 /nobreak >nul
)

:fetch_done
for /f %%H in ('git rev-parse --short HEAD') do set "CURRENT_COMMIT=%%H"
if "!FETCH_OK!"=="0" (
  echo [WARN] GitHub fetch failed. Current commit: !CURRENT_COMMIT!
  echo The script can continue only if the required commit is already installed.
)

if "!FETCH_OK!"=="1" (
  echo [2/6] Fast-forwarding to origin/main...
  git merge --ff-only origin/main || goto :fail
) else (
  echo [2/6] Skipping merge because GitHub is unavailable.
)

for /f %%H in ('git rev-parse --short HEAD') do set "CURRENT_COMMIT=%%H"
echo Current commit: !CURRENT_COMMIT!
git merge-base --is-ancestor %TARGET_COMMIT% HEAD >nul 2>nul || (
  echo [ERROR] Required commit %TARGET_COMMIT% is not installed.
  echo Restore GitHub access or apply the offline Git bundle, then run this script again.
  goto :fail
)

echo [3/6] Verifying payment gateway...
findstr /L /C:"%EXPECTED_GATEWAY%" "%ROOT_DIR%start-dropai-backend.bat" >nul || (
  echo [ERROR] start-dropai-backend.bat does not contain the expected gateway:
  echo %EXPECTED_GATEWAY%
  goto :fail
)

echo [4/6] Stopping the old DropAI backend, if running...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$items=Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*academic-rewrite-backend-0.0.1-SNAPSHOT.jar*' }; foreach($item in $items){ Stop-Process -Id $item.ProcessId -Force; Write-Host ('Stopped PID ' + $item.ProcessId) }"
timeout /t 2 /nobreak >nul

echo [5/6] Building backend without Maven clean...
cd /d "%BACKEND_DIR%" || goto :fail
call mvn package -DskipTests || goto :fail

if not exist "%BACKEND_DIR%\target\academic-rewrite-backend-0.0.1-SNAPSHOT.jar" (
  echo [ERROR] Backend JAR was not generated.
  goto :fail
)

echo [6/6] Starting DropAI backend in a new window...
cd /d "%ROOT_DIR%"
start "DropAI Backend" cmd.exe /k call "%ROOT_DIR%start-dropai-backend.bat"

echo.
echo ========================================
echo  Update completed
echo ========================================
echo Commit:  !CURRENT_COMMIT!
echo Gateway: %EXPECTED_GATEWAY%
echo.
echo IMPORTANT:
echo The payment-platform merchant uid 1000 must use keytype=0 (MD5),
echo and its merchant key must match the runtime EPAY_KEY.
echo.
pause
exit /b 0

:fail
echo.
echo ========================================
echo  Update failed. No new backend was started.
echo ========================================
pause
exit /b 1
