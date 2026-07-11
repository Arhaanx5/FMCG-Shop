# 🧾 FMCG-Shop: GST Billing & Filing Plan (Hinglish)

Is document me humne GST calculations (Inclusive Billing), B2C cash sales, Input Tax Credit (ITC) flow, aur GSTR-1 manual export features ki details ko Hinglish me summarize kiya hai.

---

## 💡 1. Current System: GST Inclusive Billing

Aapki app me abhi already **GST Inclusive Billing** chal rahi hai. Iska mathematical flow aur business details niche hain:

### A. Dynamic Back-Calculation
Aap product create karte waqt sales price **"Incl. Tax" (tax ke sath)** set karte hain (e.g. biscuit box = ₹100). Billing ke waqt backend automatically tax split kar deta hai:
*   `Taxable Subtotal = Total / (1 + GST%/100)` ➡️ (₹100 / 1.05 = ₹95.24)
*   `GST Amount = Taxable Subtotal * GST%/100` ➡️ (₹95.24 * 0.05 = ₹4.76)
*   *Bill Output*: Customer ko invoice me Total ₹100 hi show hoga, lekin backend me ₹4.76 output GST save ho jayega.

### B. Business Value
*   **Zero Audit Risk**: Har transaction ka stock purchase aur sale tax invoices se completely match rehta hai.
*   **Customer Satisfaction**: Customer ko lagta hai use bina extra tax ke flat rate par maal mila hai (use koi extra tax nahi bharna padta).
*   **Claiming ITC (Input Tax Credit)**: Jo GST aapne kharidte waqt diya tha, wo is sale GST se automatically adjust/offset ho jayega. 

---

## 🧾 2. Unregistered Customers & B2C Billing

Wholesale business me kafi retail customers ke paas GSTIN nahi hota. Is case me system aur portal filing ka logic:

*   **App UI me**: Customer status automatically **"Unregistered"** save hoga (GSTIN field blank rahegi). Billing flat price par normal generate ho jayegi.
*   **Filing Output (B2CS)**: GSTR-1 return me in unregistered sales ko **B2CS (B2C Small)** section me summarize kiya jata hai. Isme humein ek-ek bill number dene ki zaroorat nahi hoti, bas pure month ki state-wise total tax summary portal par jaati hai.
*   **ITC Safety**: Unregistered sales dikhane se aapke purchase input GST (ITC) claim par koi issue nahi aayega. 

---

## 📂 3. GSTR-1 JSON/CSV Manual Export Tool

E-filing ko automation ke zariye free aur stable banane ke liye hum manual JSON/CSV export feature ka use karenge:

*   **Feature Setup**:
    1. Reports dashboard me **"Export GSTR-1 [Month]"** button hoga.
    2. Click karte hi government portal ke exact excel/JSON scheme templates download ho jayeinge.
    3. User is file ko directly government portal (`gst.gov.in`) par upload kar dega.
    4. Portal data load kar lega, aur user OTP enter karke self-file kar lega.
*   **Fayda**: CA data entry fees ki poori bachat, zero dependency, aur 100% legal system accuracy.
