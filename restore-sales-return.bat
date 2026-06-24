@echo off
:: Check if PowerShell is available
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\restore-sales-return.ps1"
