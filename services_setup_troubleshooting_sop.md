# 🛠️ Lari Traders — Backend, Database aur Cloudflare Service Setup SOP (Simple Hinglish)

Bhai, laptop me backend database configure karne, port badalne (UAT ↔ PROD), aur background services manage karne ke liye ye aasan guide hai. Naye computer par setup karte waqt iske steps follow karein.

---

## 📋 Index (TOC)
1. [NSSM aur Background Services Setup (fmcg-backend)](#1-nssm-aur-background-services-setup-fmcg-backend)
2. [Services ko Start, Stop aur Check Kaise Karein](#2-services-ko-start-stop-aur-check-kaise-karein)
3. [Environment Switch Kaise Karein (UAT ↔ PROD)](#3-environment-switch-kaise-karein-uat--prod)
4. [Parallel Setup (UAT aur PROD dono ek sath chalana)](#4-parallel-setup-uat-aur-prod-dono-ek-sath-chalana)
5. [Aane Wale Errors aur Unka Fix (Troubleshooting)](#5-aane-wale-errors-aur-unka-fix-troubleshooting)
6. [Laptop ka Lid (Dhakkan) Band karne par Sleep rokna](#6-laptop-ka-lid-dhakkan-band-karne-par-sleep-rokna)
7. [Frontend Build & Static Deployment (`fmcg-build`)](#7-frontend-build--static-deployment-fmcg-build)

---

## 1. NSSM aur Background Services Setup (fmcg-backend)

Har bar black screen terminal chalu rakhne ke badle, hum services ko Windows ke background me automatic run kar dete hain. Iske liye hum **NSSM** use karte hain:

### A. Backend Service Setup (`fmcg-backend`):
1. **PowerShell** ko **Run as Administrator** open karein.
2. Niche di gayi command run karein setup panel kholne ke liye:
   ```powershell
   nssm install fmcg-backend
   ```
3. Ek window khulegi, usme ye details bharo:
   - **Path**: Apne computer ka Java path select karein (Jaise: `C:\Program Files\Java\jdk-21\bin\java.exe`).
   - **Startup directory**: Project ke target folder ka path (Jaise: `D:\intelliJ2025\fmcg-shop\fmcg-shop\target`).
   - **Arguments** (UAT ke liye): `-jar fmcg-shop-0.0.1-SNAPSHOT.jar --spring.profiles.active=uat`
   - **Arguments** (PROD ke liye): `-jar fmcg-shop-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod`
   - **Display name** (Details Tab): `Lari Traders Backend`
   - **Startup type** (Details Tab): `Automatic`
4. **Install service** par click kar do.

### B. Cloudflare Tunnel Setup (`Cloudflared`):
Internet par secure access ke liye Administrator PowerShell me ye command chalayein:
```powershell
nssm install Cloudflared "D:\intelliJ2025\fmcg-shop\fmcg-shop\cloudflared.exe" "--config C:\Users\arhaa\.cloudflared\config.yml tunnel run"
nssm set Cloudflared DisplayName "Lari Traders Cloudflare Tunnel"
nssm set Cloudflared Description "Maintains the secure Cloudflare Tunnel connection for app.laritraders.store"
```

---

## 2. Services ko Start, Stop aur Check Kaise Karein

PowerShell as Admin me in commands se services ko control karein:

* **Start Backend**: `Start-Service fmcg-backend`
* **Stop Backend**: `Stop-Service fmcg-backend`
* **Restart Backend**: `Restart-Service fmcg-backend`
* **Check Status**: `Get-Service fmcg-backend` (Status **Running** dikhna chahiye)

*(Cloudflare Tunnel ke liye `fmcg-backend` ki jagah `Cloudflared` likhein).*

---

## 3. Environment Switch Kaise Karein (UAT ↔ PROD)

Agar aap test profile (UAT) aur real live business profile (PROD) ke beech switch karna chahte hain:

* **Short Method**: Project root directory me `switch-to-uat.bat` aur `switch-to-prod.bat` files hain. In par right-click karo aur **"Run as Administrator"** chala do. Switch automatic ho jayega!
* **Manual Method** (CMD as Admin):
  ```powershell
  Stop-Service fmcg-backend
  # UAT ke liye:
  nssm set fmcg-backend AppParameters "-jar fmcg-shop-0.0.1-SNAPSHOT.jar --spring.profiles.active=uat"
  # Ya PROD ke liye:
  nssm set fmcg-backend AppParameters "-jar fmcg-shop-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod"
  
  Start-Service fmcg-backend
  ```

---

## 4. Parallel Setup (UAT aur PROD dono ek sath chalana)

Agar aapko testing aur live business dono computer par ek sath run karni hain:

* **UAT Config**: Port `8085` | Database: `fmcg_shop` | Link: `https://uat.laritraders.store`
* **PROD Config**: Port `8086` | Database: `fmcg_shop_prod` | Link: `https://app.laritraders.store`

### Setup Steps:
1. Purani backend service delete karein:
   ```powershell
   Stop-Service fmcg-backend -ErrorAction SilentlyContinue
   nssm remove fmcg-backend confirm
   ```
2. Dono ki alag-alag service install karein:
   ```powershell
   nssm install fmcg-backend-uat "C:\Program Files\Java\jdk-21\bin\java.exe" "-jar D:\intelliJ2025\fmcg-shop\fmcg-shop\target\fmcg-shop-0.0.1-SNAPSHOT.jar --spring.profiles.active=uat"
   nssm set fmcg-backend-uat DisplayName "Lari Traders Backend - UAT"

   nssm install fmcg-backend-prod "C:\Program Files\Java\jdk-21\bin\java.exe" "-jar D:\intelliJ2025\fmcg-shop\fmcg-shop\target\fmcg-shop-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod"
   nssm set fmcg-backend-prod DisplayName "Lari Traders Backend - PROD"
   ```
3. Cloudflare `config.yml` configuration (usually under `C:\Users\arhaa\.cloudflared\config.yml`) file me ingress rules me dono setup maps add karein.
4. Services start karein:
   ```powershell
   Start-Service fmcg-backend-uat
   Start-Service fmcg-backend-prod
   Restart-Service Cloudflared
   ```

---

## 5. Aane Wale Errors aur Unka Fix (Troubleshooting)

### 🔴 Error 1: Update karne par Build failure ya File Lock aana
* **Kyu**: Java service chalu hone ke karan Windows compile file (.jar) ko write karne se block karta hai.
* **Fix**: Re-build karne se pehle hamesha `fmcg-backend` service stop karein, ya direct `fmcg-build` command chalayein (wo automatic handles kar legi).

### 🔴 Error 2: Mobile App me bill PDF generate hone par Crash aana
* **Kyu**: Android secure system local file URL (file://) direct open hone par block karta hai security restrictions ke karan.
* **Fix**: Billing script code me direct URL return karne ke badle user device storage me file write karke native path `files: [writeResult.uri]` se check-out share karein (humare code me updated hai).

### 🔴 Error 3: App load hone par Blue Screen ya blank aana
* **Kyu**: Cloudflare online tunnel link down hai ya local port 8085 crash ho gaya hai.
* **Fix**: PowerShell me check karein local connection chalu hai ya nahi:
  `Test-NetConnection -Port 8085 -ComputerName localhost`
  Aur mobile App seting me clear cache karke sync restore karein.

---

## 6. Laptop ka Lid (Dhakkan) Band karne par Sleep rokna

Laptop band karne par system so (sleep) jata hai aur website internet par offline ho jati hai. Ise rokne ke liye ye settings karein:

### Quick command (Run as Administrator CMD):
```powershell
# AC Power (Plugged in) par lid band hone par kuch na kare
powercfg /setacvalueindex SCHEME_CURRENT SUB_BUTTONS LIDACTION 0
# Battery power par lid band hone par kuch na kare
powercfg /setdcvalueindex SCHEME_CURRENT SUB_BUTTONS LIDACTION 0
# Settings apply kare
powercfg /setactive SCHEME_CURRENT
```

---

## 7. Frontend Build & Static Deployment (fmcg-build)

Humne pure building workflow (React build generation + Static files copy + Service stop & JAR packaging + Service Restart) ko automatic kar diya hai.

* **Kaise chlayein**: Admin command window me kahi se bhi seedhe type karein:
  ```cmd
  fmcg-build
  ```
* Is file ka automatic version [fmcg-build.bat](file:///d:/intelliJ2025/fmcg-shop/fmcg-shop/fmcg-build.bat) project folder me bhi rakha hai. Detailed info ke liye [frontend_build_deployment_sop.md](file:///d:/intelliJ2025/fmcg-shop/fmcg-shop/frontend_build_deployment_sop.md) chck karein.
