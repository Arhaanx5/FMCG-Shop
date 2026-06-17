@echo off
echo ===================================================
echo Lari Traders (UAT) - Cloudflare Pages Deployer
echo ===================================================
echo.
cd /d "%~dp0\frontend"
echo [1/2] Building React Frontend...
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Frontend build failed!
    pause
    exit /b %ERRORLEVEL%
)
echo.
echo [2/2] Uploading and deploying to Cloudflare Pages...
call npx wrangler pages deploy dist --project-name lari-traders-uat-ui
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Cloudflare Pages deployment failed!
    pause
    exit /b %ERRORLEVEL%
)
echo.
echo ===================================================
echo [SUCCESS] Frontend deployed to Cloudflare Pages!
echo ===================================================
pause
