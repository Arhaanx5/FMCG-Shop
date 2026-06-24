@echo off
:: Ensure standard Windows System32 paths are in the execution PATH
set PATH=%PATH%;C:\Windows\System32;C:\Windows\System32\WindowsPowerShell\v1.0\

:: Check for Administrator privileges
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo.
    echo ======================================================================
    echo [ERROR] This script requires Administrator privileges to manage services.
    echo Please right-click this file and select "Run as administrator".
    echo ======================================================================
    echo.
    pause
    exit /b 1
)

echo ========================================================
echo   Lari Traders - Rebuild & Restart BOTH UAT & PROD
echo ========================================================
echo.

echo [1/5] Stopping all active services...
echo Stopping fmcg-backend-prod...
net stop fmcg-backend-prod
echo Stopping fmcg-backend-uat...
net stop fmcg-backend-uat
echo Stopping fmcg-whatsapp...
net stop fmcg-whatsapp
echo.

echo [2/5] Rebuilding UAT Backend (fmcg-shop)...
cd /d "d:\intelliJ2025\fmcg-shop\fmcg-shop"
call mvnw.cmd clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo [ERROR] UAT Backend build failed!
    goto BUILD_FAILED
)
echo.

echo [3/5] Rebuilding PROD Backend (fmcg-shop-prod)...
cd /d "d:\intelliJ2025\fmcg-shop\fmcg-shop-prod"
call mvnw.cmd clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo [ERROR] PROD Backend build failed!
    goto BUILD_FAILED
)
echo.

echo [4/5] Starting services...
echo Starting fmcg-whatsapp...
net start fmcg-whatsapp
echo Starting fmcg-backend-uat...
net start fmcg-backend-uat
echo Starting fmcg-backend-prod...
net start fmcg-backend-prod

echo.
echo ========================================================
echo   [SUCCESS] Both UAT and PROD backends rebuilt and restarted!
echo ========================================================
echo.
pause
exit /b 0

:BUILD_FAILED
echo.
echo ========================================================
echo   [ERROR] Rebuild failed! Services are kept stopped.
echo   Please resolve compilation errors and try again.
echo ========================================================
echo.
pause
exit /b 1
