@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul

rem DropAI backend launcher for a Windows server.
rem Put this file in the DropAI project root, then run it as Administrator if needed.

set "ROOT_DIR=%~dp0"
set "BACKEND_DIR=%ROOT_DIR%backend"
set "JAR_FILE=%BACKEND_DIR%\target\academic-rewrite-backend-0.0.1-SNAPSHOT.jar"
set "ENV_FILE=%ROOT_DIR%.env"
set "BACKEND_ENV_FILE=%BACKEND_DIR%\.env"
set "BACKEND_YML=%BACKEND_DIR%\application.yml"

echo ========================================
echo  DropAI Backend Starting
echo ========================================
echo Root:    %ROOT_DIR%
echo Backend: %BACKEND_DIR%
echo.

if not exist "%BACKEND_DIR%\" (
  echo [ERROR] Backend directory not found: %BACKEND_DIR%
  pause
  exit /b 1
)

cd /d "%ROOT_DIR%"

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java is not available in PATH.
  echo Install JDK/JRE 17 and try again.
  pause
  exit /b 1
)

where python >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Python is not available in PATH.
  pause
  exit /b 1
)

if not exist "%ROOT_DIR%diagram-worker\web_engine.py" (
  echo [ERROR] Diagram worker not found: %ROOT_DIR%diagram-worker\web_engine.py
  pause
  exit /b 1
)

python "%ROOT_DIR%diagram-worker\web_engine.py" --self-test >nul 2>nul
if errorlevel 1 (
  echo [ERROR] PNG/VSDX export self-test failed.
  echo Run: python -m pip install -r "%ROOT_DIR%diagram-worker\requirements-web.txt"
  echo Then run: python "%ROOT_DIR%diagram-worker\web_engine.py" --self-test
  pause
  exit /b 1
)

if not exist "%JAR_FILE%" (
  echo [ERROR] Jar not found:
  echo %JAR_FILE%
  echo.
  echo Build it first:
  echo cd /d "%BACKEND_DIR%"
  echo mvn -DskipTests package
  pause
  exit /b 1
)

rem Defaults. Values in .env override these.
set "SERVER_PORT=8080"
set "SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/drop_ai?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
set "SPRING_DATASOURCE_USERNAME=root"
set "SPRING_DATASOURCE_PASSWORD=204828"

set "DOUBAO_API_KEY="
set "DOUBAO_MODEL=doubao-seed-2-0-lite-260428"
set "DOUBAO_TEXT_MODEL=doubao-seed-2-0-lite-260428"
set "DOUBAO_VISION_MODEL="
set "DOUBAO_MECHANICAL_VISION_MODEL=doubao-seed-2-1-turbo-260628"
set "DOUBAO_ENDPOINT=https://ark.cn-beijing.volces.com/api/v3/chat/completions"
set "DOUBAO_DOCUMENT_CONCURRENCY=8"
set "DOUBAO_MAX_RETRIES=2"
set "DOUBAO_WEB_SEARCH_ENABLED=true"
set "DOUBAO_WEB_SEARCH_TIMEOUT_SECONDS=20"
set "WRITING_REFERENCE_SEARCH_ENABLED=true"
set "WRITING_REFERENCE_SEARCH_PROVIDER=doubao_web,openalex,crossref"
set "WRITING_REFERENCE_SEARCH_TIMEOUT_SECONDS=20"
set "WRITING_REFERENCE_SEARCH_RETRY_COUNT=1"
set "WRITING_REFERENCE_SEARCH_PUBLIC_FALLBACK_ENABLED=true"
set "WRITING_REFERENCE_SEARCH_PARALLELISM=8"

set "WORD_FORMAT_ENABLED=true"
set "WORD_FORMAT_PYTHON=python"
set "WORD_FORMAT_WORKER=%ROOT_DIR%document-format-tool\format_cli.py"
set "WORD_FORMAT_DATA_DIR=%BACKEND_DIR%\storage\word-format"
set "WORD_FORMAT_LEGACY_TEMPLATES_ENABLED=true"
set "DOKIAI_TOMCAT_NIO2_ENABLED=false"

set "MATRIX_API_KEY="
set "MATRIX_MODEL=claude-opus-4-7"
set "MATRIX_DESIGN_ENABLED=true"

set "APP_BASE_URL=https://dro.k8818.cn"
set "EPAY_GATEWAY=https://pay.dropai-demo.com/submit.php"
set "EPAY_PID=1000"
set "EPAY_KEY=T451e2G86pJlG2tcePGe41jpzMprQ14b"
set "EPAY_NOTIFY_URL=https://dro.k8818.cn/api/recharge/notify"
set "EPAY_RETURN_URL=https://dro.k8818.cn/recharge"
set "EPAY_SITE_NAME=DropAI"
set "EPAY_DEFAULT_TYPE=alipay"
set "POI_MIN_INFLATE_RATIO=0.001"
set "JAVA_OPTS=-Xms512m -Xmx1024m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

call :load_env "%ENV_FILE%"
call :load_env "%BACKEND_ENV_FILE%"

