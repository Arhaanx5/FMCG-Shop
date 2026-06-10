@echo off
:: Reinstall WhatsApp Service with LocalSystem account (no password needed)
cd /d "%~dp0"

echo ==========================================
echo Reinstalling WhatsApp Service (LocalSystem)
echo ==========================================

:: Stop old service if running
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service fmcg-whatsapp -Force -ErrorAction SilentlyContinue"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Sleep -Seconds 2"

:: Remove old registration
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" remove fmcg-whatsapp confirm 2>nul

:: Reinstall with correct settings
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" install fmcg-whatsapp "C:\Program Files\nodejs\node.exe" "D:\intelliJ2025\fmcg-shop\fmcg-shop\whatsapp-service\server.js"
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-whatsapp DisplayName "Lari Traders WhatsApp Service"
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-whatsapp Description "Headless WhatsApp Web service for automatic bulk reminders"
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-whatsapp AppDirectory "D:\intelliJ2025\fmcg-shop\fmcg-shop\whatsapp-service"
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-whatsapp ObjectName LocalSystem
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-whatsapp Start SERVICE_AUTO_START
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-whatsapp AppRestartDelay 5000
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-whatsapp AppStdout "D:\intelliJ2025\fmcg-shop\fmcg-shop\whatsapp-service\service.log"
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-whatsapp AppStderr "D:\intelliJ2025\fmcg-shop\fmcg-shop\whatsapp-service\service.log"

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
