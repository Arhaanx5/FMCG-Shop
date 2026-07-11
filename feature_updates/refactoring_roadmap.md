# 📋 Lari Traders — Refactoring Roadmap & Master Reference Document

This document serves as the master reference for the entire clean-code refactoring journey of the FMCG Shop codebase. It contains the complete sprint plan, target package structure, and progress tracking.

---

## 📋 Sprint Roadmap

### ✅ Sprint 0 — `CustomerLedgerService` (DONE)
* **Description**: Extracted duplicate `recalculateCustomerPending` logic from `BillService` and `KhataService` to a neutral shared location at `com.shop.common.ledger.CustomerLedgerService`.
* **Verification**: Verified via database snapshot matching before/after balances, and 5 new unit tests.

### ✅ Sprint A — `BillCreditValidator` (DONE)
* **Description**: NPA/credit-limit checks (previously duplicated in 4 locations across `BillService.java`) extracted to `com.shop.modules.billing.validator.BillCreditValidator`.
* **Verification**: Side-by-side logic diff verification complete, and verified via 16 dedicated unit tests.

### ✅ Sprint B — RestTemplate Beans (DONE)
* **Description**: Convert inline `new RestTemplate()` calls (`DashboardAiService`, `KhataAiService`, `InvoiceOcrController`, `AiReminderGenerator`) to proper `@Bean` configuration in `config/AppConfig.java` with connection and read timeouts.
* **Verification**: Verified via clean unit test execution.

### ✅ Sprint C — `KhataService` Decompose (DONE)
* **Description**: Extracted payment orchestration logic into specialized components: `PaymentRecordingService.java`, `PaymentPreviewService.java`, `PaymentReversalService.java`, and `KhataMapper.java`, converting `KhataService` to a thin orchestrator.
* **Verification**: Verified using existing manual mock setups and unified test suites.

### ⬜ Sprint D — `BillService` Decompose (Pending)
* **Target**: Breakdown the monolithic billing service (highest complexity):
  - `BillCreationService.java` — `createBill()`
  - `BillUpdateService.java` — `updateBillDetails()`
  - `BillCancellationService.java` — `cancelBill()`, `returnItems()`, `restoreBill()`
  - `BillConfirmationService.java` — `confirmBill()`, `bulkConfirmBills()`
  - `BillMapper.java` — `toResponse()`, `getBillSnapshotJson()`
  - `BillCalculationHelper.java` — `getRateForUnit()`, `generateBillNumber()`, `getSafeSecondaryPerPrimary()`, `checkStockAvailability()`
  - `BillService.java` → thin orchestrator (query methods + delegation)

### ⬜ Sprint E — `DashboardService` Decompose (Pending)
* **Target**: Splitting reporting aggregation:
  - `SalesReportService.java` — `getMonthlyReport()`, `getYearlyReport()`, `getTrendData()`
  - `DashboardSummaryService.java` — `getTodaySummary()`, `getDashboardSummary()`
  - `SalesmenPerformanceService.java` — `getSalesmenPerformance()`
  - `DashboardCalculationHelper.java` — `calculateCollectionBreakdown()`, `calculateTotalInventoryValue()`, `calculateAvgCollectionDays()`
  - **L-3 Fix**: `calculateEffectiveCreditLimit()` double-call optimization.
  - **M-4 Fix**: Decouple `BackupService` to a separate `/api/dashboard/system-status` endpoint.

### ⬜ Sprint F — `StockService` Split (Pending)
* **Target**: Decomposing remaining core operations of the 777-line service:
  - `StockDeductionService.java` — `deductByPrimary()`, `deductFromBatches()`, `deductOfferUnits()`
  - `StockRestorationService.java` — `addBackStock()`, `addBackStockToBatch()`, `addBackOfferStock()`, `restoreStockToBatches()`
  - `StockAdjustmentService.java` — `adjustStock()`, `writeOffExpiredBatch()`
  - Move `markBatchDamage()` to `DamageService`

### ⬜ Sprint G — Mapper Layer (Pending)
* **Target**: Introduce DTO mappers (`*Mapper.java`) in all modules (`dto/` folder) to clean inline `toResponse()` implementations across 12 services and remove controller-level violations (like mapping inside `DeliveryController`).

