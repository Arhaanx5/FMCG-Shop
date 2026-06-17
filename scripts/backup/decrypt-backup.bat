@echo off
:: Lari Traders Offline Backup Decrypter
:: This script runs the offline Java decrypter program.

cd /d "%~dp0"
java decrypt-backup.java
pause
