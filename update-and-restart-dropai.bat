@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "ROOT_DIR=%~dp0"
set "BACKEND_DIR=%ROOT_DIR%backend"
set "TARGET_COMMIT=a17701ed65c46449735e13ff73a250fbc8a7434a"
set "EXPECTED_GATEWAY=https://pay.dropai-demo.com/submit.php"
set "WORD_FORMAT_ENABLED=true"
set "WORD_FORMAT_PYTHON=python"
set "WORD_FORMAT_WORKER=%ROOT_DIR%document-format-tool\format_cli.py"
set "WORD_FORMAT_LEGACY_TEMPLATES_ENABLED=true"

call :load_word_env "%ROOT_DIR%.env"
call :load_word_env "%BACKEND_DIR%\.env"
if "%WORD_FORMAT_ENABLED%"=="" set "WORD_FORMAT_ENABLED=true"
if "%WORD_FORMAT_PYTHON%"=="" set "WORD_FORMAT_PYTHON=python"
if "%WORD_FORMAT_WORKER%"=="" set "WORD_FORMAT_WORKER=%ROOT_DIR%document-format-tool\format_cli.py"
if "%WORD_FORMAT_LEGACY_TEMPLATES_ENABLED%"=="" set "WORD_FORMAT_LEGACY_TEMPLATES_ENABLED=true"

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

where python >nul 2>nul || (
  echo [ERROR] Python is not available in PATH.
  goto :fail
)

git rev-parse --is-inside-work-tree >nul 2>nul || (
  echo [ERROR] This directory is not a Git repository.
  goto :fail
)

set "STASHED_SERVER_CHANGES=0"
git diff --quiet
if errorlevel 1 goto :stash_server_changes
git diff --cached --quiet
if errorlevel 1 goto :stash_server_changes
goto :server_changes_ready

:stash_server_changes
echo [INFO] Saving tracked server-specific changes before update...
git status --short
git stash push -m "dropai-auto-update-server-config" || goto :fail
set "STASHED_SERVER_CHANGES=1"

:server_changes_ready

echo [1/7] Fetching origin/main...
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
  echo [2/7] Fast-forwarding to origin/main...
  git merge --ff-only origin/main || goto :restore_and_fail
) else (
  echo [2/7] Skipping merge because GitHub is unavailable.
)

if "!STASHED_SERVER_CHANGES!"=="1" (
  echo Restoring tracked server-specific changes...
  git stash pop || (
    echo [ERROR] Server configuration restore has conflicts.
    echo Resolve the conflicts before restarting the backend.
    goto :fail
  )
  set "STASHED_SERVER_CHANGES=0"
)

for /f %%H in ('git rev-parse --short HEAD') do set "CURRENT_COMMIT=%%H"
echo Current commit: !CURRENT_COMMIT!
git merge-base --is-ancestor %TARGET_COMMIT% HEAD >nul 2>nul || (
  echo [ERROR] Required commit %TARGET_COMMIT% is not installed.
  echo Restore GitHub access or apply the offline Git bundle, then run this script again.
  goto :fail
)

echo [3/7] Installing Python worker dependencies...
python -m pip install --disable-pip-version-check --no-input -r "%ROOT_DIR%diagram-worker\requirements-web.txt" || goto :fail
if /i "%WORD_FORMAT_ENABLED%"=="true" (
  call :resolve_word_formatter || goto :fail
  "%WORD_FORMAT_PYTHON%" -m pip install --disable-pip-version-check --no-input -r "!WORD_FORMAT_REQUIREMENTS!" || goto :fail
  call :preflight_word_formatter || goto :fail
) else (
  echo [INFO] Word formatter is disabled; skipping its dependency install and runtime check.
)

echo [4/7] Verifying payment gateway...
findstr /L /C:"%EXPECTED_GATEWAY%" "%ROOT_DIR%start-dropai-backend.bat" >nul || (
  echo [ERROR] start-dropai-backend.bat does not contain the expected gateway:
  echo %EXPECTED_GATEWAY%
  goto :fail
)

echo [5/7] Stopping the old DropAI backend, if running...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$items=Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*academic-rewrite-backend-0.0.1-SNAPSHOT.jar*' }; foreach($item in $items){ Stop-Process -Id $item.ProcessId -Force; Write-Host ('Stopped PID ' + $item.ProcessId) }"
timeout /t 2 /nobreak >nul

