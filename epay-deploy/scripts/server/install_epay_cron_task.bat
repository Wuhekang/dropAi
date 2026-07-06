@echo off
setlocal
call "%~dp0_settings.bat"

set "TASK_NAME=EpayCron"
set "TASK_CMD=%~dp0run_epay_cron.bat"

echo [task] creating Windows scheduled task: %TASK_NAME%
schtasks /Create /F /SC MINUTE /MO 1 /TN "%TASK_NAME%" /TR "\"%TASK_CMD%\"" /RL HIGHEST

echo [task] created. To test now:
echo schtasks /Run /TN "%TASK_NAME%"
endlocal

