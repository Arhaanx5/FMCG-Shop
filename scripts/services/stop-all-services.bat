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
cd /d "%~dp0..\.."

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

:: Otherwise, stop whichever exists (Single setup)
echo =========================================================
echo Stopping Lari Traders Services...
echo =========================================================

if %single_exists%==0 echo 1. Stopping Main Backend Service (fmcg-backend)...
if %single_exists%==0 "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service fmcg-backend -ErrorAction SilentlyContinue"

if %uat_exists%==0 echo 1. Stopping UAT Backend Service (fmcg-backend-uat)...
if %uat_exists%==0 "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service fmcg-backend-uat -ErrorAction SilentlyContinue"

if %prod_exists%==0 echo 1. Stopping PROD Backend Service (fmcg-backend-prod)...
if %prod_exists%==0 "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service fmcg-backend-prod -ErrorAction SilentlyContinue"

echo 2. Stopping WhatsApp Helper Service (fmcg-whatsapp)...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service fmcg-whatsapp -ErrorAction SilentlyContinue"

echo 3. Stopping Cloudflare Secure Tunnel (Cloudflared)...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service Cloudflared -ErrorAction SilentlyContinue"

:: Clean up ports and orphaned processes
echo =========================================================
echo Cleaning up ports and orphaned processes...
echo =========================================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-NetTCPConnection -LocalPort 3000, 8080, 8085, 8086 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }; Get-Process -Name cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue; Get-CimInstance Win32_Process -Filter \"Name = 'chrome.exe'\" | Where-Object { $_.CommandLine -like '*session_data*' -or $_.CommandLine -like '*whatsapp-service*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"

echo =========================================================
echo SUCCESS: All services stopped and cleaned successfully!
echo =========================================================
pause
exit /b

:menu
cls
echo =========================================================
echo Lari Traders - Select Environment to Stop
echo =========================================================
echo [1] uat  - Stop UAT Environment (Backend Port 8085 + WA + Tunnel)
echo [2] prod - Stop PROD Environment (Backend Port 8086 + WA + Tunnel)
echo [3] all  - Stop BOTH UAT and PROD (All backends + WA + Tunnel)
echo [4] exit - Exit
echo =========================================================
set /p opt="Type environment (uat / prod / all / exit) or number: "

if /i "%opt%"=="uat" goto stop_uat_opt
if "%opt%"=="1" goto stop_uat_opt

if /i "%opt%"=="prod" goto stop_prod_opt
if "%opt%"=="2" goto stop_prod_opt

if /i "%opt%"=="all" goto stop_all_opt
if "%opt%"=="3" goto stop_all_opt

if /i "%opt%"=="exit" exit /b
if "%opt%"=="4" exit /b

echo Invalid option! Please type uat, prod, all, or exit.
pause
goto menu

:stop_uat_opt
echo Stopping UAT Environment Services (Backend-UAT, WhatsApp, Tunnel)...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service fmcg-backend-uat, fmcg-whatsapp, Cloudflared -ErrorAction SilentlyContinue; Get-NetTCPConnection -LocalPort 8085, 3000 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }; Get-Process -Name cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue; Get-CimInstance Win32_Process -Filter \"Name = 'chrome.exe'\" | Where-Object { $_.CommandLine -like '*session_data*' -or $_.CommandLine -like '*whatsapp-service*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
echo Done!
pause
exit /b

:stop_prod_opt
echo Stopping PROD Environment Services (Backend-PROD, WhatsApp, Tunnel)...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service fmcg-backend-prod, fmcg-whatsapp, Cloudflared -ErrorAction SilentlyContinue; Get-NetTCPConnection -LocalPort 8086, 3000 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }; Get-Process -Name cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue; Get-CimInstance Win32_Process -Filter \"Name = 'chrome.exe'\" | Where-Object { $_.CommandLine -like '*session_data*' -or $_.CommandLine -like '*whatsapp-service*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
echo Done!
pause
exit /b

:stop_all_opt
echo Stopping ALL Environment Services (UAT, PROD, WhatsApp, Tunnel)...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service fmcg-backend-uat, fmcg-backend-prod, fmcg-whatsapp, Cloudflared -ErrorAction SilentlyContinue; Get-NetTCPConnection -LocalPort 3000, 8085, 8086 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }; Get-Process -Name cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue; Get-CimInstance Win32_Process -Filter \"Name = 'chrome.exe'\" | Where-Object { $_.CommandLine -like '*session_data*' -or $_.CommandLine -like '*whatsapp-service*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
echo Done!
pause
exit /b
