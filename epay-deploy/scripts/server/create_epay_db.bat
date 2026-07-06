@echo off
setlocal
call "%~dp0_settings.bat"

set "SQL_FILE=%~dp0create_epay_db.sql"
if not exist "%SQL_FILE%" (
  echo [error] SQL file not found: %SQL_FILE%
  echo Copy create_epay_db.sql.template to create_epay_db.sql and edit the password first.
  exit /b 1
)

where mysql >nul 2>nul
if not %errorlevel%==0 (
  echo [error] mysql.exe not found in PATH.
  echo Open MySQL command line manually and run: source %SQL_FILE%
  exit /b 1
)

echo [db] running database setup. You may be prompted for the MySQL root password.
mysql -u root -p < "%SQL_FILE%"
endlocal

