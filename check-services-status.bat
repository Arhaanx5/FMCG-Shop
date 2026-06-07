@echo off
echo =========================================================
echo Checking Lari Traders Services Status...
echo =========================================================
echo.

"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Get-Service fmcg-backend, fmcg-whatsapp, Cloudflared | Format-Table -Property Status, Name, DisplayName"

echo =========================================================
echo (Note: Status 'Running' means active, 'Stopped' means inactive)
echo =========================================================
echo.
pause
