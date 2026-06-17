# 📝 Lari Traders (UAT) — Systems Migration & DR SOP

Bhai, agar system fail ya crash hota hai, toh 10 minutes me new machine par migration/recovery complete karne ke steps niche hain.

---

## 🌐 1. Infrastructure Overview (UAT)
System main segments me distributed hai:
* **Local Windows Server (UAT)**:
  - `fmcg-backend-uat` (Port `8085`)
  - PostgreSQL Database Engine (Port `5432`, DB: `fmcg_shop`)
* **Cloud Infrastructure**:
  - Cloudflare Zero Trust (Public routing tunnel for `uat.laritraders.store`)
  - Google Apps Script Web App (File transfer bridge)
  - Google Drive Folder (Secure encrypted storage space for database backups)

---

## ⚙️ 2. Step-by-Step Migration & Restoration Guide

### Step 1: Install Software Prerequisites
New computer par step-by-step in links se setup standard installation run karein:
1. **Java JDK 21** (Adoptium Temurin): Add java binary path to Environment Variable `PATH`.
2. **NodeJS (LTS)**: Package manager dependencies resolution ke liye.
3. **Python (version 3.12 or 3.14)**: FastAPI OCR model environment load karne ke liye.
4. **PostgreSQL 16**: Installation database config me master user password **`9450`** strictly define karein.
5. **Git**: Repository fetching commands support ke liye.

---

### Step 2: Code Retrieval and Folder Structure Setup
Naye system par clean path layout create karein:
```bash
# UAT Repository Clone karein:
git clone <your-git-repo-url> d:\intelliJ2025\fmcg-shop\fmcg-shop
```

---

### Step 3: Secret Keys Configuration Files Restore karein
Kyunki credentials files security reasons se Git repository me commit nahi hoti, isliye in files ko cloud storage (Drive) se download karke manually paste karein:
1. **`.env`** -> paste directly in UAT root: `d:\intelliJ2025\fmcg-shop\fmcg-shop\.env`
2. **`google-drive-key.json`** -> paste directly in UAT root: `d:\intelliJ2025\fmcg-shop\fmcg-shop\google-drive-key.json`
3. **`backup-config.properties`** -> paste directly in UAT root: `d:\intelliJ2025\fmcg-shop\fmcg-shop\backup-config.properties`

---

### Step 4: Import Database Dump (Restore data)
1. Apne Google Drive account se latest sql backup file download karein (e.g. `fmcg_shop_backup_17_06_2026.sql`).
2. Naye database engine PGAdmin open karke or shell prompt command se empty database check prepare karein:
   ```sql
   CREATE DATABASE fmcg_shop; -- For UAT
   ```
3. Target command prompt open karke database import command trigger karein:
   ```bash
   # Go to PostgreSQL Binary folder
   cd "C:\Program Files\PostgreSQL\16\bin"

   # UAT Schema Import (Restore backups relative path or downloaded path):
   psql -U postgres -d fmcg_shop -f "d:\intelliJ2025\fmcg-shop\fmcg-shop\backups\fmcg_shop_backup_latest.sql"
   ```
   *(Import karte samay master password `9450` enter karein).*

---

### Step 5: Post-Restore Verification
Database import ho jaane ke baad, services ko register aur start karne ke liye:
* NSSM background services setup check karne ke liye **`d:\intelliJ2025\fmcg-shop\fmcg-shop\scripts\services\services_setup_and_debug_sop.md`** dekhein.
* Project compilation aur global commands setup ke liye **`d:\intelliJ2025\fmcg-shop\fmcg-shop\scripts\build\build_and_deployment_sop.md`** dekhein.

---

## 🌐 3. Domain Swap & Public IP Management SOP

### A. ISP Server Public IP badal jaye toh (No Action Needed)
* **Status**: Hum static Cloudflare Tunnel connectivity utilize kar rahe hain.
* **Why**: Tunnel internal outgoing connectivity models call target mapping establish karta hai. System local network, router, ya internet service provider change hone par public domain names mapping **automatic switch ho jati hai without local updates.**

### B. New Domain Swap settings (Cloudflare Settings)
Agar naya domain buy kiya hai aur purane website URL routes replace karne hain:
1. Login to **Cloudflare Dashboard**.
2. **Websites** section click **"Add Site"** input your new domain name (Select free tier). Replace nameserver configuration in your domain registrar records panel.
3. Sidebar select **Access -> Tunnels** options.
4. Active tunnel edit option check click parameters details.
5. Select **Public Hostname** configurations tab.
6. Edit targeted route mapping:
   - Route domain: `new-uat.laritraders.in` -> local service link: `http://localhost:8085` (UAT instance)
7. Save adjustments parameters. Ab online connections automatic naye routes catch up karne lagenge!
