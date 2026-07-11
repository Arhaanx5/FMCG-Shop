# 🔍 Consolidated Responsibility Breakdown Report
**Project**: `com.shop.modules.*`  
**Target Classes**: `BillService`, `DashboardService`, `StockService`, `KhataService`  
**Purpose**: Map out all method responsibilities to plan Clean Architecture refactoring.

---

## 🧾 1. `BillService.java` (Billing Module)
*Size: ~2005 lines · 28 public methods*

| Method Name | Scope | Primary Responsibility Type | Description / Responsibilities Mixed |
|---|---|---|---|
| `toResponse` | `private` | **Mapping** | Maps `Bill` JPA Entity to `BillResponse` DTO. Includes inline GST calculation and rounding logic. |
| `recalculateCustomerPending` | `private` | **Calculation / Persistence** | Recalculates outstanding balances for a customer by summing up unpaid invoices and updating the database. |
| `getAllBills` / `getRecentBills` / `getPendingBills` | `public` | **Orchestration / Persistence** | Query orchestration: Fetches bills using JpaRepository and maps them to DTO lists. |
| `getBillById` | `public` | **Orchestration / Validation** | Retrieves a single invoice, checks existence, and maps to DTO. |
| `getCustomerHistory` | `public` | **Orchestration / Persistence** | Fetches historical invoices for a customer with pagination/criteria and maps to DTOs. |
| `createBill` | `public` | **Orchestration / Validation / Calculation / Persistence** | **Massive God Method (mixed concerns):** Loads entities, runs checks, generates sequential numbers, takes shop profile snapshot, calculates GST totals, calls stock service, saves bill, and updates customer pending ledger. |
| `checkStockAvailability` | `private` | **Validation** | Domain validation: Checks if inventory has sufficient virtual or physical stock for primary/secondary units. |
| `getRateForUnit` | `private` | **Calculation** | Conversion helper: Calculates rate depending on Box/Ladi unit configuration. |
| `generateBillNumber` | `private` | **Calculation / Persistence** | Generates sequential bill numbers. |
| `checkPriceOverrideLimits` | `private` | **Validation** | Authorization logic: Validates if the salesman/admin is allowed to override product base rate based on cost limits. |
| `cancelBill` | `public` | **Orchestration / Persistence / Stock Refund** | Cancels confirmed/draft bills, instructs `StockService` to refund stock back to specific batches, updates outstanding customer balance, and alters status. |
| `returnItems` | `public` | **Orchestration / Validation / Calculation / Persistence** | Computes returned quantities, validates return limits against sold amounts, processes partial stock refunds, recalculates invoice total, and adjusts ledger. |
| `deleteBill` | `public` | **Persistence** | Simple database deletion of draft invoices. |
| `updateBillDetails` | `public` | **Orchestration / Validation / Calculation / Persistence** | **Highly complex logic mixture:** Handles invoice modifications, audit trails, stock adjustments (deducting new or returning old stock differences), delta-based credit validation, and ledger recalculation. |
| `getBillSnapshotJson` | `private` | **Mapping / Serialization** | Converts the bill's state to a JSON string for historical audit tracking. |
| `confirmBill` | `public` | **Orchestration / Validation / Persistence** | Transitions DRAFT invoice to CONFIRMED, validates final credit terms, executes physical stock deduction, and updates customer balance. |
| `restoreBill` | `public` | **Orchestration / Validation / Persistence** | Re-activates a cancelled invoice, validates credit room, and attempts to re-deduct stock. |
| `bulkConfirmBills` | `public` | **Orchestration** | Batch processes a list of DRAFT invoices. |
| `getBillEditHistory` | `public` | **Persistence** | Fetches audit trail history logs for the given invoice. |
| `getSafeSecondaryPerPrimary` | `private` | **Validation / Calculation** | Null-safe helper to fetch unit conversion ratio. |

---

## 📊 2. `DashboardService.java` (Dashboard Module)
*Size: 1373 lines · 9 public methods*

| Method Name | Scope | Primary Responsibility Type | Mixed Concerns / Details |
|---|---|---|---|
| `getTodaySummary` | `public` | **Orchestration / Calculation / Persistence** | **Highly Mixed:** Fetches sales, deliveries, expenses, damage, and customer records. Computes EOD status, unpaid credits, customer limit alerts, and database backup health in one massive call. |
| `getMonthlyReport` | `public` | **Orchestration / Calculation** | Aggregates invoices, expenses, and returns for a specific month. Computes daily sales trend lines, category-wise revenue distributions, and net profit margins. |
| `getYearlyReport` | `public` | **Orchestration / Calculation** | Aggregates financial performance data across a 12-month calendar window. |
| `getSalesmenPerformance` | `public` | **Orchestration / Calculation** | Evaluates salesman KPIs: total orders generated, delivery success rates, payment collection amounts, and travel distances. |
| `getDashboardSummary` | `public` | **Orchestration / Calculation** | Core API for landing dashboard KPIs. Pulls metrics from multiple repository layers simultaneously. |
| `calculateCollectionBreakdown` | `private` | **Calculation** | Business logic: Groups payment modes (Cash, UPI, Cheque, Udhar) to draw percentage-based charts. |
| `calculateTotalInventoryValue` | `private` | **Calculation / Persistence** | Computes current stock value by checking batch buying rates multiplied by remaining quantities. |
| `getTrendData` | `public` | **Orchestration / Calculation** | Queries invoices for a dynamic rolling day window (e.g. 7, 30, 90 days) to construct daily moving averages. |
| `calculateAvgCollectionDays` | `private` | **Calculation** | Computes payment lifecycle duration (time elapsed from invoice creation to collection payment clearance). |

