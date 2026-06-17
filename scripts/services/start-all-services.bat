@echo off
:: Check for Admin permissions bypassed

:: Go to the project root directory
cd /d "%~dp0..\.."

:: Check which services are registered using sc.exe (100% reliable, native, no PowerShell)
set parallel_installed=1
"%SystemRoot%\System32\sc.exe" query fmcg-backend-uat >nul 2>&1
set uat_exists=%errorlevel%
"%SystemRoot%\System32\sc.exe" query fmcg-backend-prod >nul 2>&1
set prod_exists=%errorlevel%
if %uat_exists%==0 if %prod_exists%==0 set parallel_installed=0

"%SystemRoot%\System32\sc.exe" query fmcg-backend >nul 2>&1
set single_exists=%errorlevel%

:: If both parallel services exist, show menu
if %parallel_installed%==0 goto menu

:: Otherwise, start whichever exists (Single setup)
echo =========================================================
echo Starting Lari Traders Services...
echo =========================================================

if %single_exists%==0 echo 1. Starting Main Backend Service (fmcg-backend)...
if %single_exists%==0 "%SystemRoot%\System32\net.exe" start fmcg-backend >nul 2>&1

if %uat_exists%==0 echo 1. Starting UAT Backend Service (fmcg-backend-uat)...
if %uat_exists%==0 "%SystemRoot%\System32\net.exe" start fmcg-backend-uat >nul 2>&1

if %prod_exists%==0 echo 1. Starting PROD Backend Service (fmcg-backend-prod)...
if %prod_exists%==0 "%SystemRoot%\System32\net.exe" start fmcg-backend-prod >nul 2>&1

echo 2. Checking WhatsApp Helper Service (fmcg-whatsapp)...
"%SystemRoot%\System32\sc.exe" query fmcg-whatsapp >nul 2>&1
if not "%errorlevel%"=="0" goto no_wa_service_1
echo Starting WhatsApp Helper Service fmcg-whatsapp...
"%SystemRoot%\System32\net.exe" start fmcg-whatsapp >nul 2>&1
goto wa_done_1

:no_wa_service_1
"%SystemRoot%\System32\netstat.exe" -ano | "%SystemRoot%\System32\findstr.exe" ":3000 " >nul 2>&1
if "%errorlevel%"=="0" goto wa_run_1
echo Starting WhatsApp Helper Service node server.js...
pushd "%~dp0..\..\whatsapp-service" && start "Lari Traders WhatsApp Service" /min "%SystemRoot%\System32\cmd.exe" /c "node server.js" && popd
goto wa_done_1

:wa_run_1
echo WhatsApp Service already running on port 3000. Skipping launch.

:wa_done_1

echo 3. Starting Cloudflare Secure Tunnel (Cloudflared)...
"%SystemRoot%\System32\net.exe" start Cloudflared >nul 2>&1

:: Wait 3 seconds for services to initialize
echo Waiting 3 seconds for services to initialize...
"%SystemRoot%\System32\ping.exe" 127.0.0.1 -n 4 >nul

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
echo Starting UAT Environment...
echo [1/4] Starting UAT Backend Service...
"%SystemRoot%\System32\net.exe" start fmcg-backend-uat >nul 2>&1
echo [2/4] Starting Cloudflared Service...
"%SystemRoot%\System32\net.exe" start Cloudflared >nul 2>&1
echo [3/4] Checking WhatsApp Service (fmcg-whatsapp)...
"%SystemRoot%\System32\sc.exe" query fmcg-whatsapp >nul 2>&1
if not "%errorlevel%"=="0" goto no_wa_service_uat
echo [4/4] Starting WhatsApp Helper Service fmcg-whatsapp...
"%SystemRoot%\System32\net.exe" start fmcg-whatsapp >nul 2>&1
goto wa_done_uat

:no_wa_service_uat
"%SystemRoot%\System32\netstat.exe" -ano | "%SystemRoot%\System32\findstr.exe" ":3000 " >nul 2>&1
if "%errorlevel%"=="0" goto wa_run_uat
echo [4/4] Starting WhatsApp Service node server.js...
pushd "%~dp0..\..\whatsapp-service" && start "Lari Traders WhatsApp Service" /min "%SystemRoot%\System32\cmd.exe" /c "node server.js" && popd
goto wa_done_uat

