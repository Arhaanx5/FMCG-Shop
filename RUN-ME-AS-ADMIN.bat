@echo off
:: ============================================================
:: ONE-CLICK: Stop → Build → Start (Run as Administrator)
:: ============================================================
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo =========================================================
    echo ERROR: Please Right-Click and "Run as Administrator"!
    echo =========================================================
    pause
    exit /b
)

cd /d "d:\intelliJ2025\fmcg-shop\fmcg-shop"

echo =========================================================
echo STEP 1/5: Stopping ALL Services...
echo =========================================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service fmcg-backend -ErrorAction SilentlyContinue"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service fmcg-whatsapp -ErrorAction SilentlyContinue"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service Cloudflared -ErrorAction SilentlyContinue"
echo All services stopped!

echo =========================================================
echo STEP 2/5: Building Frontend React App...
echo =========================================================
cd frontend
call npm run build
if %errorLevel% neq 0 (
    echo ERROR: Frontend build failed!
    cd ..
    pause
    exit /b
)
cd ..

echo =========================================================
echo STEP 3/5: Copying Build to Static Resources...
echo =========================================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Remove-Item -Recurse -Force src/main/resources/static/* -ErrorAction SilentlyContinue; Copy-Item -Recurse -Force frontend/dist/* src/main/resources/static/"

echo =========================================================
echo STEP 4/5: Packaging Backend JAR (Maven)...
echo =========================================================
call mvnw.cmd package -DskipTests
if %errorLevel% neq 0 (
    echo ERROR: Backend build failed!
    pause
    exit /b
)

echo =========================================================
echo STEP 5/5: Starting ALL Services...
echo =========================================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service fmcg-backend"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service fmcg-whatsapp"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service Cloudflared"

echo =========================================================
echo SUCCESS! All done - Website is LIVE!
echo =========================================================
pause
