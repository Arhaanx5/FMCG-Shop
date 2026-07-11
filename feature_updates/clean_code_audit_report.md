# 🔍 Spring Boot Clean-Code Audit Report
**Project**: `com.shop.modules.*`  
**Date**: July 12, 2026  
**Method**: Graphify AST + PowerShell line-count + grep analysis

---

## 📊 God Class Size Summary

| File | Lines | Public Methods | Verdict |
|---|---|---|---|
| `BillService.java` | **2057** | **28** | 🔴 God Class |
| `DashboardService.java` | **1373** | **9** | 🔴 God Class |
| `StockService.java` | **777** | **40** | 🔴 God Class |
| `KhataService.java` | **787** | **9** | 🟠 Oversized |
| `DashboardAiService.java` | **664** | **5** | 🟠 Oversized |
| `StockReportService.java` | **617** | **15** | 🟠 Oversized |
| `BackupService.java` | **532** | **8** | 🟠 Oversized |
| `StockController.java` | **465** | **19** | 🟠 Fat Controller |
| `CustomerController.java` | **235** | **20** | 🟡 Watch |
| `DeliveryService.java` | **376** | **11** | 🟡 Watch |

---

## 🔴 HIGH PRIORITY

---

### H-1 — `BillService.java` — Biggest God Class
**File**: `billing/BillService.java`  
**Size**: 2057 lines · 28 public methods  
**Problem**: Ek hi class me 7+ alag responsibilities hain:

| Responsibility | Lines (approx) |
|---|---|
| Bill creation + FIFO deduction | L342–L930 |
| DTO mapping (`toResponse()`) | L66–L200 |
| NPA/credit limit validation | L413, L720, L1673, L1798, L1941 (5 places!) |
| Bill return + stock restore | L1014–L1200 |
| Bill update/edit + audit trail | L1325–L1500 |
| Bill confirm + bulk confirm | L1772–L1960 |
| Bill snapshot logic | L1960+ |

**Duplicate Logic Found**: NPA + credit limit check **5 times** across `createBill()`, `updateBill()`, `confirmBill()`, `bulkConfirm()` — exact same code copy-pasted.

**Suggested Decomposition** (5 new classes):
```
BillCreationService.java      — createBill(), draft/confirm logic
BillReturnService.java        — returnItems(), stock restore
BillUpdateService.java        — updateBill(), audit trail
BillMapper.java               — toResponse(), toItemResponse()
BillCreditValidator.java      — NPA check + credit limit (single source of truth)
```

**Refactor Risk**: 🔴 HIGH — `BillService` is used by:
- `BillController`, `DashboardService`, `BackupService`, `DeliveryService`, tests (70 tests)

---

### H-2 — `DashboardService.java` — Cross-Module God Class
**File**: `dashboard/DashboardService.java`  
**Size**: 1373 lines · 9 public methods  
**Problem**: **14 dependencies** injected in constructor — ye Spring me "Constructor Injection Hell" ka classic sign hai:

```java
// Line 51–64 — 14 injected dependencies:
BillRepository, CustomerRepository, ProductRepository,
StockBatchRepository, StockRepository, DeliveryRepository,
ExpenseRepository, UserRepository, AreaRepository,
PaymentRepository, DamageLogRepository,
BillService, CustomerService, BackupService
```

**SRP Violation**: Ek service me sales reporting + inventory reporting + staff tracking + credit alerts + backup status sab mix hai.

**Also**: Line 312–322 me `calculateEffectiveCreditLimit()` **twice** call ho rahi hai same loop me — N+1 style wasteful computation:
```java
.filter(c -> customerService.calculateEffectiveCreditLimit(c) ...)  // call 1
.map(c -> ...creditLimit(customerService.calculateEffectiveCreditLimit(c)))  // call 2
```

**Suggested Decomposition** (4 new classes):
```
SalesReportService.java         — daily/monthly sales, trends
InventoryAlertService.java      — low stock, expiry alerts
StaffPerformanceService.java    — salesman performance, GPS
DashboardAggregatorService.java — compose all into DashboardResponse
```

**Refactor Risk**: 🟠 MEDIUM — Only `DashboardController` consumes this.

---

### H-3 — `StockService.java` — 40 Public Methods
**File**: `stock/StockService.java`  
**Size**: 777 lines · **40 public methods** — highest method count in entire codebase  
**Problem**: 5 different responsibilities in one class:

