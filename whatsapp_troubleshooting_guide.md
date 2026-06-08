# WhatsApp Service Troubleshooting Guide (Hinglish)

Jab aapka WhatsApp helper link nahi hota ya stuck ho jata hai, to aksar background me chal rahe **headless Chrome (Puppeteer) processes** session cache ko lock kar dete hain. 

Is document me step-by-step bataya gaya hai ki in locked processes ko kaise check, detect aur kill (terminate) kiya jata hai.

---

## 1. Kaise Pata Karein Ki Kon-Kon Se Processes Chal Rahe Hain? (Check/Detect)

### Step A: Port 3000 Pe Chal Rahi Service Ka Pata Lagana
WhatsApp service default me port `3000` pe listen karti hai. Is port pe kaunsa process active hai ye dekhne ke liye:

**PowerShell Command:**
```powershell
Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess
```
*   **Kya hota hai isse?** Agar koi process port 3000 use kar raha hoga, to ye uska PID (Process ID) print karega. Agar kuch print nahi hota, to iska matlab port free hai.

---

### Step B: Headless Chrome Processes Ko Dhundhna (Detecting Locked Sessions)
WhatsApp Web ko chalane ke liye Puppeteer library background me Google Chrome ka ek private headless version chalti hai. Agar ye freeze ho jaye, to Windows iske files ko lock kar deta hai.

Normal user Chrome aur Puppeteer Chrome ke beech ka farq dekhne ke liye ye command chalayein:

**PowerShell Command:**
```powershell
Get-CimInstance Win32_Process -Filter "Name = 'chrome.exe'" | Where-Object { $_.CommandLine -like "*puppeteer*" } | Select-Object ProcessId, CommandLine | Format-List
```
*   **Kaise pata lagaya humne?** 
    *   Normal Chrome normal use me chalta hai.
    *   Lekin Puppeteer Chrome ke launch arguments (CommandLine) me `--user-data-dir` ke andar `whatsapp-service\session_data` ka path aur `--no-sandbox` jaise flags hote hain.
    *   Ye command sirf unhi Chrome processes ko select karke unka `ProcessId` aur path dikhayega jo WhatsApp service ke liye chal rahe hain.

---

## 2. Locked Processes Ko Kaise Band Karein? (Kill/Terminate)

### Step A: Port 3000 Ke Process Ko Force Kill Karna
Port 3000 par chal rahe Node process ko band karne ke liye (Administrator Command Prompt ya PowerShell me):

**PowerShell Command:**
```powershell
$conn = Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue
if ($conn) {
    Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    Write-Host "Port 3000 stopped successfully!"
}
```

---

### Step B: Locked Puppeteer Chrome Processes Ko Force Kill Karna
Sirf un Chrome windows ko band karne ke liye jo background me session files ko lock kiye huye hain:

**PowerShell Command:**
```powershell
Get-CimInstance Win32_Process -Filter "Name = 'chrome.exe'" | Where-Object { $_.CommandLine -like "*puppeteer*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
```
*   **Kya karta hai ye?** Ye background me chal rahe saare system Chrome processes ko scan karta hai, jiske command-line path me `"puppeteer"` word ho, unhe force kill (`Stop-Process -Force`) kar deta hai. Isse aapki personal open Google Chrome tabs band **nahi** hongi.

---

## 3. Session Cache Folder Clear Karna

Jab saare Chrome processes kill ho jayein, tab aap safely corrupt cache directory ko delete kar sakte hain taaki login fresh ho sake:

**PowerShell Command:**
```powershell
Remove-Item -Recurse -Force "whatsapp-service/session_data" -ErrorAction SilentlyContinue
```

---

## 4. Helper Service Ko Background me Chalu Karna

### Option A: Windows Background Service Ke Tarike Se (Best - No Taskbar Window)
```powershell
Start-Service fmcg-whatsapp
```
*   **Fayda:** Ye bilkul silent chalti hai, taskbar me command prompt ka black window nahi dikhta.

### Option B: Terminal Window Ke Sath (Debug Mode)
```powershell
cd whatsapp-service
node server.js
```
*   **Fayda:** Screen par realtime logs dikhte hain ki QR code mila ya device link hua.
