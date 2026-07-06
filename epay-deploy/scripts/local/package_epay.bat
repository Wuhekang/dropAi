@echo off
setlocal
call "%~dp0_settings.bat"

if not exist "%SRC_DIR%" (
  echo [error] source not found: %SRC_DIR%
  echo Run scripts\local\download_epay.bat first.
  exit /b 1
)

if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%DIST_DIR%"
mkdir "%DIST_DIR%\package-root"
mkdir "%DIST_DIR%\package-root\app"
mkdir "%DIST_DIR%\package-root\server-scripts"
mkdir "%DIST_DIR%\package-root\nginx"

echo [epay] copying app files...
robocopy "%SRC_DIR%" "%DIST_DIR%\package-root\app" /E /XD .git .github /XF .gitignore >nul
if %errorlevel% GEQ 8 exit /b %errorlevel%

echo [epay] copying server scripts...
robocopy "%BASE_DIR%\scripts\server" "%DIST_DIR%\package-root\server-scripts" /E >nul
if %errorlevel% GEQ 8 exit /b %errorlevel%

echo [epay] copying nginx templates...
robocopy "%BASE_DIR%\nginx" "%DIST_DIR%\package-root\nginx" /E >nul
if %errorlevel% GEQ 8 exit /b %errorlevel%

echo [epay] creating zip...
if exist "%PKG_FILE%" del "%PKG_FILE%" >nul 2>nul
where tar >nul 2>nul
if %errorlevel%==0 (
  tar -a -cf "%PKG_FILE%" -C "%DIST_DIR%\package-root" .
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop'; Compress-Archive -Force -Path '%DIST_DIR%\package-root\*' -DestinationPath '%PKG_FILE%'"
)

if not exist "%PKG_FILE%" (
  echo [error] package was not created.
  exit /b 1
)

echo [epay] package ready: %PKG_FILE%
endlocal