| Responsibility | Methods |
|---|---|
| FIFO deduction logic | `deductByPrimary()`, `deductBySecondary()`, `deductFromBatches()` |
| Stock restoration (return) | `addBackStock()`, `addBackStockToBatch()`, `restoreStockToBatches()` |
| Offer/free unit management | `addBackOfferStock()`, `deductOfferUnits()` |
| Batch lifecycle | `writeOffExpiredBatch()`, `getOrCreateStock()` |
| Damage marking | `markBatchDamage()` |

> Note: `StockService` is **already partially split** — `StockReceiveService`, `StockInventoryService`, `StockMovementService`, `StockReportService`, `StockBIService` exist. But core `StockService` is still 777 lines.

**Suggested Decomposition** (2 new classes):
```
StockDeductionService.java    — all deduct/FIFO methods
StockRestorationService.java  — addBack/restore methods
```
Move `markBatchDamage()` → `DamageService`

**Refactor Risk**: 🔴 HIGH — `StockService` used by `BillService`, `DamageService`, `StockController` (3 major modules).

---

### H-4 — Duplicate NPA/Credit Validation — `BillService.java`
**File**: `billing/BillService.java`  
**Problem**: Exact same NPA + credit limit check block appears at **5 locations**:
- Line 413 (`createBill`)
- Line 720 (`createBill` — inner loop)
- Line 1673 (`updateBill`)
- Line 1798 (`confirmBill`)
- Line 1941 (`bulkConfirmBills`)

```java
// 5 times copy-pasted:
if (Boolean.TRUE.equals(customer.getIsNpa()) && !"CASH".equals(paymentMode))
    throw new RuntimeException("Credit sales are blocked for NPA customer...");
if (customer.getTotalPending().compareTo(creditLimit) > 0)
    throw new RuntimeException("Credit limit exceeded for customer...");
```

**Suggested Fix**: Extract to `BillCreditValidator.java`:
```java
@Component
public class BillCreditValidator {
    public void validateCreditEligibility(Customer customer, String paymentMode) { ... }
}
```
**Risk**: 🟠 MEDIUM — All 5 call sites need update, but change is localized to one file.

---

### H-5 — `new RestTemplate()` Inside Method Bodies (5 Files)
**Files**:
- `dashboard/DashboardAiService.java` — Line 370, 534
- `khata/KhataAiService.java` — Line 49
- `stock/InvoiceOcrController.java` — Line 85
- `customer/AiReminderGenerator.java` — Line 69
- `customer/WhatsAppService.java` — Line 23 (field-level inline)

