# 🧾 Walkthrough: RestTemplate Bean Configuration Refactoring (Sprint B)

Humne pure Spring Boot codebase se inline `new RestTemplate()` instantiation aur local RequestFactory configurations ko successfully remove karke Spring-managed custom beans me convert kar diya hai. 

Isse HTTP requests hang hone, socket leakage hone aur thread blocking ki risk completely khatam ho gayi hai.

---

## ⚙️ Phase 1 — Config Class Creation
Humne ek central config file banayi:
- **Class Path**: [`config/AppConfig.java`](file:///d:/intelliJ2025/fmcg-shop/fmcg-shop/src/main/java/com/shop/config/AppConfig.java)
- Isme **teen distinct beans** declare kiye taaki timeouts aur security interceptors isolate rahein:
  1. `@Primary RestTemplate`: Connect/Read timeout **45 seconds** (Default use ke liye - like OCR scan aur general calls).
  2. `@Qualifier("shortTimeoutRestTemplate")`: Connect timeout **4 seconds**, Read timeout **6 seconds** (Gemini AI text generator calls ke liye).
  3. `@Qualifier("whatsAppRestTemplate")`: Connect/Read timeout **45 seconds** (WhatsApp service ke liye).

---

## ⚙️ Phase 2 — Service Refactoring (Constructor Injection)
Inline calls ko delete karke pre-configured beans ko constructor-inject kiya:

### 1. `DashboardAiService.java`
- Primary `RestTemplate` inject kiya.
- `callStructuredOcrWithRetry()` ke andar se local request factory configuration (`setConnectTimeout(45000)`) aur `new RestTemplate(factory)` calls ko remove kiya.
- `callPythonTextGen()` ke andar se `new RestTemplate()` call ko delete kiya.

### 2. `KhataAiService.java`
- Primary `RestTemplate` inject kiya.
- `generateReminder()` method ke andar se local `new RestTemplate()` instantiation ko delete kiya.

### 3. `InvoiceOcrController.java`
- Primary `RestTemplate` field inject kiya (using Lombok `@RequiredArgsConstructor`).
- `/upload` api ke andar se `new RestTemplate()` hataya.

### 4. `WhatsAppService.java`
- `private final RestTemplate restTemplate = new RestTemplate();` field declaration ko remove kiya.
- Isko manual constructor ke through `@Qualifier("whatsAppRestTemplate")` bean inject kiya.
- Isse custom security headers interceptor (`x-internal-secret` inject karne wala) isi specific bean instance tak isolated raha, dusre global calls affect nahi hue.

### 5. `AiReminderGenerator.java`
- `@Qualifier("shortTimeoutRestTemplate")` inject kiya.
- Gemini API text generator block ke andar se manual request factory timeouts (4000ms / 6000ms) aur local `new RestTemplate()` logic ko remove kiya.

---

## 🧪 Phase 3 — Test Classes Updates
Naye dependency injection/constructor structure ke chalte testing manually instantiations me compile errors aa rahe the, jise humne resolve kiya:

### 1. `FmcgShopBusinessTests.java`
- `setUp()` method me `DashboardAiService` constructor instantiation me mock RestTemplate pass kiya: `mock(RestTemplate.class)` as 11th parameter.
- Gemini formatting tests (`testAiReminderGeneratorFallbackAndFormatting()` aur `testAiReminderGeneratorFallbackNoShopName()`) me subclass test generator `super()` constructors ko modified kiya taaki mock RestTemplate call pass ho sake (`super(mock(RestTemplate.class))`).

---

## 📊 Final Test Run Result

Humne compile-check aur complete test verification perform kiya:

```
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  18.433 s
[INFO] Tests run: 81, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
```

* **81 tests run, 0 failures, 0 errors** - Green check passed.
* Codebase Knowledge Graph updated successfully.
