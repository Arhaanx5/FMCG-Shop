@echo off
:: Check for Admin permissions
net session >nul 2>&1
if %errorLevel%==0 goto admin_ok
echo =========================================================
echo ERROR: Please Right-Click and select "Run as Administrator"!
echo =========================================================
pause
exit /b
:admin_ok

:: Go to the project root directory
cd /d "d:\intelliJ2025\fmcg-shop\fmcg-shop"

:: Check which services are registered using PowerShell exit codes (100% reliable, zero parentheses)
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-backend-uat, fmcg-backend-prod -ErrorAction Stop" >nul 2>&1
set parallel_installed=%errorlevel%

"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-backend -ErrorAction Stop" >nul 2>&1
set single_exists=%errorlevel%

"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-backend-uat -ErrorAction Stop" >nul 2>&1
set uat_exists=%errorlevel%

"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-backend-prod -ErrorAction Stop" >nul 2>&1
set prod_exists=%errorlevel%

:: If both parallel services exist, show menu
if %parallel_installed%==0 goto menu

:: Otherwise, start whichever exists (Single setup)
echo =========================================================
echo Starting Lari Traders Services...
echo =========================================================

if %single_exists%==0 echo 1. Starting Main Backend Service (fmcg-backend)...
if %single_exists%==0 "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service fmcg-backend -ErrorAction SilentlyContinue"

if %uat_exists%==0 echo 1. Starting UAT Backend Service (fmcg-backend-uat)...
if %uat_exists%==0 "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service fmcg-backend-uat -ErrorAction SilentlyContinue"

if %prod_exists%==0 echo 1. Starting PROD Backend Service (fmcg-backend-prod)...
if %prod_exists%==0 "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service fmcg-backend-prod -ErrorAction SilentlyContinue"

echo 2. Starting WhatsApp Helper Service (node server.js)...
start "Lari Traders WhatsApp Service" /min cmd /c "cd /d d:\intelliJ2025\fmcg-shop\fmcg-shop\whatsapp-service && node server.js"

echo 3. Starting Cloudflare Secure Tunnel (Cloudflared)...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service Cloudflared -ErrorAction SilentlyContinue"

:: Wait 3 seconds for services to initialize
echo Waiting 3 seconds for services to initialize...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Sleep -Seconds 3"

echo =========================================================
echo Current Services Status:
echo =========================================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-backend, fmcg-backend-uat, fmcg-backend-prod, fmcg-whatsapp, Cloudflared -ErrorAction SilentlyContinue | Format-Table -Property Status, Name, DisplayName"
echo =========================================================
pause
exit /b

:menu
cls
echo =========================================================
echo Lari Traders - Select Environment to Start
echo =========================================================
echo [1] uat  - Start UAT Environment (Backend Port 8085 + WA + Tunnel)
echo [2] prod - Start PROD Environment (Backend Port 8086 + WA + Tunnel)
echo [3] all  - Start BOTH UAT and PROD (All backends + WA + Tunnel)
echo [4] exit - Exit
echo =========================================================
set /p opt="Type environment (uat / prod / all / exit) or number: "

if /i "%opt%"=="uat" goto start_uat_opt
if "%opt%"=="1" goto start_uat_opt

if /i "%opt%"=="prod" goto start_prod_opt
if "%opt%"=="2" goto start_prod_opt

if /i "%opt%"=="all" goto start_all_opt
if "%opt%"=="3" goto start_all_opt

if /i "%opt%"=="exit" exit /b
if "%opt%"=="4" exit /b

echo Invalid option! Please type uat, prod, all, or exit.
pause
goto menu

:start_uat_opt
echo Starting UAT Backend, WhatsApp and Tunnel...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service fmcg-backend-uat, Cloudflared -ErrorAction SilentlyContinue"
start "Lari Traders WhatsApp Service" /min cmd /c "cd /d d:\intelliJ2025\fmcg-shop\fmcg-shop\whatsapp-service && node server.js"
echo Waiting 3 seconds...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Sleep -Seconds 3"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-backend-uat, fmcg-whatsapp, Cloudflared | Format-Table -Property Status, Name, DisplayName"
pause
exit /b

:start_prod_opt
echo Starting PROD Backend, WhatsApp and Tunnel...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service fmcg-backend-prod, Cloudflared -ErrorAction SilentlyContinue"
start "Lari Traders WhatsApp Service" /min cmd /c "cd /d d:\intelliJ2025\fmcg-shop\fmcg-shop\whatsapp-service && node server.js"
echo Waiting 3 seconds...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Sleep -Seconds 3"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-backend-prod, fmcg-whatsapp, Cloudflared | Format-Table -Property Status, Name, DisplayName"
pause
exit /b

:start_all_opt
echo Starting UAT, PROD, WhatsApp and Tunnel...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service fmcg-backend-uat, fmcg-backend-prod, Cloudflared -ErrorAction SilentlyContinue"
start "Lari Traders WhatsApp Service" /min cmd /c "cd /d d:\intelliJ2025\fmcg-shop\fmcg-shop\whatsapp-service && node server.js"
echo Waiting 3 seconds...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Sleep -Seconds 3"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-backend-uat, fmcg-backend-prod, fmcg-whatsapp, Cloudflared | Format-Table -Property Status, Name, DisplayName"
pause
exit /b
