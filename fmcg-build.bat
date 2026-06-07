@echo off
:: Check for Admin permissions
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ERROR: Please Right-Click and select "Run as Administrator" or run cmd/powershell as Admin!
    pause
    exit /b
)

:: Go to the project root directory
cd /d "%~dp0"

:: Detect service name based on directory name
echo %~dp0 | findstr /i "fmcg-shop-prod" >nul
if %errorLevel% equ 0 (
    set SERVICE_NAME=fmcg-backend-prod
) else (
    set SERVICE_NAME=fmcg-backend-uat
)

:: Update Android and Capacitor environment configurations dynamically based on environment
if "%SERVICE_NAME%"=="fmcg-backend-prod" (
    "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "(Get-Content frontend/capacitor.config.json) -replace '\"appId\":\s*\"[^\"]*\"', '\"appId\": \"com.laritraders.app\"' -replace '\"appName\":\s*\"[^\"]*\"', '\"appName\": \"Lari Traders\"' -replace '\"url\":\s*\"[^\"]*\"', '\"url\": \"https://app.laritraders.store\"' | Set-Content frontend/capacitor.config.json"
    "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "(Get-Content frontend/android/app/src/main/res/values/strings.xml) -replace '<string name=\"app_name\">[^<]*</string>', '<string name=\"app_name\">Lari Traders</string>' -replace '<string name=\"title_activity_main\">[^<]*</string>', '<string name=\"title_activity_main\">Lari Traders</string>' -replace '<string name=\"package_name\">[^<]*</string>', '<string name=\"package_name\">com.laritraders.app</string>' -replace '<string name=\"custom_url_scheme\">[^<]*</string>', '<string name=\"custom_url_scheme\">com.laritraders.app</string>' | Set-Content frontend/android/app/src/main/res/values/strings.xml"
    "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "(Get-Content frontend/android/app/build.gradle) -replace 'applicationId\s*\"[^\"]*\"', 'applicationId \"com.laritraders.app\"' | Set-Content frontend/android/app/build.gradle"
) else (
    "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "(Get-Content frontend/capacitor.config.json) -replace '\"appId\":\s*\"[^\"]*\"', '\"appId\": \"com.laritraders.app.uat\"' -replace '\"appName\":\s*\"[^\"]*\"', '\"appName\": \"Lari Traders UAT\"' -replace '\"url\":\s*\"[^\"]*\"', '\"url\": \"https://uat.laritraders.store\"' | Set-Content frontend/capacitor.config.json"
    "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "(Get-Content frontend/android/app/src/main/res/values/strings.xml) -replace '<string name=\"app_name\">[^<]*</string>', '<string name=\"app_name\">Lari Traders UAT</string>' -replace '<string name=\"title_activity_main\">[^<]*</string>', '<string name=\"title_activity_main\">Lari Traders UAT</string>' -replace '<string name=\"package_name\">[^<]*</string>', '<string name=\"package_name\">com.laritraders.app.uat</string>' -replace '<string name=\"custom_url_scheme\">[^<]*</string>', '<string name=\"custom_url_scheme\">com.laritraders.app.uat</string>' | Set-Content frontend/android/app/src/main/res/values/strings.xml"
    "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "(Get-Content frontend/android/app/build.gradle) -replace 'applicationId\s*\"[^\"]*\"', 'applicationId \"com.laritraders.app.uat\"' | Set-Content frontend/android/app/build.gradle"
)

echo ==========================================
echo Project Directory: %~dp0
echo Service Name:      %SERVICE_NAME%
echo ==========================================

echo ==========================================
echo 1. BUILDING FRONTEND REACT APP...
echo ==========================================
cd frontend
call npm run build
if %errorLevel% neq 0 (
    echo ERROR: Frontend build failed!
    cd ..
    pause
    exit /b
)
cd ..

echo ==========================================
echo 2. COPYING BUILD TO STATIC RESOURCES...
echo ==========================================
:: Clean old assets and copy new ones using PowerShell
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Remove-Item -Recurse -Force src/main/resources/static/* -ErrorAction SilentlyContinue; Copy-Item -Recurse -Force frontend/dist/* src/main/resources/static/"
if %errorLevel% neq 0 (
    echo ERROR: Copying build assets failed!
    pause
    exit /b
)

echo ==========================================
echo 3. STOPPING BACKEND SERVICE...
echo ==========================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service %SERVICE_NAME%"

echo ==========================================
echo 4. PACKAGING BACKEND JAR...
echo ==========================================
call mvnw.cmd package -DskipTests
if %errorLevel% neq 0 (
    echo ERROR: Backend compilation failed!
    pause
    exit /b
)

echo ==========================================
echo 5. STARTING BACKEND SERVICE...
echo ==========================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service %SERVICE_NAME%"

echo ==========================================
echo SUCCESS: Frontend and Backend built and restarted successfully!
echo ==========================================
pause
