@echo off
set PATH=%SystemRoot%\System32;%SystemRoot%;%SystemRoot%\System32\Wbem;%PATH%

echo =========================================================
echo          LARI TRADERS WHATSAPP SERVICE RESET
echo =========================================================

echo 1. Stopping any process running on port 3000...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :3000 ^| findstr LISTENING') do (
    echo Found process %%a on port 3000, killing...
    taskkill /F /PID %%a
)

echo 1.5. Killing any locked Puppeteer Chrome processes...
powershell -Command "Get-CimInstance Win32_Process -Filter \"Name = 'chrome.exe'\" | Where-Object { $_.CommandLine -like '*puppeteer*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"

echo 2. Deleting cached session data (session_data)...
if exist "%~dp0whatsapp-service\session_data" (
    rmdir /S /Q "%~dp0whatsapp-service\session_data"
    echo Session cache cleared!
) else (
    echo No session cache found.
)

echo 3. Starting WhatsApp Helper Service...
start "Lari Traders WhatsApp Service" /min cmd /c "cd /d %~dp0whatsapp-service && node server.js"

echo =========================================================
echo RESET COMPLETED! Please refresh your browser and scan the new QR code.
echo =========================================================
pause
