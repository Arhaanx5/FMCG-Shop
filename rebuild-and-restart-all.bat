@echo off
echo ==========================================
echo 1. STOPPING ALL SERVICES...
echo ==========================================
powershell -Command "Stop-Service fmcg-backend -ErrorAction SilentlyContinue"
powershell -Command "Stop-Service Cloudflared -ErrorAction SilentlyContinue"
powershell -Command "Stop-Service fmcg-whatsapp -ErrorAction SilentlyContinue"

echo ==========================================
echo 2. ENSURING JAVA SERVICE PROCESS IS KILLED...
echo ==========================================
powershell -Command "Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.SessionId -eq 0 } | Stop-Process -Force -ErrorAction SilentlyContinue"

echo Waiting 3 seconds for file locks to release...
ping 127.0.0.1 -n 4 > nul

echo ==========================================
echo 3. PACKAGING BACKEND JAR...
echo ==========================================
cd /d "D:\intelliJ2025\fmcg-shop\fmcg-shop"
call mvnw.cmd package -DskipTests
if %errorLevel% neq 0 (
    echo ERROR: Backend compilation failed!
    pause
    exit /b
)

echo ==========================================
echo 4. STARTING ALL SERVICES...
echo ==========================================
powershell -Command "Start-Service fmcg-backend"
powershell -Command "Start-Service Cloudflared"
powershell -Command "Start-Service fmcg-whatsapp"

echo ==========================================
echo SUCCESS: All services compiled and restarted successfully!
echo ==========================================
pause
