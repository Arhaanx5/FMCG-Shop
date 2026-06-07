@echo off
echo Installing WhatsApp Service...
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" install fmcg-whatsapp node "D:\intelliJ2025\fmcg-shop\fmcg-shop\whatsapp-service\server.js"
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-whatsapp DisplayName "Lari Traders WhatsApp Service"
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-whatsapp Description "Headless WhatsApp Web service for automatic bulk reminders"
"C:\Users\arhaa\AppData\Local\Microsoft\WinGet\Links\nssm.exe" set fmcg-whatsapp AppDirectory "D:\intelliJ2025\fmcg-shop\fmcg-shop\whatsapp-service"

echo Starting WhatsApp Service...
net start fmcg-whatsapp
echo Done!
