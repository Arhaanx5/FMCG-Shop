@echo off
set PATH=%SystemRoot%\System32;%SystemRoot%;%SystemRoot%\System32\Wbem;%PATH%
:: Check for Admin permissions bypassed
goto admin_ok

:admin_ok
:: Go to the project root directory
cd /d "%~dp0"

:: Detect service name based on directory name
set SERVICE_NAME=fmcg-backend-uat
echo %~dp0 | findstr /i "fmcg-shop-prod" >nul
if %errorLevel% equ 0 set SERVICE_NAME=fmcg-backend-prod

:: Update Android and Capacitor environment configurations dynamically based on environment
if "%SERVICE_NAME%"=="fmcg-backend-prod" "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -Command "& '%~dp0update-android-env.ps1' prod"
if "%SERVICE_NAME%"=="fmcg-backend-uat" "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -Command "& '%~dp0update-android-env.ps1' uat"

echo ==========================================
echo Project Directory: %~dp0
echo Service Name:      %SERVICE_NAME%
echo ==========================================

echo ==========================================
echo 1. BUILDING FRONTEND REACT APP...
echo ==========================================
cd frontend
call npm run build
if %errorLevel% neq 0 goto build_fail
cd ..
goto build_ok

:build_fail
echo ERROR: Frontend build failed!
cd ..
pause
exit /b

:build_ok
echo ==========================================
echo 2. COPYING BUILD TO STATIC RESOURCES...
echo ==========================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Remove-Item -Recurse -Force src/main/resources/static/* -ErrorAction SilentlyContinue; Copy-Item -Recurse -Force frontend/dist/* src/main/resources/static/"
if %errorLevel% neq 0 goto copy_fail
goto copy_ok

:copy_fail
echo ERROR: Copying build assets failed!
pause
exit /b

:copy_ok
echo ==========================================
echo 3. STOPPING BACKEND SERVICE...
echo ==========================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service %SERVICE_NAME% -ErrorAction SilentlyContinue"

echo ==========================================
echo 4. PACKAGING BACKEND JAR...
echo ==========================================
call mvnw.cmd clean package -DskipTests
if %errorLevel% neq 0 goto package_fail
goto package_ok

:package_fail
echo ERROR: Backend compilation failed!
pause
exit /b

:package_ok
:: Copy Google Drive key to target folder if it exists
if exist google-drive-key.json (
    echo Copying google-drive-key.json to target...
    copy /y google-drive-key.json target\ >nul
)
echo ==========================================
echo 5. STARTING BACKEND SERVICE...
echo ==========================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service %SERVICE_NAME% -ErrorAction SilentlyContinue"

echo ==========================================
echo SUCCESS: Frontend and Backend built and restarted successfully!
echo ==========================================
pause