**Problem**: `new RestTemplate()` created **inside method body or as field** — not Spring-managed, no timeout config, no connection pooling, untestable (can't mock).

```java
// Anti-pattern:
RestTemplate restTemplate = new RestTemplate(); // ❌ — no timeout, no pooling
```

**Suggested Fix**:
```java
// In config/AppConfig.java — single Spring-managed bean:
@Bean
public RestTemplate restTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(45_000);
    factory.setReadTimeout(45_000);
    return new RestTemplate(factory);
}
// Then inject in each service via constructor
```
**Risk**: 🟢 LOW — Config-only change, no business logic affected.

---

## 🟠 MEDIUM PRIORITY

---

### M-1 — Entity→DTO Mapping Inline in 12 Services (No Mapper Layer)
**Files affected**:
`BillService`, `CustomerService`, `KhataService`, `ProductService`,
`UserService`, `ExpenseService`, `DamageService`, `HsnCategoryMappingService`,
`ShopProfileService`, `AreaService`, **`DeliveryController`** (in controller! — wrong layer)

**Pattern found** (`toResponse()` private method inside service):
```java
// CustomerService.java, ProductService.java, KhataService.java etc:
private CustomerResponse toResponse(Customer c) {
    return CustomerResponse.builder()...build();  // 20-30 lines inline
}
```

**Layer Violation**: `DeliveryController.java` has `toResponse()` — mapping logic belongs in service/mapper, not controller.

**Suggested Fix**: Add `*Mapper.java` classes per module (or use MapStruct):
```
billing/dto/BillMapper.java
customer/dto/CustomerMapper.java
delivery/dto/DeliveryMapper.java
product/dto/ProductMapper.java
khata/dto/KhataMapper.java
```
**Risk**: 🟡 LOW-MEDIUM — Internal refactor only, no API change.

---

### M-2 — `StockController.java` — Fat Controller (19 methods, 465 lines)
**File**: `stock/StockController.java`  
**Problem**: 19 endpoints in one controller — handles batch management + inventory + purchases + adjustments + damage + write-offs + OCR.

**Suggested Decomposition**:
```
StockBatchController.java     — getBatches(), topUp(), getBatchHistory()
StockPurchaseController.java  — receiveStock(), getPurchases(), getBatchesByInvoice()
(keep StockController lean — stock overview + adjustment only)
```
**Risk**: 🟢 LOW — URL routing change only, business logic stays in services.

---

### M-3 — `CustomerController.java` — WhatsApp Mixed into Customer Domain
**File**: `customer/CustomerController.java`  
**Problem**: `CustomerController` injects `WhatsAppService` and exposes WhatsApp status/QR/logout endpoints. These are infrastructure endpoints, not customer-domain.

```java
// Line 23 — wrong place:
private final WhatsAppService whatsAppService;

// These don't belong in CustomerController:
getWhatsAppStatus()  // L27
getWhatsAppQr()      // L38
logoutWhatsApp()     // L50
```

**Suggested Fix**: Extract to `notification/WhatsAppController.java`  
**Risk**: 🟢 LOW — API path change (`/api/customers/whatsapp` → `/api/whatsapp`), frontend update needed.

---

### M-4 — `BackupService` Cross-Module Boundary in DashboardService
**File**: `dashboard/DashboardService.java` line 64  
**Problem**: `BackupService` (infra concern) injected into `DashboardService` (business reporting) — wrong module boundary. Backup status check runs on every dashboard load.

**Suggested Fix**: Expose `/api/dashboard/system-status` as a **separate endpoint** in `DashboardController` that independently calls `BackupService`. Remove from main `getDashboard()` aggregation.  
**Risk**: 🟢 LOW — Backend-only structural change.

---

## 🟡 LOW PRIORITY

---

### L-1 — Test Coverage Gap — 33 Services, Only 8 Test Files
**Existing test files**:
```
FmcgShopBusinessTests.java         ← billing + stock integration
FmcgShopHighSeverityBugsTests.java ← stock/billing bugs
FmcgShopBugsVerificationTests.java ← billing bugs
FmcgShopMediumLowBugsTests.java    ← medium bugs
AuthControllerMfaTest.java         ← MFA
TotpUtilTest.java                  ← TOTP
GstBillingAndFilingTest.java       ← GST compliance
VerifyPassword.java                ← password utility
```

**Services with ZERO dedicated unit tests**:

| Service | Lines | Risk |
|---|---|---|
| `DashboardService` | 1373 | 🔴 Critical |
| `KhataService` | 787 | 🔴 Critical |
| `StockReportService` | 617 | 🟠 High |
| `BackupService` | 532 | 🟠 High |
| `DeliveryService` | 376 | 🟠 High |
| `CustomerService` | 352 | 🟠 High |
| `WhatsAppService` | 237 | 🟡 Medium |
| `ReceivablesService` | 221 | 🟡 Medium |
| `RouteOptimizationService` | 216 | 🟡 Medium |
| `ReconciliationService` | 145 | 🟡 Medium |
| `AreaService` | 137 | 🟡 Medium |
| `ExpenseService` | 107 | 🟡 Medium |

---

### L-2 — `WhatsAppService.java` — Wrong Package
**Current location**: `modules/customer/WhatsAppService.java`  
**Problem**: Used by 3 different modules:
- `ReceivablesService` (khata module)
- `CODWhatsAppService` (delivery module)
- `CustomerController` (customer module)

It's a **shared cross-cutting infrastructure service** — should not live inside the `customer` domain.

**Suggested Fix**: Move to `modules/notification/WhatsAppService.java`  
**Risk**: 🟢 LOW — Package rename + import update in 3 files.

---

### L-3 — N+1 Risk: `calculateEffectiveCreditLimit()` Called 2× Per Customer
**File**: `dashboard/DashboardService.java` lines 314–321  
**Problem**: Inside a stream over ALL active customers, same method called twice:
```java
.filter(c -> customerService.calculateEffectiveCreditLimit(c) > ...)  // DB call #1
.map(c -> ...creditLimit(customerService.calculateEffectiveCreditLimit(c)))  // DB call #2
```
If `calculateEffectiveCreditLimit()` does a DB query → 2 queries per customer.  
With 500 customers = **1000 unnecessary queries per dashboard load**.

**Suggested Fix** (compute once):
```java
activeCustomers.stream()
    .map(c -> Map.entry(c, customerService.calculateEffectiveCreditLimit(c)))
    .filter(e -> e.getKey().getTotalPending().compareTo(e.getValue()) > 0)
    .map(e -> CreditLimitAlert.builder()
        .creditLimit(e.getValue())  // reuse, no second call
        ...build())
```
**Risk**: 🟢 LOW — Logic-only optimization, same output.

---

### L-4 — `KhataService.java` — Oversized (787 lines, 9 methods)
**File**: `khata/KhataService.java`  
**Problem**: Average method is ~87 lines each — every method is doing too much internally. Also mixes:
- Customer outstanding balance calculation
- Payment entry + validation
- Khata ledger fetching
- NPA flagging logic

**Suggested Fix** (no immediate split needed, but watch):
```
KhataLedgerService.java    — read-only: getHistory(), getBalance()
KhataPaymentService.java   — write: recordPayment(), waiveOff()
```
**Risk**: 🟢 LOW — Only `KhataController` consumes this.

---

## 📋 Complete Priority Summary Table

| # | Priority | File | Problem | New Classes Needed | Refactor Risk |
|---|---|---|---|---|---|
| H-1 | 🔴 HIGH | `BillService.java` | God Class (2057L, 7 responsibilities) | `BillCreationService`, `BillReturnService`, `BillUpdateService`, `BillMapper`, `BillCreditValidator` | 🔴 HIGH |
| H-2 | 🔴 HIGH | `DashboardService.java` | 14 deps, 5 mixed domains (1373L) | `SalesReportService`, `InventoryAlertService`, `StaffPerformanceService`, `DashboardAggregatorService` | 🟠 MEDIUM |
| H-3 | 🔴 HIGH | `StockService.java` | 40 public methods (777L) | `StockDeductionService`, `StockRestorationService` | 🔴 HIGH |
| H-4 | 🔴 HIGH | `BillService.java` | NPA/credit check duplicated 5× | `BillCreditValidator` (1 class only) | 🟠 MEDIUM |
| H-5 | 🔴 HIGH | 5 files | `new RestTemplate()` inline — untestable | `AppConfig.java` RestTemplate `@Bean` | 🟢 LOW |
| M-1 | 🟠 MED | 12 services + 1 controller | `toResponse()` inline, no Mapper layer | 1 `*Mapper.java` per module (~6 files) | 🟡 LOW-MED |
| M-2 | 🟠 MED | `StockController.java` | Fat controller (19 methods, 465L) | `StockBatchController`, `StockPurchaseController` | 🟢 LOW |
| M-3 | 🟠 MED | `CustomerController.java` | WhatsApp endpoints in customer controller | `WhatsAppController` | 🟢 LOW |
| M-4 | 🟠 MED | `DashboardService.java` | `BackupService` cross-boundary injection | API endpoint separation | 🟢 LOW |
| L-1 | 🟡 LOW | 12 services | Zero unit test coverage | 12 new `*Test.java` files | 🟢 LOW |
| L-2 | 🟡 LOW | `WhatsAppService.java` | Wrong package (`customer/` vs `notification/`) | Package rename | 🟢 LOW |
| L-3 | 🟡 LOW | `DashboardService.java` | `calculateEffectiveCreditLimit()` 2× per customer | Stream optimization (no new file) | 🟢 LOW |
| L-4 | 🟡 LOW | `KhataService.java` | Oversized (787L), no unit tests | `KhataLedgerService`, `KhataPaymentService` | 🟢 LOW |

---

## 🎯 Recommended Refactor Order (Sprint-wise)

```
Sprint A — SAFEST, HIGH ROI
  → H-4: BillCreditValidator (remove 5× duplicate NPA check)
  → H-5: RestTemplate @Bean in AppConfig.java

Sprint B — MEDIUM COMPLEXITY
  → M-2: Split StockController
  → M-3: WhatsAppController extract
  → L-2: Move WhatsAppService to notification/
  → L-3: Fix double calculateEffectiveCreditLimit()

Sprint C — HIGH COMPLEXITY (plan carefully)
  → H-1: BillService decomposition (5 new classes)
  → H-3: StockService deduction/restoration split

Sprint D — REPORTING LAYER
  → H-2: DashboardService split (4 new classes)
  → L-4: KhataService split

Sprint E — QUALITY & COVERAGE
  → M-1: Mapper layer for all modules
  → L-1: Unit tests for 12 untested services
```

---

*Report generated from live codebase — Graphify AST graph (7094 nodes · 15465 edges) + PowerShell line-count + grep pattern analysis. No files were modified during audit.*
