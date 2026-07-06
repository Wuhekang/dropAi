@echo off
set "EPAY_REPO=https://github.com/lopinx/epay.git"
set "EPAY_ZIP=https://github.com/lopinx/epay/archive/refs/heads/master.zip"
set "BASE_DIR=%~dp0..\.."
for %%I in ("%BASE_DIR%") do set "BASE_DIR=%%~fI"
set "WORK_DIR=%BASE_DIR%\work"
set "SRC_DIR=%WORK_DIR%\epay-source"
set "DIST_DIR=%BASE_DIR%\dist"
set "PKG_FILE=%DIST_DIR%\epay-package.zip"
set "SERVER_ROOT=C:\epay"
set "EPAY_DOMAIN=dropai-demo.cn"
set "PHP_FCGI_PORT=9074"

