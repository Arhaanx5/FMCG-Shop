# 📝 Lari Traders — Kaam Aasan Karne Wali Build & Deploy Guide (Simple Hinglish)

Bhai, is guide ko padh kar koi bhi naya banda aram se frontend-backend compile kar sakta hai aur saare bat files ko use kar sakta hai. Isme sab kuch aasan language me likha hai.

---

## ❓ 1. Humein iski zaroorat kyun hai? (Kyun karna hai ye process?)

Jab bhi aap React frontend me koi changes karte ho (jaise kisi page ka button badla ya text change kiya), toh wo direct browser me nahi dikhta. React code ko compile karke, backend ke static folder me copy karna padta hai, aur backend server restart karna padta hai. Tabhi changes live dikhte hain.

---

## 📂 2. Kis `.bat` file ka kya kaam hai aur kaise chalan hai?

Humare paas ye main bat files hain:

### A. `fmcg-build.bat` (UI aur API compile karke restart karne ke liye)
* **Kab chalana hai**: Jab bhi frontend (React) ya backend (Java) me naya change live karna ho.
* **Kaise chalana hai**: Is file par **Right-Click** karo aur **"Run as Administrator"** select karo.
* **Ye kya karega**: 
  1. React UI ko build karega.
  2. Nayi files ko Spring Boot backend me copy karega.
  3. Running backend service ko automatic stop karega.
  4. Maven se backend ka fresh JAR package ready karega.
  5. Backend service ko restart kar dega taaki changes live ho jayein.
  6. Sab success hone par press any key kahega, enter maar kar band kar do.

### B. `switch-to-uat.bat` (Test Environment me switch karne ke liye)
* **Kab chalana hai**: Agar testing karni ho, Port `8085` aur test database `fmcg_shop` use karna ho.
* **Kaise chalana hai**: **Right-Click** karke **"Run as Administrator"** chala do.
* **Ye kya karega**: Service stop karega, profile switch karke restart kar dega.

### C. `switch-to-prod.bat` (Live Production me switch karne ke liye)
* **Kab chalana hai**: Jab app final live chalan ho, Port `8086` aur live database `fmcg_shop_prod` par.
* **Kaise chalana hai**: **Right-Click** karke **"Run as Administrator"** chala do.
* **Ye kya karega**: Service stop karega, profile prod par switch karke wapas start kar dega.

### D. `install-whatsapp-service.bat` (WhatsApp background service setup ke liye)
* **Kab chalana hai**: Naye system par background automatic WhatsApp helper (NodeJS) service set karne ke liye.
* **Kaise chalana hai**: **Right-Click** karke **"Run as Administrator"** chala do.
* **Ye kya karega**: Background service active kar dega (port `3000` par scan/listen karne ke liye).

---

## ⚙️ 3. Kaun-Kaun si background services hamesha chalu honi chahiye?

Reminders aur system chalane ke liye Windows me ye teen services hamesha **Running** rehni chahiye:
1. **`fmcg-backend`** (Main shop management chalane ke liye)
2. **`fmcg-whatsapp`** (WhatsApp par bulk reminders automatic send karne ke liye)
3. **`Cloudflared`** (Domain secure tunnel maintain rakhne ke liye taaki website pure internet par chale)

---

## 💻 4. Naye System me `fmcg-build` command ko kahi se bhi chalane ke liye (PATH Setup)

Agar aap chahte ho ki aapko bar-bar folder ke andar na jana pade, aur kisi bhi CMD window me direct `fmcg-build` type karke project build ho jaye, toh ise set karne ke do tarike hain:

### Tarika A: Sabse aasan (NodeJS/npm ke through copy-paste)
Agar system me node/npm installed hai:
1. Keyboard par **`Win + R`** (Run box) dabao, usme **`shell:appdata`** likho aur Enter dabao.
2. Ek folder khulega, uske andar **`npm`** folder ke andar jao (Path hoga: `C:\Users\<AapkaUsername>\AppData\Roaming\npm`).
3. Project root ya download folder me se **`fmcg-build.bat`** file ko copy karo aur is `npm` folder ke andar direct paste kar do.
4. *Kaam khatam!* Ab kisi bhi CMD window ko run as admin karo, aur direct type karo `fmcg-build`, project automatic deploy ho jayega!

---

### Tarika B: Windows settings se path set karna (Standard Setup)
Agar NodeJS installed nahi hai ya standard tarika chahiye:
1. Jo updated **`fmcg-build.bat`** file hai, use system ke kisi permanent folder me rakh do (Jaise: `D:\scripts\fmcg-build.bat`).
2. Apne keyboard me **Win key** dabao aur search karo: **"Environment Variables"** (ya system properties).
3. **"Edit the system environment variables"** par click karo.
4. Ek box khulega, niche **"Environment Variables..."** button par click karo.
5. Upar wale box me **`Path`** namak line ko select karo aur **"Edit..."** button par click karo.
6. Right side me **"New"** button par click karo, aur us folder ka path likho jaha bat file rakhi hai (Jaise: `D:\scripts` or folder path).
7. Har jagah **"OK"** -> **"OK"** -> **"OK"** dabakar save kar do.
8. Bas! Ab naya command prompt open karke as admin `fmcg-build` likho, setup chalu ho jayega!
