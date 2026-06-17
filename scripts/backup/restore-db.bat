@echo off
setlocal enabledelayedexpansion
set PATH=%SystemRoot%\System32;%SystemRoot%;%SystemRoot%\System32\Wbem;%PATH%

:: 1. Check for Admin rights
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ========================================================
    echo ERROR: Please run this script as an Administrator!
    echo Right-click on restore-db.bat and select "Run as Administrator".
    echo ========================================================
    pause
    exit /b
)

:: Go to the project root directory
cd /d "%~dp0"

:: 2. Detect service name & database based on directory name
set SERVICE_NAME=fmcg-backend-uat
set DB_NAME=fmcg_shop
echo %~dp0 | findstr /i "fmcg-shop-prod" >nul
if %errorLevel% equ 0 (
    set SERVICE_NAME=fmcg-backend-prod
    set DB_NAME=fmcg_shop_prod
)

echo ==========================================
echo LARI TRADERS — DATABASE RESTORE SYSTEM
echo ==========================================
echo Service Name:      %SERVICE_NAME%
echo Target Database:   %DB_NAME%
echo ==========================================

:: 3. Read DB Password from .env
set DB_PASSWORD=
if exist "..\..\.env" (
    for /f "usebackq tokens=1,2 delims==" %%a in ("..\..\.env") do (
        set key=%%a
        set val=%%b
        if "!key!"=="DB_PASSWORD" set DB_PASSWORD=%%b
    )
)
if "%DB_PASSWORD%"=="" set DB_PASSWORD=9450

:: 4. List available backups
echo ==========================================
echo AVAILABLE BACKUP FILES:
echo ==========================================
dir /b /o:-d "..\..\backups\*.sql" 2>nul
dir /b /o:-d "..\..\backups\*.enc" 2>nul
echo ==========================================

:: 5. Prompt user for filename
set /p BACKUP_NAME="Enter backup filename to restore (e.g. fmcg_shop_backup_dd_MM_yyyy.sql): "

if "%BACKUP_NAME%"=="" (
    echo ERROR: Filename cannot be empty.
    pause
    exit /b
)

:: Resolve backup file path
set BACKUP_PATH=%~dp0..\..\backups\%BACKUP_NAME%

if not exist "%BACKUP_PATH%" (
    echo ERROR: Backup file not found at: %BACKUP_PATH%
    pause
    exit /b
)

:: Decrypt .enc files automatically
echo %BACKUP_NAME% | findstr /i "\.enc$" >nul
if %errorLevel% equ 0 (
    echo ========================================================
    echo You selected an ENCRYPTED (.enc) backup file.
    echo Decrypting it automatically using decrypt-backup.java...
    echo ========================================================
    java decrypt-backup.java "%BACKUP_NAME%"
    if !errorLevel! neq 0 (
        echo ERROR: Decryption failed. Cannot proceed with restoration.
        pause
        exit /b
    )
    :: Update the restore filename to the decrypted version
    set BACKUP_NAME=!BACKUP_NAME:~0,-4!
    set BACKUP_PATH=%~dp0..\..\backups\!BACKUP_NAME!
    echo.
    echo Using decrypted file: !BACKUP_NAME!
)

echo.
echo !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
echo WARNING: This will OVERWRITE the database '%DB_NAME%'.
echo All current data will be replaced by the backup file.
echo !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
set /p CONFIRM="Are you absolutely sure you want to proceed? (Type 'YES' to confirm): "

if not "%CONFIRM%"=="YES" (
    echo Restore cancelled by user.
    pause
    exit /b
)

echo ==========================================
echo 1. STOPPING SERVICE: %SERVICE_NAME%...
echo ==========================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Stop-Service %SERVICE_NAME% -ErrorAction SilentlyContinue"
echo Service stopped.

echo ==========================================
echo 2. RUNNING DATABASE RESTORE (psql)...
echo ==========================================
:: Set environment password for psql
set PGPASSWORD=%DB_PASSWORD%

:: Find psql.exe
set PSQL_PATH=psql
set PG_CANDIDATE_1="C:\Program Files\PostgreSQL\16\bin\psql.exe"
set PG_CANDIDATE_2="C:\Program Files\PostgreSQL\15\bin\psql.exe"
set PG_CANDIDATE_3="C:\Program Files\PostgreSQL\14\bin\psql.exe"

if exist %PG_CANDIDATE_1% set PSQL_PATH=%PG_CANDIDATE_1%
if not exist %PG_CANDIDATE_1% if exist %PG_CANDIDATE_2% set PSQL_PATH=%PG_CANDIDATE_2%
if not exist %PG_CANDIDATE_1% if not exist %PG_CANDIDATE_2% if exist %PG_CANDIDATE_3% set PSQL_PATH=%PG_CANDIDATE_3%

echo Using psql path: %PSQL_PATH%

:: Terminate other connections & Drop/Recreate database to ensure clean restore
echo Terminating active connections and recreating database...
%PSQL_PATH% -h localhost -p 5432 -U postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '%DB_NAME%' AND pid <> pg_backend_pid();" >nul 2>&1
%PSQL_PATH% -h localhost -p 5432 -U postgres -c "DROP DATABASE IF EXISTS %DB_NAME%;" >nul 2>&1
%PSQL_PATH% -h localhost -p 5432 -U postgres -c "CREATE DATABASE %DB_NAME%;" >nul 2>&1

:: Import the backup file
echo Restoring data schema and tables...
%PSQL_PATH% -h localhost -p 5432 -U postgres -d %DB_NAME% -f "%BACKUP_PATH%"
if %errorLevel% neq 0 (
    echo ==========================================
    echo ERROR: Database restore failed!
    echo ==========================================
    goto restart_service
)
echo Database restore completed successfully.

:restart_service
echo ==========================================
echo 3. RESTARTING SERVICE: %SERVICE_NAME%...
echo ==========================================
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -Command "Start-Service %SERVICE_NAME% -ErrorAction SilentlyContinue"
echo Service started.

echo ==========================================
echo SUCCESS: Restoration process completed!
echo ==========================================
pause
