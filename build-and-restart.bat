@echo off
:: Ensure standard Windows System32 paths are in the execution PATH
set PATH=%PATH%;C:\Windows\System32;C:\Windows\System32\WindowsPowerShell\v1.0\

:: Check for Administrator privileges
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo.
    echo [ERROR] Please close this and run it by right-clicking and selecting "Run as administrator"!
    echo.
    pause
    exit /b 1
)

:: Change working directory to the folder where this batch file is located
cd /d "%~dp0"

echo ========================================================
echo   Lari Traders (UAT) - Backend Rebuild & Restart Script
echo ========================================================
echo.

echo [1/4] Stopping fmcg-backend-uat service...
net stop fmcg-backend-uat
echo.

echo [2/4] Stopping fmcg-whatsapp service...
net stop fmcg-whatsapp
echo.

echo [3/4] Building new JAR package (skipping tests)...

:: Try maven wrapper first (CMD syntax)
call mvnw.cmd clean package -DskipTests

if %ERRORLEVEL% neq 0 (
    echo.
    echo [WARNING] Maven wrapper failed. Trying global maven installation...
    call mvn clean package -DskipTests
)

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build failed! Please check console errors.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [4/4] Starting services...
echo Starting fmcg-backend-uat service...
net start fmcg-backend-uat
echo Starting fmcg-whatsapp service...
net start fmcg-whatsapp

echo.
echo ========================================================
echo   [SUCCESS] Backend JAR built and services restarted!
echo ========================================================
echo.
pause