:wa_run_uat
echo [4/4] WhatsApp Service is already running. Skipping.

:wa_done_uat
echo Waiting 3 seconds for services to initialize...
"%SystemRoot%\System32\ping.exe" 127.0.0.1 -n 4 >nul
echo =========================================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-backend-uat, fmcg-whatsapp, Cloudflared | Format-Table -Property Status, Name, DisplayName"
pause
exit /b

:start_prod_opt
echo Starting PROD Environment...
echo [1/4] Starting PROD Backend Service...
"%SystemRoot%\System32\net.exe" start fmcg-backend-prod >nul 2>&1
echo [2/4] Starting Cloudflared Service...
"%SystemRoot%\System32\net.exe" start Cloudflared >nul 2>&1
echo [3/4] Checking WhatsApp Service (fmcg-whatsapp)...
"%SystemRoot%\System32\sc.exe" query fmcg-whatsapp >nul 2>&1
if not "%errorlevel%"=="0" goto no_wa_service_prod
echo [4/4] Starting WhatsApp Helper Service fmcg-whatsapp...
"%SystemRoot%\System32\net.exe" start fmcg-whatsapp >nul 2>&1
goto wa_done_prod

:no_wa_service_prod
"%SystemRoot%\System32\netstat.exe" -ano | "%SystemRoot%\System32\findstr.exe" ":3000 " >nul 2>&1
if "%errorlevel%"=="0" goto wa_run_prod
echo [4/4] Starting WhatsApp Service node server.js...
pushd "%~dp0..\..\whatsapp-service" && start "Lari Traders WhatsApp Service" /min "%SystemRoot%\System32\cmd.exe" /c "node server.js" && popd
goto wa_done_prod

:wa_run_prod
echo [4/4] WhatsApp Service is already running. Skipping.

:wa_done_prod
echo Waiting 3 seconds for services to initialize...
"%SystemRoot%\System32\ping.exe" 127.0.0.1 -n 4 >nul
echo =========================================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-backend-prod, fmcg-whatsapp, Cloudflared | Format-Table -Property Status, Name, DisplayName"
pause
exit /b

:start_all_opt
echo Starting BOTH UAT and PROD Environments...
echo [1/5] Starting UAT Backend Service...
"%SystemRoot%\System32\net.exe" start fmcg-backend-uat >nul 2>&1
echo [2/5] Starting PROD Backend Service...
"%SystemRoot%\System32\net.exe" start fmcg-backend-prod >nul 2>&1
echo [3/5] Starting Cloudflared Service...
"%SystemRoot%\System32\net.exe" start Cloudflared >nul 2>&1
echo [4/5] Checking WhatsApp Service (fmcg-whatsapp)...
"%SystemRoot%\System32\sc.exe" query fmcg-whatsapp >nul 2>&1
if not "%errorlevel%"=="0" goto no_wa_service_all
echo [5/5] Starting WhatsApp Helper Service fmcg-whatsapp...
"%SystemRoot%\System32\net.exe" start fmcg-whatsapp >nul 2>&1
goto wa_done_all

:no_wa_service_all
"%SystemRoot%\System32\netstat.exe" -ano | "%SystemRoot%\System32\findstr.exe" ":3000 " >nul 2>&1
if "%errorlevel%"=="0" goto wa_run_all
echo [5/5] Starting WhatsApp Helper Service node server.js...
pushd "%~dp0..\..\whatsapp-service" && start "Lari Traders WhatsApp Service" /min "%SystemRoot%\System32\cmd.exe" /c "node server.js" && popd
goto wa_done_all

:wa_run_all
echo [5/5] WhatsApp Service is already running. Skipping.

:wa_done_all
echo Waiting 3 seconds for services to initialize...
"%SystemRoot%\System32\ping.exe" 127.0.0.1 -n 4 >nul
echo =========================================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-backend-uat, fmcg-backend-prod, fmcg-whatsapp, Cloudflared | Format-Table -Property Status, Name, DisplayName"
pause
exit /b
