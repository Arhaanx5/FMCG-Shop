# Self-elevate to Administrator if not already running as Admin
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Requesting Administrator privileges..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs
    exit
}

# Move to the script's directory
cd $PSScriptRoot

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "        LARI TRADERS SERVICES NSSM ENVIRONMENT SETUP      " -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host ""

# Helper function to parse .env file
function Parse-EnvFile($path) {
    $envVars = @{}
    if (Test-Path $path) {
        Get-Content $path | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
                $parts = $line.Split("=", 2)
                $key = $parts[0].Trim()
                $val = $parts[1].Trim()
                $envVars[$key] = $val
            }
        }
    }
    return $envVars
}

# 1. Parse UAT and PROD .env files
$uatEnvPath = Join-Path $PSScriptRoot ".env"
$prodEnvPath = Join-Path (Split-Path $PSScriptRoot -Parent) "fmcg-shop-prod\.env"

# Fallback path logic
if (-not (Test-Path $prodEnvPath)) {
    $prodEnvPath = "d:\intelliJ2025\fmcg-shop\fmcg-shop-prod\.env"
}

Write-Host "Reading UAT config from: $uatEnvPath" -ForegroundColor Gray
$uatEnv = Parse-EnvFile $uatEnvPath

Write-Host "Reading PROD config from: $prodEnvPath" -ForegroundColor Gray
$prodEnv = Parse-EnvFile $prodEnvPath

# Check if Gemini API key exists
$geminiApiKey = $uatEnv["GEMINI_API_KEY"]
if (-not $geminiApiKey -or $geminiApiKey -eq "AIzaSy_TUMHARI_ACTUAL_KEY_YAHAN") {
    $geminiApiKey = $prodEnv["GEMINI_API_KEY"]
}

if (-not $geminiApiKey -or $geminiApiKey -eq "AIzaSy_TUMHARI_ACTUAL_KEY_YAHAN") {
    Write-Host "[WARNING] GEMINI_API_KEY not configured in .env files!" -ForegroundColor Yellow
    $geminiApiKey = Read-Host "Please enter your GEMINI_API_KEY (or press Enter to skip configuring Gemini key)"
}

# 2. Locate nssm.exe and python.exe
$nssmPath = (Get-Command nssm -ErrorAction SilentlyContinue).Path
if (-not $nssmPath) {
    $nssmPath = "C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe"
}

if (-not (Test-Path $nssmPath)) {
    Write-Host "[ERROR] nssm.exe could not be found! Please make sure NSSM is installed." -ForegroundColor Red
    pause
    exit
}

$pythonPath = "C:\Users\arhaa\AppData\Local\Programs\Python\Python314\python.exe"
if (-not (Test-Path $pythonPath)) {
    $pythonPath = (Get-Command python -ErrorAction SilentlyContinue).Path
}

if (-not $pythonPath -or -not (Test-Path $pythonPath)) {
    Write-Host "[ERROR] python.exe could not be found! Please verify Python installation." -ForegroundColor Red
    pause
    exit
}

$ocrScriptPath = Join-Path $PSScriptRoot "ocr-service\main.py"

# 3. Setup fmcg-ocr service
$ocrSvc = Get-Service -Name "fmcg-ocr" -ErrorAction SilentlyContinue
if (-not $ocrSvc) {
    Write-Host "Installing fmcg-ocr service..." -ForegroundColor Green
    & $nssmPath install fmcg-ocr `"$pythonPath`" `"$ocrScriptPath`"
    & $nssmPath set fmcg-ocr DisplayName "Lari Traders OCR Scanner Service"
    & $nssmPath set fmcg-ocr Description "FastAPI service running Gemini OCR model for parsing purchase tax invoices on port 8087"
    & $nssmPath set fmcg-ocr AppDirectory `"$PSScriptRoot\ocr-service`"
} else {
    Write-Host "fmcg-ocr service already exists." -ForegroundColor Gray
}

# 4. Set AppEnvironmentExtra variables via NSSM
Write-Host "Configuring service environment variables..." -ForegroundColor Green

# A. fmcg-ocr
if ($geminiApiKey) {
    & $nssmPath set fmcg-ocr AppEnvironmentExtra "GEMINI_API_KEY=$geminiApiKey"
    Write-Host "  -> fmcg-ocr GEMINI_API_KEY updated." -ForegroundColor Gray
}

# B. fmcg-backend-uat
$dbPassUat = $uatEnv["DB_PASSWORD"]
$jwtSecretUat = $uatEnv["JWT_SECRET"]
if ($dbPassUat -and $jwtSecretUat) {
    & $nssmPath set fmcg-backend-uat AppEnvironmentExtra "DB_PASSWORD=$dbPassUat" "JWT_SECRET=$jwtSecretUat"
    Write-Host "  -> fmcg-backend-uat environment variables updated." -ForegroundColor Gray
}

# C. fmcg-backend-prod
$dbPassProd = $prodEnv["DB_PASSWORD"]
$jwtSecretProd = $prodEnv["JWT_SECRET"]
if ($dbPassProd -and $jwtSecretProd) {
    & $nssmPath set fmcg-backend-prod AppEnvironmentExtra "DB_PASSWORD=$dbPassProd" "JWT_SECRET=$jwtSecretProd"
    Write-Host "  -> fmcg-backend-prod environment variables updated." -ForegroundColor Gray
}

# 5. Restart services to apply changes
Write-Host "Restarting configured services to apply environment changes..." -ForegroundColor Green

Stop-Service fmcg-ocr -ErrorAction SilentlyContinue
Start-Service fmcg-ocr -ErrorAction SilentlyContinue
Write-Host "  -> fmcg-ocr service restarted." -ForegroundColor Gray

if ($dbPassUat -and $jwtSecretUat) {
    Stop-Service fmcg-backend-uat -ErrorAction SilentlyContinue
    Start-Service fmcg-backend-uat -ErrorAction SilentlyContinue
    Write-Host "  -> fmcg-backend-uat service restarted." -ForegroundColor Gray
}

if ($dbPassProd -and $jwtSecretProd) {
    Stop-Service fmcg-backend-prod -ErrorAction SilentlyContinue
    Start-Service fmcg-backend-prod -ErrorAction SilentlyContinue
    Write-Host "  -> fmcg-backend-prod service restarted." -ForegroundColor Gray
}

Write-Host ""
Write-Host "NSSM services environment configuration completed successfully!" -ForegroundColor Green
Write-Host "=========================================================" -ForegroundColor Cyan
pause