---

## 📦 3. `StockService.java` (Stock & Inventory Module)
*Size: 777 lines · 40 public methods*

| Method Name | Scope | Primary Responsibility Type | Mixed Concerns / Details |
|---|---|---|---|
| `getAllStock` / `getAllStockPaged` | `public` | **Orchestration / Persistence** | Retrieves active product stocks. |
| `getOrCreateStock` | `public` | **Persistence / Initialisation** | Thread-safe generation of inventory record block for a new product. |
| `getBatchesByProduct` / `getExpiringSoon` | `public` | **Orchestration / Persistence** | Batch filtering and lifecycle tracking queries. |
| `receiveStock` | `public` | **Orchestration / Persistence / Stock Inflow** | Adds inbound procurement quantities, creates new batches, calculates cost margins, and updates total virtual/physical inventory. |
| `deductOfferUnits` | `public` | **Calculation / Stock Outflow** | Subtracts free promotional items from batches during sales. |
| `addBackOfferStock` | `public` | **Calculation / Stock Inflow** | Restores free items back to inventory if an invoice is returned or cancelled. |
| `deductByPrimary` / `deductFromBatches` | `public` | **Calculation / Validation / Stock Outflow** | **FIFO Engine:** Computes primary vs secondary packaging conversion, validates stock room, and deducts quantities in FIFO order from oldest batches. |
| `addBackStock` / `addBackStockToBatch` | `public` | **Calculation / Stock Inflow** | Restores normal stock items. Handles movement logging internally. |
| `restoreStockToBatches` | `public` | **Calculation / Stock Inflow** | Parses returned invoice items and allocates them back to their exact parent batches. |
| `adjustStock` | `public` | **Validation / Persistence / Audit** | Adjusts stock physically due to human errors. Creates a persistent `StockAdjustmentLog` audit record. |
| `writeOffExpiredBatch` | `public` | **Validation / Persistence** | Flushes an entire batch's inventory to 0 due to expiry; creates write-off logs. |
| `markBatchDamage` | `public` | **Validation / Persistence** | Segregates normal inventory into damage logs. |

---

## 💳 4. `KhataService.java` (Khata / Receivables Module)
*Size: 787 lines · 9 public methods*

| Method Name | Scope | Primary Responsibility Type | Mixed Concerns / Details |
|---|---|---|---|
| `toResponse` / `toResponses` | `private` | **Mapping** | Entity DTO mapping for payment collections. |
| `applyPaymentToBill` | `private` | **Calculation / Domain Update** | Business Logic: Applies a cash/UPI receipt to an open credit invoice, decrementing outstanding balance and changing bill status. |
| `deriveBillStatus` | `private` | **Calculation** | Computes target status (`PARTIAL`, `PAID`, `COD_COLLECTED`) based on payment amounts. |
| `recalculateCustomerPending` | `private` | **Calculation / Persistence** | Recalculates outstanding balances for a customer by summing up unpaid invoices and updating the database. |
| `getCustomerPayments` / `getTodayCollections` | `public` | **Orchestration / Persistence** | Standard collection retrieval queries. |
| `previewOverpayment` | `public` | **Calculation / Preview** | Simulation Logic: Calculates how a new payment will distribute across a list of open bills and returns a preview without saving. |
| `recordPayment` | `public` | **Orchestration / Persistence** | **Massive God Method:** Inspects payment mode, splits into `recordNormalPayment`, `recordManualAdjust`, `recordAutoAdjust`, or `recordGeneralPayment`. Updates invoice status, records transactions, and updates customer balance. |
| `recordNormalPayment` / `recordManualAdjust` | `private` | **Persistence / Calculation** | Allocates payment directly to a single invoice. |
| `recordAutoAdjust` / `recordGeneralPayment` | `private` | **Calculation / Persistence** | **FIFO allocation algorithm:** Spans a payment amount across multiple oldest invoices recursively. |
| `deletePayment` | `public` | **Orchestration / Stock Rest Restore** | Removes a payment receipt and **rolls back** (adds back) outstanding balance to all affected bills. |
| `updatePayment` | `public` | **Persistence** | Updates metadata notes on a payment receipt. |
