@echo off
call "%~dp0stop_epay_php.bat"
timeout /t 1 /nobreak >nul
call "%~dp0start_epay_php.bat"

