@echo off
setlocal
call "%~dp0_settings.bat"

if not exist "%SRC_DIR%" (
  echo [error] source not found: %SRC_DIR%
  echo Run scripts\local\download_epay.bat first.
  exit /b 1
)

echo [epay] inspecting source...
dir "%SRC_DIR%"

if exist "%SRC_DIR%\install" (
  echo [ok] install directory found.
) else (
  echo [warn] install directory not found. Check upstream project structure.
)

if exist "%SRC_DIR%\admin" (
  echo [ok] admin directory found.
) else (
  echo [warn] admin directory not found. Check upstream project structure.
)

if exist "%SRC_DIR%\nginx.txt" (
  echo [ok] nginx.txt found.
) else (
  echo [warn] nginx.txt not found. Use generated nginx template first.
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Get-ChildItem -Recurse '%SRC_DIR%' -Include '*.sql','*.php','nginx.txt' | Select-Object FullName,Length | Format-Table -AutoSize"

echo [epay] inspect done.
endlocal

