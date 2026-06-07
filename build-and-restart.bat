@echo off
:: Check for Admin permissions
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ERROR: Please Right-Click and select "Run as Administrator"!
    pause
    exit /b
)

:: Change directory to the folder where this batch file is located
cd /d "%~dp0"

echo ==========================================
echo 1. BUILDING FRONTEND REACT APP...
echo ==========================================
cd frontend
call npm run build
if %errorLevel% neq 0 (
    echo ERROR: Frontend build failed!
    cd ..
    pause
    exit /b
)
cd ..

echo ==========================================
echo 2. COPYING BUILD TO STATIC RESOURCES...
echo ==========================================
:: Clean old assets and copy new ones using PowerShell to avoid xcopy or system path issues
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Remove-Item -Recurse -Force src/main/resources/static/* -ErrorAction SilentlyContinue; Copy-Item -Recurse -Force frontend/dist/* src/main/resources/static/"
if %errorLevel% neq 0 (
    echo ERROR: Copying build assets failed!
    pause
    exit /b
)

echo ==========================================
echo 3. STOPPING BACKEND SERVICE...
echo ==========================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service fmcg-backend"

echo ==========================================
echo 4. PACKAGING BACKEND JAR...
echo ==========================================
call mvnw.cmd package -DskipTests
if %errorLevel% neq 0 (
    echo ERROR: Backend compilation failed!
    pause
    exit /b
)

echo ==========================================
echo 5. STARTING BACKEND SERVICE...
echo ==========================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service fmcg-backend"

echo ==========================================
echo SUCCESS: Frontend and Backend built and restarted successfully!
echo ==========================================
pause
