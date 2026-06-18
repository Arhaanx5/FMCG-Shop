@echo off
:: Ensure admin privileges
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo.
    echo ===============================================================
    echo [ERROR] Please Right-Click this file and select "Run as administrator"
    echo ===============================================================
    echo.
    pause
    exit /b 1
)

echo.
echo ===============================================================
echo   Lari Traders - Restarting OCR Scanner Service
echo ===============================================================
echo.

echo [1/2] Stopping fmcg-ocr service...
net stop fmcg-ocr
echo.

echo [2/2] Starting fmcg-ocr service...
net start fmcg-ocr
echo.

echo ===============================================================
echo [SUCCESS] OCR Service has been restarted successfully!
echo ===============================================================
echo.
pause
