@echo off
:: Check for Admin permissions
net session >nul 2>&1
if %errorLevel% equ 0 goto admin_ok
echo =========================================================
echo ERROR: Please Right-Click and select "Run as Administrator"!
echo =========================================================
pause
exit /b
:admin_ok

"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Expression -Command (Get-Content -Path '%~f0' | Select-Object -Skip 14 | Out-String)"
pause
exit /b

$uat = Get-Service fmcg-backend-uat -ErrorAction SilentlyContinue
$prod = Get-Service fmcg-backend-prod -ErrorAction SilentlyContinue

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "          LARI TRADERS SERVICES RECOVERY & LOGS          " -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan

if ($uat) {
    $status = $uat.Status
    $color = if ($status -eq "Running") { "Green" } else { "Red" }
    Write-Host "  UAT Service Status  : " -NoNewline
    Write-Host $status -ForegroundColor $color
} else {
    Write-Host "  UAT Service Status  : NOT INSTALLED" -ForegroundColor Yellow
}

if ($prod) {
    $status = $prod.Status
    $color = if ($status -eq "Running") { "Green" } else { "Red" }
    Write-Host "  PROD Service Status : " -NoNewline
    Write-Host $status -ForegroundColor $color
} else {
    Write-Host "  PROD Service Status : NOT INSTALLED" -ForegroundColor Yellow
}
Write-Host "---------------------------------------------------------"

$restartUat = "n"
if ($uat) {
    if ($uat.Status -ne "Running") {
        Write-Host "  [!] UAT Service is stuck or NOT running properly (Status: $($uat.Status))" -ForegroundColor Red
        $restartUat = Read-Host "  -> Do you want to recover/restart UAT? (y/n)"
    } else {
        $restartUat = Read-Host "  -> UAT is running fine. Do you still want to restart it? (y/n)"
    }
}

$restartProd = "n"
if ($prod) {
    if ($prod.Status -ne "Running") {
        Write-Host "  [!] PROD Service is stuck or NOT running properly (Status: $($prod.Status))" -ForegroundColor Red
        $restartProd = Read-Host "  -> Do you want to recover/restart PROD? (y/n)"
    } else {
        $restartProd = Read-Host "  -> PROD is running fine. Do you still want to restart it? (y/n)"
    }
}

Write-Host "---------------------------------------------------------" -ForegroundColor Cyan

if ($restartUat -eq "y" -or $restartUat -eq "Y") {
    Write-Host "  Configuring log files for UAT..." -ForegroundColor Yellow
    & "C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-backend-uat AppStdout "D:\intelliJ2025\fmcg-shop\fmcg-shop\target\backend-stdout.log"
    & "C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-backend-uat AppStderr "D:\intelliJ2025\fmcg-shop\fmcg-shop\target\backend-stderr.log"
    Write-Host "  Restarting UAT Service (fmcg-backend-uat)..." -ForegroundColor Yellow
    Restart-Service fmcg-backend-uat
    Write-Host "  [+] UAT Backend started successfully!" -ForegroundColor Green
} else {
    Write-Host "  [-] Skipped UAT restart." -ForegroundColor Gray
}

if ($restartProd -eq "y" -or $restartProd -eq "Y") {
    Write-Host "  Configuring log files for PROD..." -ForegroundColor Yellow
    & "C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-backend-prod AppStdout "D:\intelliJ2025\fmcg-shop\fmcg-shop-prod\target\backend-stdout.log"
    & "C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-backend-prod AppStderr "D:\intelliJ2025\fmcg-shop\fmcg-shop-prod\target\backend-stderr.log"
    Write-Host "  Restarting PROD Service (fmcg-backend-prod)..." -ForegroundColor Yellow
    Restart-Service fmcg-backend-prod
    Write-Host "  [+] PROD Backend started successfully!" -ForegroundColor Green
} else {
    Write-Host "  [-] Skipped PROD restart." -ForegroundColor Gray
}

Write-Host "=========================================================" -ForegroundColor Cyan