### ⬜ Sprint H — Controller Cleanup (Pending)
* **Target**: Route separation and infra refactoring:
  - `StockController` → `StockBatchController.java` + `StockPurchaseController.java`
  - Extract WhatsApp endpoints from `CustomerController` to `WhatsAppController.java`.
  - Relocate shared infrastructure `WhatsAppService.java` to `notification/WhatsAppService.java`.

### ⬜ Sprint I — Test Coverage (Pending)
* **Target**: Close coverage gap by writing dedicated unit test classes for `DashboardService`, `KhataService`, `StockReportService`, `BackupService`, and others.

---

## 🏗️ Final Target Codebase Structure

```
src/main/java/com/shop/
│
├── common/
│   └── ledger/
│       └── CustomerLedgerService.java          ✅ (Sprint 0)
│
├── notification/                                 (Sprint H — moved from customer/)
│   └── WhatsAppService.java
│   └── WhatsAppController.java
│
├── config/
│   └── AppConfig.java                            (RestTemplate bean — Sprint B)
│
├── modules/
│   ├── auth/
│   │
│   ├── area/
│   │   └── dto/AreaMapper.java                   (Sprint G)
│   │
│   ├── billing/
│   │   ├── dto/
│   │   │   └── BillMapper.java                   (Sprint D)
│   │   ├── validator/
│   │   │   └── BillCreditValidator.java          ✅ (Sprint A)
│   │   ├── BillCreationService.java              (Sprint D)
│   │   ├── BillUpdateService.java                (Sprint D)
│   │   ├── BillCancellationService.java          (Sprint D)
│   │   ├── BillConfirmationService.java          (Sprint D)
│   │   ├── BillCalculationHelper.java            (Sprint D)
│   │   └── BillService.java                      (thin orchestrator — Sprint D)
│   │
│   ├── customer/
│   │   ├── dto/CustomerMapper.java                (Sprint G)
│   │   ├── CustomerService.java
│   │   └── CustomerController.java                (WhatsApp removed — Sprint H)
│   │
│   ├── stock/
│   │   ├── StockDeductionService.java             (Sprint F)
│   │   ├── StockRestorationService.java           (Sprint F)
│   │   ├── StockAdjustmentService.java            (Sprint F)
│   │   ├── StockService.java                      (thin — Sprint F)
│   │   ├── StockBatchController.java              (Sprint H)
│   │   └── StockPurchaseController.java           (Sprint H)
│   │
│   ├── khata/
│   │   ├── dto/KhataMapper.java                   (Sprint C)
│   │   ├── PaymentRecordingService.java           (Sprint C)
│   │   ├── PaymentPreviewService.java             (Sprint C)
│   │   ├── PaymentReversalService.java            (Sprint C)
│   │   └── KhataService.java                      (thin — Sprint C)
│   │
│   ├── dashboard/
│   │   ├── SalesReportService.java                (Sprint E)
│   │   ├── DashboardSummaryService.java           (Sprint E)
│   │   ├── SalesmenPerformanceService.java        (Sprint E)
│   │   ├── DashboardCalculationHelper.java        (Sprint E)
│   │   └── DashboardService.java                  (thin — Sprint E)
│   │
│   └── backup/
│       └── BackupService.java                      (Dashboard se decoupled — Sprint E)
```

---

## 📊 Progress Tracker

| Sprint | Module | Status | Risk |
|---|---|---|---|
| 0 | CustomerLedgerService | ✅ Done | 🟢 Low |
| A | BillCreditValidator | ✅ Done | 🟠 Medium |
| B | RestTemplate Beans | ✅ Done | 🟢 Low |
| C | KhataService Split | ✅ Done | 🟠 Medium |
| D | BillService Split | ⬜ Pending | 🔴 High |
| E | DashboardService Split | ⬜ Pending | 🟠 Medium |
| F | StockService Split | ⬜ Pending | 🔴 High |
| G | Mapper Layer (all modules) | ⬜ Pending | 🟡 Yellow |
| H | Controller Cleanup | ⬜ Pending | 🟢 Low |
| I | Test Coverage | ⬜ Pending | 🟢 Low |

---

> **UAT Deployment Note**: After each sprint, compile, run all tests, and verify correct API response signatures to ensure no functional regressions are introduced.
