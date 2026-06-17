# 💬 Lari Traders (UAT) — WhatsApp Operations & Debug SOP

Bhai, automated bulk reminders backend system design aur Puppeteer process lock problems ko solve karne ke instructions niche hain.

---

## 🏗️ 1. WhatsApp Reminders Architecture

Reminders system local microservice aur headless browser logic utilize karta hai:

```
[React / Android App UI]
       │
       ▼ (Start Broadcast trigger hone par)
[Spring Boot Backend (Port 8085)]
       │
       ▼ (REST API connection checks)
[Local Node.js Helper (Port 3000)] ──► [Headless Chrome (Puppeteer)] ──► [Customer Phone]
```

1. **Spring Boot Backend**: UAT DB (`fmcg_shop`) se pending payment details gather karke templates compile karta hai.
2. **Node.js Helper**: Puppeteer library through dynamic headless browser open karke real WhatsApp Web instances spin-up aur coordinate karta hai.
3. **Spam Protection Delay**: WhatsApp account secure rakhne ke liye humne messages delivery interval threads me **2.5 seconds delay** (`Thread.sleep(2500)`) set kiya hai.

---

## 🔍 2. Locked Processes & Port 3000 Detection (Check/Detect)

Headless browser (Chrome.exe) crash hone par session folders lock rehte hain aur QR code dynamic scanning fail ho jati hai. Status detect karne ke liye:

### A. Port 3000 listener check karein:
```powershell
Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess
```
*Port active hone par ye process PID print karega.*

### B. Locked Puppeteer Chrome processes identify karein:
```powershell
Get-CimInstance Win32_Process -Filter "Name = 'chrome.exe'" | Where-Object { $_.CommandLine -like "*puppeteer*" } | Select-Object ProcessId, CommandLine | Format-List
```
*Ye command normal chrome windows ko ignore karke sirf WhatsApp relative Puppeteer chrome windows ka path aur PID display karega.*

---

## 🛠️ 3. Locked Processes Force Kill & Cache Reset (Kill/Terminate)

Agar WhatsApp QR generate na ho ya link scan processing stuck ho jaye, toh run Admin PowerShell:

### Step 1: Terminate Node.js Port 3000
```powershell
$conn = Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue
if ($conn) {
    Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    Write-Host "Port 3000 Node process terminated!"
}
```

### Step 2: Terminate Locked Chrome instances
```powershell
Get-CimInstance Win32_Process -Filter "Name = 'chrome.exe'" | Where-Object { $_.CommandLine -like "*puppeteer*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
```
*Ye command bina normal browser tabs band kiye sirf locked script-based chrome processes end karegi.*

### Step 3: Clear Corrupted Session Cache
```powershell
Remove-Item -Recurse -Force "d:\intelliJ2025\fmcg-shop\fmcg-shop\whatsapp-service\session_data" -ErrorAction SilentlyContinue
```

### Step 4: Restart Service
```powershell
Start-Service fmcg-whatsapp
```
*Ab admin panel par refresh maarkar QR code refresh generate karein!*
