@echo off
:: Check for Admin permissions
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ERROR: Please Right-Click and select "Run as Administrator"!
    pause
    exit /b
)

echo Stopping Backend Service...
powershell -Command "Stop-Service fmcg-backend"

echo Switching profile to PROD...
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-backend AppParameters "-jar fmcg-shop-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod"

echo Starting Backend Service...
powershell -Command "Start-Service fmcg-backend"

echo SUCCESS: Profile switched to PROD successfully!
pause
