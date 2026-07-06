@echo off
setlocal
call "%~dp0_settings.bat"

set "PHP_URL=https://downloads.php.net/~windows/releases/archives/php-7.4.33-nts-Win32-vc15-x64.zip"
set "PHP_ZIP=%EPAY_RUNTIME%\php-7.4.33-nts-Win32-vc15-x64.zip"

if not exist "%EPAY_RUNTIME%" mkdir "%EPAY_RUNTIME%"
if not exist "%EPAY_LOG_DIR%" mkdir "%EPAY_LOG_DIR%"
if not exist "%EPAY_RUN_DIR%" mkdir "%EPAY_RUN_DIR%"

if not exist "%PHP_DIR%\php.exe" (
  echo [php] downloading PHP 7.4.33 NTS x64...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%PHP_URL%' -OutFile '%PHP_ZIP%'"
  echo [php] extracting...
  if exist "%PHP_DIR%" rmdir /s /q "%PHP_DIR%"
  mkdir "%PHP_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%PHP_ZIP%' '%PHP_DIR%'"
) else (
  echo [php] existing PHP found: %PHP_DIR%
)

if not exist "%PHP_INI%" (
  copy "%PHP_DIR%\php.ini-production" "%PHP_INI%" >nul
)

echo [php] configuring php.ini...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ini='%PHP_INI%';" ^
  "$text=Get-Content -Raw $ini;" ^
  "$text=$text -replace ';extension_dir = \"ext\"','extension_dir = \"ext\"';" ^
  "$exts=@('curl','fileinfo','gd2','mbstring','mysqli','openssl','pdo_mysql');" ^
  "foreach($e in $exts){ $text=$text -replace (';extension='+$e),('extension='+$e) };" ^
  "$text=$text -replace 'memory_limit = 128M','memory_limit = 256M';" ^
  "$text=$text -replace 'upload_max_filesize = 2M','upload_max_filesize = 20M';" ^
  "$text=$text -replace 'post_max_size = 8M','post_max_size = 20M';" ^
  "Set-Content -Encoding ASCII $ini $text;"

"%PHP_DIR%\php.exe" -v
"%PHP_DIR%\php.exe" -m | findstr /i "curl fileinfo gd mbstring mysqli openssl pdo_mysql"

echo [php] done.
endlocal

