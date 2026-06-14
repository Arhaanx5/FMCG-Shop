param (
    [string]$Env
)

# Move to the script directory
cd $PSScriptRoot

if ($Env -eq "prod") {
    $appId = "com.laritraders.app"
    $appName = "Lari Traders"
    $url = "https://app.laritraders.store"
} else {
    $appId = "com.laritraders.app.uat"
    $appName = "Lari Traders UAT"
    $url = "https://uat.laritraders.store"
}

Write-Host "Configuring Android for environment: $Env"
Write-Host "App ID:   $appId"
Write-Host "App Name: $appName"
Write-Host "URL:      $url"

# 1. Update capacitor.config.json (local assets loading, using secure https scheme)
$configJson = @{
    appId = $appId
    appName = $appName
    webDir = "dist"
    server = @{
        androidScheme = "https"
    }
} | ConvertTo-Json
$configJson | Set-Content "frontend/capacitor.config.json"

# 2. Generate env.js inside the frontend src/config directory
$configDir = "frontend/src/config"
if (!(Test-Path $configDir)) {
    New-Item -ItemType Directory -Force -Path $configDir | Out-Null
}
$envContent = "export const ENV = { apiUrl: '$url/api' }"
$envContent | Set-Content "$configDir/env.js" -Encoding utf8

# 3. Update strings.xml
$stringsPath = "frontend/android/app/src/main/res/values/strings.xml"
if (Test-Path $stringsPath) {
    (Get-Content $stringsPath -Raw) `
      -replace '<string name="app_name">[^<]*</string>', "<string name=`"app_name`">$appName</string>" `
      -replace '<string name="title_activity_main">[^<]*</string>', "<string name=`"title_activity_main`">$appName</string>" `
      -replace '<string name="package_name">[^<]*</string>', "<string name=`"package_name`">$appId</string>" `
      -replace '<string name="custom_url_scheme">[^<]*</string>', "<string name=`"custom_url_scheme`">$appId</string>" `
      | Set-Content $stringsPath
}

# 4. Update build.gradle
$gradlePath = "frontend/android/app/build.gradle"
if (Test-Path $gradlePath) {
    (Get-Content $gradlePath -Raw) `
      -replace 'applicationId\s*"[^"]*"', "applicationId `"$appId`"" `
      | Set-Content $gradlePath
}

# 5. Update Android native assets capacitor.config.json copy
$androidConfigPath = "frontend/android/app/src/main/assets/capacitor.config.json"
if (Test-Path $androidConfigPath) {
    $configJson | Set-Content $androidConfigPath
}

Write-Host "Android environment configuration completed successfully!"
