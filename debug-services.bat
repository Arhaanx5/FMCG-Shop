@echo off
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Expression -Command (Get-Content -Path '%~f0' | Select-Object -Skip 4 | Out-String)"
if "%1" neq "-NoPause" pause
exit /b

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "            LARI TRADERS SERVICES DIAGNOSTICS            " -ForegroundColor Cyan -NoNewline
Write-Host " [Admin]" -ForegroundColor Magenta
Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host ""

# Check Admin privileges
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "[WARNING] Script is not running as Administrator! Some diagnostic checks might fail." -ForegroundColor Yellow
    Write-Host "Please close this and run it as Administrator for full details." -ForegroundColor Yellow
    Write-Host "---------------------------------------------------------" -ForegroundColor Yellow
}

# 1. Check Windows Services Status
Write-Host "--- [1] Background Services Operational Status ---" -ForegroundColor White
$servicesToCheck = @(
    @{ Name = "fmcg-backend"; Display = "Main Backend (fmcg-backend)" },
    @{ Name = "fmcg-backend-uat"; Display = "UAT Backend (fmcg-backend-uat)" },
    @{ Name = "fmcg-backend-prod"; Display = "PROD Backend (fmcg-backend-prod)" },
    @{ Name = "Cloudflared"; Display = "Cloudflare Tunnel (Cloudflared)" }
)

$installedServices = @()

foreach ($svc in $servicesToCheck) {
    $serviceObj = Get-Service -Name $svc.Name -ErrorAction SilentlyContinue
    if ($serviceObj) {
        $installedServices += $svc.Name
        $status = $serviceObj.Status
        $color = if ($status -eq "Running") { "Green" } else { "Red" }
        Write-Host ("  {0,-35} : " -f $svc.Display) -NoNewline
        Write-Host $status -ForegroundColor $color
    }
}

# WhatsApp Helper Status (combines service status + port 3000 check)
$waSvc = Get-Service -Name "fmcg-whatsapp" -ErrorAction SilentlyContinue
$waPortOccupied = [bool](Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue)
Write-Host ("  {0,-35} : " -f "WhatsApp Helper (Port 3000)") -NoNewline
if ($waPortOccupied) {
    Write-Host "Running" -ForegroundColor Green
} elseif ($waSvc -and $waSvc.Status -eq "Running") {
    Write-Host "Running" -ForegroundColor Green
} else {
    Write-Host "Stopped" -ForegroundColor Red
}
if ($installedServices.Count -eq 0) {
    Write-Host "  No Lari Traders services found on this system!" -ForegroundColor Red
}
Write-Host ""

# 2. Check Port Bindings
Write-Host "--- [2] Network Port Bindings ---" -ForegroundColor White
$portsToCheck = @(
    @{ Port = 3000; Display = "WhatsApp API Port (3000)" },
    @{ Port = 8085; Display = "UAT Backend Port (8085)" },
    @{ Port = 8086; Display = "PROD Backend Port (8086)" }
)

foreach ($pt in $portsToCheck) {
    $connections = Get-NetTCPConnection -LocalPort $pt.Port -State Listen -ErrorAction SilentlyContinue
    Write-Host ("  {0,-30} : " -f $pt.Display) -NoNewline
    if ($connections) {
        $pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
        $pidList = $pids -join ", "
        Write-Host "OCCUPIED (PID: $pidList)" -ForegroundColor Green
        # Identify process name
        foreach ($p in $pids) {
            $proc = Get-Process -Id $p -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Host "                                   ↳ Process: $($proc.ProcessName).exe" -ForegroundColor Gray
            }
        }
    } else {
        Write-Host "FREE (Closed)" -ForegroundColor Yellow
    }
}
Write-Host ""

# 3. API & Web App Connection Test
Write-Host "--- [3] API & Web App Connectivity ---" -ForegroundColor White

# WhatsApp Status Check
Write-Host "  WhatsApp API Status              : " -NoNewline
$waPortListening = [bool](Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue)
if (-not $waPortListening) {
    Write-Host "DOWN / UNREACHABLE (Port Closed) 🛑" -ForegroundColor Red
} else {
    try {
        $waResponse = Invoke-RestMethod -Uri "http://127.0.0.1:3000/status" -TimeoutSec 2 -ErrorAction Stop
        $waState = $waResponse.status
        if ($waState -eq "CONNECTED") {
            Write-Host "CONNECTED (WhatsApp is Linked) ✅" -ForegroundColor Green
        } elseif ($waState -eq "INITIALIZING") {
            Write-Host "INITIALIZING (Starting up...) ⏳" -ForegroundColor Yellow
        } else {
            Write-Host "DISCONNECTED (Scan QR Code Required) ❌" -ForegroundColor Red
        }
    } catch {
        Write-Host "DOWN / UNREACHABLE 🛑" -ForegroundColor Red
    }
}

