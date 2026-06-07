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

# 1. Update capacitor.config.json
if (Test-Path "frontend/capacitor.config.json") {
    (Get-Content "frontend/capacitor.config.json" -Raw) `
      -replace '"appId":\s*"[^"]*"', "`"appId`": `"$appId`"" `
      -replace '"appName":\s*"[^"]*"', "`"appName`": `"$appName`"" `
      -replace '"url":\s*"[^"]*"', "`"url`": `"$url`"" `
      | Set-Content "frontend/capacitor.config.json"
}

# 2. Update strings.xml
$stringsPath = "frontend/android/app/src/main/res/values/strings.xml"
if (Test-Path $stringsPath) {
    (Get-Content $stringsPath -Raw) `
      -replace '<string name="app_name">[^<]*</string>', "<string name=`"app_name`">$appName</string>" `
      -replace '<string name="title_activity_main">[^<]*</string>', "<string name=`"title_activity_main`">$appName</string>" `
      -replace '<string name="package_name">[^<]*</string>', "<string name=`"package_name`">$appId</string>" `
      -replace '<string name="custom_url_scheme">[^<]*</string>', "<string name=`"custom_url_scheme`">$appId</string>" `
      | Set-Content $stringsPath
}

# 3. Update build.gradle
$gradlePath = "frontend/android/app/build.gradle"
if (Test-Path $gradlePath) {
    (Get-Content $gradlePath -Raw) `
      -replace 'applicationId\s*"[^"]*"', "applicationId `"$appId`"" `
      | Set-Content $gradlePath
}

Write-Host "Android environment configuration completed successfully!"
