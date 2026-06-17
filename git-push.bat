@echo off
:: Change working directory to the folder where this batch file is located
cd /d "%~dp0"

echo ========================================================
echo   Lari Traders (UAT) - Git Stage, Commit & Push Script
echo ========================================================
echo.

echo [1/3] Running: git add .
call git add .
echo.

echo [2/3] Running: git commit -m ...
call git commit -m "Feat: Implement LariTraders Business Health Report module with programmatic score cap, cooldown retry buttons, refined inventory checks, and empty state support"
echo.

echo [3/3] Running: git push
call git push
echo.

echo ========================================================
echo   [SUCCESS] Changes pushed to UAT Git remote repository!
echo ========================================================
echo.
pause