# UAT App Check
if ($installedServices -contains "fmcg-backend-uat" -or $installedServices -contains "fmcg-backend") {
    Write-Host "  UAT Web App Response (8085)       : " -NoNewline
    try {
        $req = [System.Net.WebRequest]::Create("http://127.0.0.1:8085")
        $req.Timeout = 2000
        $res = $req.GetResponse()
        Write-Host "ONLINE (HTTP $($res.StatusCode)) 🌐" -ForegroundColor Green
        $res.Close()
    } catch {
        if ($_.Exception.InnerException -and $_.Exception.InnerException.Message -like "*connection refused*") {
            Write-Host "OFFLINE (Connection Refused) 🛑" -ForegroundColor Red
        } elseif ($_.Exception.Response) {
            Write-Host "ONLINE (HTTP $($_.Exception.Response.StatusCode)) 🌐" -ForegroundColor Green
        } else {
            Write-Host "OFFLINE / ERROR 🛑" -ForegroundColor Red
        }
    }
}

# PROD App Check
if ($installedServices -contains "fmcg-backend-prod" -or $installedServices -contains "fmcg-backend") {
    Write-Host "  PROD Web App Response (8086)      : " -NoNewline
    try {
        $req = [System.Net.WebRequest]::Create("http://127.0.0.1:8086")
        $req.Timeout = 2000
        $res = $req.GetResponse()
        Write-Host "ONLINE (HTTP $($res.StatusCode)) 🌐" -ForegroundColor Green
        $res.Close()
    } catch {
        if ($_.Exception.InnerException -and $_.Exception.InnerException.Message -like "*connection refused*") {
            Write-Host "OFFLINE (Connection Refused) 🛑" -ForegroundColor Red
        } elseif ($_.Exception.Response) {
            Write-Host "ONLINE (HTTP $($_.Exception.Response.StatusCode)) 🌐" -ForegroundColor Green
        } else {
            Write-Host "OFFLINE / ERROR 🛑" -ForegroundColor Red
        }
    }
}
Write-Host ""

# 4. Troubleshooting & Actionable Advice
Write-Host "--- [4] Diagnosis & Advice ---" -ForegroundColor White
$hasIssues = $false

# WhatsApp Service down check
$waPortOccupied = [bool](Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue)

if (-not $waPortOccupied) {
    Write-Host "  [!] WhatsApp helper service is NOT running (Port 3000 is closed)." -ForegroundColor Red
    Write-Host "      Advice: Run 'start-all-services.bat' to start the WhatsApp service." -ForegroundColor Gray
    $hasIssues = $true
}

# WhatsApp Disconnected advice
try {
    $waResponse = Invoke-RestMethod -Uri "http://127.0.0.1:3000/status" -TimeoutSec 2 -ErrorAction Stop
    if ($waResponse.status -ne "CONNECTED") {
        Write-Host "  [!] WhatsApp is running but NOT linked to a phone." -ForegroundColor Yellow
        Write-Host "      Advice: Open the web application and scan the QR code under Bulk Reminders page." -ForegroundColor Gray
        $hasIssues = $true
    }
} catch {}

# Backend down check
$activeBackends = @()
foreach ($bSvc in @("fmcg-backend", "fmcg-backend-uat", "fmcg-backend-prod")) {
    $s = Get-Service -Name $bSvc -ErrorAction SilentlyContinue
    if ($s -and $s.Status -eq "Running") {
        $activeBackends += $bSvc
    }
}

if ($activeBackends.Count -eq 0) {
    Write-Host "  [!] No backend services are currently running." -ForegroundColor Red
    Write-Host "      Advice: Run 'start-all-services.bat' to start the application backend." -ForegroundColor Gray
    $hasIssues = $true
}

# Cloudflared down check
$cfSvc = Get-Service -Name "Cloudflared" -ErrorAction SilentlyContinue
if ($cfSvc -and $cfSvc.Status -ne "Running") {
    Write-Host "  [!] Cloudflare Tunnel service is NOT running." -ForegroundColor Red
    Write-Host "      Advice: Start the tunnel using 'Start-Service Cloudflared' to make the app accessible online." -ForegroundColor Gray
    $hasIssues = $true
}

# Internet connectivity check
Write-Host "  Checking internet connectivity..." -ForegroundColor Gray
try {
    $pingTest = Test-Connection -ComputerName google.com -Count 1 -Delay 1 -ErrorAction Stop
} catch {
    Write-Host "  [!] System has NO internet connection." -ForegroundColor Red
    Write-Host "      Advice: Please check your Wi-Fi or local internet router." -ForegroundColor Gray
    $hasIssues = $true
}

if (-not $hasIssues) {
    Write-Host "  ✅ ALL SERVICES ARE RUNNING PERFECTLY! Website is healthy." -ForegroundColor Green
}
Write-Host ""
Write-Host "=========================================================" -ForegroundColor Cyan
if ([Environment]::UserInteractive) {
    # Handled by CMD wrapper pause
}