if "%WORD_FORMAT_ENABLED%"=="" set "WORD_FORMAT_ENABLED=true"
if "%WORD_FORMAT_PYTHON%"=="" set "WORD_FORMAT_PYTHON=python"
if "%WORD_FORMAT_WORKER%"=="" set "WORD_FORMAT_WORKER=%ROOT_DIR%document-format-tool\format_cli.py"
if "%WORD_FORMAT_DATA_DIR%"=="" set "WORD_FORMAT_DATA_DIR=%BACKEND_DIR%\storage\word-format"
if "%WORD_FORMAT_LEGACY_TEMPLATES_ENABLED%"=="" set "WORD_FORMAT_LEGACY_TEMPLATES_ENABLED=true"

if /i "%WORD_FORMAT_ENABLED%"=="true" (
  call :verify_word_formatter
  if errorlevel 1 (
    pause
    exit /b 1
  )
)

if "%DOUBAO_API_KEY%"=="" (
  echo [ERROR] DOUBAO_API_KEY is empty.
  echo Set it in "%ENV_FILE%" or "%BACKEND_ENV_FILE%".
  pause
  exit /b 1
)

if /i "%DOUBAO_API_KEY%"=="PLEASE_SET_YOUR_DOUBAO_API_KEY" (
  echo [ERROR] DOUBAO_API_KEY is still a placeholder.
  pause
  exit /b 1
)

cd /d "%BACKEND_DIR%"

set "SPRING_CONFIG_ARG="
if exist "%BACKEND_YML%" (
  set "SPRING_CONFIG_ARG=--spring.config.additional-location=file:%BACKEND_YML%"
  echo External config: %BACKEND_YML%
)

echo.
echo Java:
java -version
echo.
echo Effective runtime:
echo   SERVER_PORT=%SERVER_PORT%
echo   SPRING_DATASOURCE_USERNAME=%SPRING_DATASOURCE_USERNAME%
echo   DOUBAO_MODEL=%DOUBAO_MODEL%
echo   DOUBAO_TEXT_MODEL=%DOUBAO_TEXT_MODEL%
echo   DOUBAO_MECHANICAL_VISION_MODEL=%DOUBAO_MECHANICAL_VISION_MODEL%
echo   DOUBAO_ENDPOINT=%DOUBAO_ENDPOINT%
echo   DOUBAO_DOCUMENT_CONCURRENCY=%DOUBAO_DOCUMENT_CONCURRENCY%
echo   WORD_FORMAT_ENABLED=%WORD_FORMAT_ENABLED%
echo   WORD_FORMAT_PYTHON=%WORD_FORMAT_PYTHON%
echo   WORD_FORMAT_WORKER=%WORD_FORMAT_WORKER%
echo   WORD_FORMAT_LEGACY_TEMPLATES_ENABLED=%WORD_FORMAT_LEGACY_TEMPLATES_ENABLED%
echo   MATRIX_DESIGN_ENABLED=%MATRIX_DESIGN_ENABLED%
echo   EPAY_GATEWAY=%EPAY_GATEWAY%
echo   EPAY_PID=%EPAY_PID%
echo   EPAY_NOTIFY_URL=%EPAY_NOTIFY_URL%
echo   EPAY_RETURN_URL=%EPAY_RETURN_URL%
echo.
echo Starting Spring Boot...
echo Stop with Ctrl+C.
echo.

java %JAVA_OPTS% -jar "%JAR_FILE%" %SPRING_CONFIG_ARG%
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo ========================================
echo  DropAI Backend Stopped, exit code %EXIT_CODE%
echo ========================================
pause
exit /b %EXIT_CODE%

:verify_word_formatter
set "WORD_FORMAT_WORKER_PATH="
if exist "%WORD_FORMAT_WORKER%" for %%I in ("%WORD_FORMAT_WORKER%") do set "WORD_FORMAT_WORKER_PATH=%%~fI"
if not defined WORD_FORMAT_WORKER_PATH if exist "%ROOT_DIR%%WORD_FORMAT_WORKER%" for %%I in ("%ROOT_DIR%%WORD_FORMAT_WORKER%") do set "WORD_FORMAT_WORKER_PATH=%%~fI"
if not defined WORD_FORMAT_WORKER_PATH if exist "%BACKEND_DIR%\%WORD_FORMAT_WORKER%" for %%I in ("%BACKEND_DIR%\%WORD_FORMAT_WORKER%") do set "WORD_FORMAT_WORKER_PATH=%%~fI"
if defined WORD_FORMAT_WORKER_PATH goto :word_worker_found
echo [ERROR] Word formatter not found: %WORD_FORMAT_WORKER%
exit /b 1

:word_worker_found
set "WORD_FORMAT_WORKER=%WORD_FORMAT_WORKER_PATH%"
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
"%WORD_FORMAT_PYTHON%" "%WORD_FORMAT_RUNTIME_CHECK%" >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Word formatter Python runtime is incomplete: %WORD_FORMAT_PYTHON%
  echo Run: "%WORD_FORMAT_PYTHON%" -m pip install -r "%WORD_FORMAT_REQUIREMENTS%"
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

:load_env
set "FILE=%~1"
if not exist "%FILE%" exit /b 0
echo Loading env: %FILE%
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%FILE%") do (
  if not "%%A"=="" (
    set "%%A=%%B"
  )
)
exit /b 0
