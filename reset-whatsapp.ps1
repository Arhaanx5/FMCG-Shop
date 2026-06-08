$env:Path = "C:\Windows\system32;C:\Windows;C:\Windows\System32\Wbem;" + $env:Path

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "          LARI TRADERS WHATSAPP SERVICE RESET" -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan

# 1. Kill any process running on port 3000
Write-Host "1. Stopping any process running on port 3000..." -ForegroundColor Yellow
$connections = Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue
if ($connections) {
    $pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($portPid in $pids) {
        Write-Host "Killing process $portPid on port 3000..." -ForegroundColor Gray
        taskkill /F /PID $portPid
    }
} else {
    Write-Host "No process running on port 3000." -ForegroundColor Gray
}

# 1.5. Kill any locked Puppeteer Chrome processes
Write-Host "1.5. Killing any locked Puppeteer Chrome processes..." -ForegroundColor Yellow
$chromeProcesses = Get-CimInstance Win32_Process -Filter "Name = 'chrome.exe'" -ErrorAction SilentlyContinue
if ($chromeProcesses) {
    $puppeteerChromes = $chromeProcesses | Where-Object { $_.CommandLine -like "*puppeteer*" }
    if ($puppeteerChromes) {
        foreach ($proc in $puppeteerChromes) {
            Write-Host "Killing locked Chrome process $($proc.ProcessId)..." -ForegroundColor Gray
            Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
        }
    } else {
        Write-Host "No locked Puppeteer Chrome processes found." -ForegroundColor Gray
    }
}

# 2. Delete cached session data
Write-Host "2. Deleting cached session data (session_data)..." -ForegroundColor Yellow
$sessionDataPath = Join-Path $PSScriptRoot "whatsapp-service\session_data"
if (Test-Path $sessionDataPath) {
    Remove-Item -Recurse -Force $sessionDataPath -ErrorAction SilentlyContinue
    Write-Host "Session cache cleared!" -ForegroundColor Green
} else {
    Write-Host "No session cache found." -ForegroundColor Gray
}

# 3. Start WhatsApp Helper Service
Write-Host "3. Starting WhatsApp Helper Service..." -ForegroundColor Yellow
$whatsappDir = Join-Path $PSScriptRoot "whatsapp-service"
Start-Process cmd.exe -ArgumentList "/c start `"Lari Traders WhatsApp Service`" /min cmd /c `"cd /d $whatsappDir && node server.js`""

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "RESET COMPLETED! Please refresh your browser and scan the new QR code." -ForegroundColor Green
Write-Host "=========================================================" -ForegroundColor Cyan
