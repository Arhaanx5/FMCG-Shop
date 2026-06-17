# 🛠️ Lari Traders (UAT) — Services Setup & Debug SOP

Bhai, laptop me backend database services register karne, background processes manage karne aur hardware standby config (Lid-Close sleep block) set karne ki details niche hain.

---

## ⚙️ 1. Windows Background Services Setup (using NSSM)

Hum black prompt screen command window chalu rakhne ke badle system ko Windows background tasks me set karne ke liye **NSSM (Non-Sucking Service Manager)** ka use karte hain.

### Run Administrator PowerShell commands to register:

```powershell
# 1. Register fmcg-backend-uat Service
nssm install fmcg-backend-uat "C:\Program Files\Java\jdk-21\bin\java.exe" "-jar fmcg-shop-0.0.1-SNAPSHOT.jar --spring.profiles.active=uat"
nssm set fmcg-backend-uat DisplayName "Lari Traders Backend (UAT)"
nssm set fmcg-backend-uat Description "Spring Boot Backend Service running UAT profile on port 8085"
nssm set fmcg-backend-uat AppDirectory "d:\intelliJ2025\fmcg-shop\fmcg-shop\target"

# 2. Register fmcg-whatsapp Service
nssm install fmcg-whatsapp "C:\Program Files\nodejs\node.exe" "server.js"
nssm set fmcg-whatsapp DisplayName "Lari Traders WhatsApp Service"
nssm set fmcg-whatsapp Description "NodeJS microservice using Puppeteer for automated WhatsApp broadcasts on port 3000"
nssm set fmcg-whatsapp AppDirectory "d:\intelliJ2025\fmcg-shop\fmcg-shop\whatsapp-service"

# 3. Register fmcg-ocr Service
nssm install fmcg-ocr "C:\Users\arhaa\AppData\Local\Programs\Python\Python314\python.exe" "main.py"
nssm set fmcg-ocr DisplayName "Lari Traders OCR Scanner Service"
nssm set fmcg-ocr Description "FastAPI service running Gemini OCR model for parsing purchase tax invoices on port 8087"
nssm set fmcg-ocr AppDirectory "d:\intelliJ2025\fmcg-shop\fmcg-shop\ocr-service"

# 4. Register Cloudflare Tunnel Service (Cloudflared)
nssm install Cloudflared "d:\intelliJ2025\fmcg-shop\fmcg-shop\cloudflared.exe" "--config C:\Users\arhaa\.cloudflared\config.yml tunnel run"
nssm set Cloudflared DisplayName "Lari Traders Cloudflare Tunnel"
nssm set Cloudflared Description "Maintains the secure Cloudflare Tunnel connection for UAT/PROD subdomains"
```

---

## 📂 2. Services Control Commands (PowerShell / CMD)

PowerShell ko Admin privilege mode se open karke niche likhe commands se status aur actions execute karein:

```powershell
# Services Status verify karne ke liye:
Get-Service fmcg-backend-uat
Get-Service fmcg-whatsapp
Get-Service fmcg-ocr
Get-Service Cloudflared

# Service Restart trigger karne ke liye:
Restart-Service fmcg-backend-uat
Restart-Service fmcg-whatsapp
Restart-Service Cloudflared

# Service Stop/Start karne ke liye:
Stop-Service fmcg-backend-uat -Force
Start-Service fmcg-backend-uat
```

---

## 💤 3. Laptop ka Lid (Dhakkan) Band karne par Sleep rokna

Laptop band karne par standard OS mode automatic sleeping/standby parameters load kar leta hai, jis se background website tunnel crash/offline ho jata hai. Ise bypass karne ke liye Admin PowerShell/CMD me run karein:

```powershell
# AC Power (Plugged in) par lid band hone par kuch na kare (Do Nothing)
powercfg /setacvalueindex SCHEME_CURRENT SUB_BUTTONS LIDACTION 0

# Battery power par lid band hone par kuch na kare (Do Nothing)
powercfg /setdcvalueindex SCHEME_CURRENT SUB_BUTTONS LIDACTION 0

# Config settings system refresh karein
powercfg /setactive SCHEME_CURRENT
```

---

## 🔴 4. Common Troubleshooting Errors

* **Error 1: Maven Build Failure (File Lock)**:
  - *Kyun*: Background Java running service JAR ko edit karne se access lock rakhti hai.
  - *Fix*: Build update scripts run karne se pehle service stop karein, ya direct `fmcg-build` automation script run as Admin karein.
* **Error 2: Network Cloudflare Portal Down (Blue Screen/Gateway Error)**:
  - *Kyun*: Backend service crash hai ya local port listener binding disconnected hai.
  - *Fix*: Check connection ports locally:
    `Test-NetConnection -Port 8085 -ComputerName localhost`
    Agar connection fail ho, toh service restart karein (`Restart-Service fmcg-backend-uat`).
