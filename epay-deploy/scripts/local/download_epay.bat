@echo off
setlocal
call "%~dp0_settings.bat"

echo [epay] repo: %EPAY_REPO%
echo [epay] work dir: %WORK_DIR%

if not exist "%WORK_DIR%" mkdir "%WORK_DIR%"

where git >nul 2>nul
if %errorlevel%==0 (
  if exist "%SRC_DIR%\.git" (
    echo [epay] updating existing source...
    git -C "%SRC_DIR%" pull --ff-only
  ) else (
    if exist "%SRC_DIR%" rmdir /s /q "%SRC_DIR%"
    echo [epay] cloning source...
    git clone "%EPAY_REPO%" "%SRC_DIR%"
  )
) else (
  echo [epay] git not found, downloading zip...
  if exist "%SRC_DIR%" rmdir /s /q "%SRC_DIR%"
  mkdir "%SRC_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$zip='%WORK_DIR%\epay-master.zip';" ^
    "Invoke-WebRequest -Uri '%EPAY_ZIP%' -OutFile $zip;" ^
    "Expand-Archive -Force $zip '%WORK_DIR%\epay-zip';" ^
    "$root=Get-ChildItem '%WORK_DIR%\epay-zip' | Select-Object -First 1;" ^
    "Copy-Item -Recurse -Force (Join-Path $root.FullName '*') '%SRC_DIR%';"
)

echo [epay] source ready: %SRC_DIR%
endlocal

