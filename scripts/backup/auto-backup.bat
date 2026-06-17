@echo off
:: ========================================================================================
::          LARI TRADERS — AUTOMATIC SYSTEM-LEVEL DATABASE BACKUP SCRIPT
:: ========================================================================================
:: This script runs independently of the Java application.
:: It dumps both UAT and PROD databases, and cleans up files older than 30 days.
:: ========================================================================================

set PGPASSWORD=9450
set "UAT_BACKUP_DIR=%~dp0..\..\backups"
set "PROD_BACKUP_DIR=%~dp0..\..\..\fmcg-shop-prod\backups"

:: Create backups folders if they don't exist
if not exist "%UAT_BACKUP_DIR%" mkdir "%UAT_BACKUP_DIR%"
if not exist "%PROD_BACKUP_DIR%" mkdir "%PROD_BACKUP_DIR%"

:: Get current date in DD_MM_YYYY format
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
set date_stamp=%datetime:~6,2%_%datetime:~4,2%_%datetime:~0,4%

echo =========================================================
echo [%date_stamp%] Starting Daily Database Backups...
echo =========================================================

:: 1. Backup Production Database
echo [1/3] Backing up fmcg_shop_prod (PROD)...
"C:\Program Files\PostgreSQL\16\bin\pg_dump.exe" -h localhost -p 5432 -U postgres -F p -f "%PROD_BACKUP_DIR%\fmcg_shop_prod_backup_%date_stamp%.sql" fmcg_shop_prod
if %errorlevel% equ 0 (
    echo [SUCCESS] Production database backed up successfully.
) else (
    echo [ERROR] Production database backup failed!
)

:: 2. Backup UAT Database
echo [2/3] Backing up fmcg_shop (UAT)...
"C:\Program Files\PostgreSQL\16\bin\pg_dump.exe" -h localhost -p 5432 -U postgres -F p -f "%UAT_BACKUP_DIR%\fmcg_shop_backup_%date_stamp%.sql" fmcg_shop
if %errorlevel% equ 0 (
    echo [SUCCESS] UAT database backed up successfully.
) else (
    echo [ERROR] UAT database backup failed!
)

:: 3. Clean up files older than 30 days
echo [3/3] Deleting SQL backup files older than 30 days...
forfiles /p "%UAT_BACKUP_DIR%" /m *.sql /d -30 /c "cmd /c del @path" >nul 2>&1
forfiles /p "%PROD_BACKUP_DIR%" /m *.sql /d -30 /c "cmd /c del @path" >nul 2>&1
echo Done!

echo =========================================================
echo BACKUP PROCESS COMPLETED!
echo =========================================================
