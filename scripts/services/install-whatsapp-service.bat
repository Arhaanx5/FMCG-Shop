@echo off
:: Reinstall WhatsApp Service with LocalSystem account (no password needed)
cd /d "%~dp0..\.."

echo ==========================================
echo Reinstalling WhatsApp Service (LocalSystem)
echo ==========================================

:: Find nssm.exe
set "NSSM_PATH=C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe"
if not exist "%NSSM_PATH%" (
    where nssm >nul 2>&1
    if %errorlevel% equ 0 (
        set "NSSM_PATH=nssm"
    ) else (
        echo ERROR: nssm.exe could not be located.
        pause
        exit /b
    )
)

:: Stop old service if running
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service fmcg-whatsapp -Force -ErrorAction SilentlyContinue"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Sleep -Seconds 2"

:: Remove old registration
"%NSSM_PATH%" remove fmcg-whatsapp confirm 2>nul

:: Reinstall with correct settings
"%NSSM_PATH%" install fmcg-whatsapp "C:\Program Files\nodejs\node.exe" "%CD%\whatsapp-service\server.js"
"%NSSM_PATH%" set fmcg-whatsapp DisplayName "Lari Traders WhatsApp Service"
"%NSSM_PATH%" set fmcg-whatsapp Description "Headless WhatsApp Web service for automatic bulk reminders"
"%NSSM_PATH%" set fmcg-whatsapp AppDirectory "%CD%\whatsapp-service"
"%NSSM_PATH%" set fmcg-whatsapp ObjectName LocalSystem
"%NSSM_PATH%" set fmcg-whatsapp Start SERVICE_AUTO_START
"%NSSM_PATH%" set fmcg-whatsapp AppRestartDelay 5000
"%NSSM_PATH%" set fmcg-whatsapp AppStdout "%CD%\whatsapp-service\service.log"
"%NSSM_PATH%" set fmcg-whatsapp AppStderr "%CD%\whatsapp-service\service.log"

echo ==========================================
echo Starting WhatsApp Service...
echo ==========================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service fmcg-whatsapp -ErrorAction SilentlyContinue"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Sleep -Seconds 3"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-whatsapp | Format-Table Status, Name, DisplayName"
echo ==========================================
echo Done! Check service status above.
echo ==========================================
pause