echo [6/7] Building backend without Maven clean...
cd /d "%BACKEND_DIR%" || goto :fail
call mvn package -DskipTests || goto :fail

if not exist "%BACKEND_DIR%\target\academic-rewrite-backend-0.0.1-SNAPSHOT.jar" (
  echo [ERROR] Backend JAR was not generated.
  goto :fail
)

echo [7/7] Starting DropAI backend in a new window...
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

:load_word_env
set "WORD_ENV_FILE=%~1"
if not exist "%WORD_ENV_FILE%" exit /b 0
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%WORD_ENV_FILE%") do (
  if /i "%%A"=="WORD_FORMAT_ENABLED" set "WORD_FORMAT_ENABLED=%%B"
  if /i "%%A"=="WORD_FORMAT_PYTHON" set "WORD_FORMAT_PYTHON=%%B"
  if /i "%%A"=="WORD_FORMAT_WORKER" set "WORD_FORMAT_WORKER=%%B"
  if /i "%%A"=="WORD_FORMAT_LEGACY_TEMPLATES_ENABLED" set "WORD_FORMAT_LEGACY_TEMPLATES_ENABLED=%%B"
)
exit /b 0

:resolve_word_formatter
set "WORD_FORMAT_WORKER_PATH="
if exist "%WORD_FORMAT_WORKER%" for %%I in ("%WORD_FORMAT_WORKER%") do set "WORD_FORMAT_WORKER_PATH=%%~fI"
if not defined WORD_FORMAT_WORKER_PATH if exist "%ROOT_DIR%%WORD_FORMAT_WORKER%" for %%I in ("%ROOT_DIR%%WORD_FORMAT_WORKER%") do set "WORD_FORMAT_WORKER_PATH=%%~fI"
if not defined WORD_FORMAT_WORKER_PATH if exist "%BACKEND_DIR%\%WORD_FORMAT_WORKER%" for %%I in ("%BACKEND_DIR%\%WORD_FORMAT_WORKER%") do set "WORD_FORMAT_WORKER_PATH=%%~fI"
if not defined WORD_FORMAT_WORKER_PATH (
  echo [ERROR] Word formatter not found: %WORD_FORMAT_WORKER%
  exit /b 1
)
for %%I in ("%WORD_FORMAT_WORKER_PATH%") do set "WORD_FORMAT_TOOL_DIR=%%~dpI"
set "WORD_FORMAT_RUNTIME_CHECK=%WORD_FORMAT_TOOL_DIR%runtime_check.py"
set "WORD_FORMAT_REQUIREMENTS=%WORD_FORMAT_TOOL_DIR%requirements-web.txt"
if not exist "%WORD_FORMAT_RUNTIME_CHECK%" (
  echo [ERROR] Word formatter runtime check not found: %WORD_FORMAT_RUNTIME_CHECK%
  exit /b 1
)
if not exist "%WORD_FORMAT_REQUIREMENTS%" (
  echo [ERROR] Word formatter requirements not found: %WORD_FORMAT_REQUIREMENTS%
  exit /b 1
)
exit /b 0

:preflight_word_formatter
"%WORD_FORMAT_PYTHON%" "%WORD_FORMAT_RUNTIME_CHECK%" >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Word formatter runtime check failed with: %WORD_FORMAT_PYTHON%
  exit /b 1
)
if /i not "%WORD_FORMAT_LEGACY_TEMPLATES_ENABLED%"=="true" exit /b 0
"%WORD_FORMAT_PYTHON%" "%WORD_FORMAT_RUNTIME_CHECK%" --legacy >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Legacy .doc/.dotx templates are enabled, but Microsoft Word COM is unavailable.
  echo Install desktop Microsoft Word, or set WORD_FORMAT_LEGACY_TEMPLATES_ENABLED=false.
  exit /b 1
)
exit /b 0

:restore_and_fail
if "!STASHED_SERVER_CHANGES!"=="1" (
  echo Restoring tracked server-specific changes after update failure...
  git stash pop
)
goto :fail

:fail
echo.
echo ========================================
echo  Update failed. No new backend was started.
echo ========================================
pause
exit /b 1
