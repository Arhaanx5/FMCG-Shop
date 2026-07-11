# 🏭 Purchase / Procurement Module — Implementation Plan

## Background

Abhi system me `StockBatch` pe `supplierName`, `supplierInvoiceDate`, `purchasePrice` fields hain — matlab **stock receive karna possible hai**, lekin koi formal **Supplier master, PO, GRN, ya payable ledger** nahi hai. Ye plan un sab gaps ko fill karega.

> **Next migration**: V25 se start karenge (V24 already hai)

---

## 🔴 Critical Design Decisions

> [!IMPORTANT]
> **GRN aur Stock Receive ka relation**: Abhi `StockReceiveService` directly `StockBatch` banata hai. Plan me GRN confirm hone ke **baad** stock receive hoga — matlab GRN approved → tab batch create hoga. Ye change **backward compatible** hai kyunki purana flow chalte rahe sakta hai.

> [!WARNING]
> **Supplier GSTIN — Input Tax Credit**: Agar supplier ka GSTIN galat hua toh GSTR-2B mismatch hogi aur GST input credit nahi milega. Plan me GSTIN format validation mandatory rahega.

---

## 📋 Proposed Changes

---

### Phase 1 — Database Migrations

#### [NEW] V25 — `suppliers` Table
```sql
CREATE TABLE suppliers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL UNIQUE,
    contact_person  VARCHAR(255),
    phone           VARCHAR(20),
    email           VARCHAR(255),
    address         TEXT,
    gstin           VARCHAR(15),          -- GST input credit ke liye
    state_code      VARCHAR(5),
    credit_days     INTEGER DEFAULT 30,   -- kitne din me payment karni hai
    active          BOOLEAN DEFAULT TRUE,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);
```

#### [NEW] V26 — `purchase_orders` Table
```sql
CREATE TABLE purchase_orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_number       VARCHAR(50) UNIQUE NOT NULL,  -- AUTO: PO-2026-0001
    supplier_id     UUID NOT NULL REFERENCES suppliers(id),
    status          VARCHAR(20) DEFAULT 'DRAFT',
                    -- DRAFT → SENT → PARTIALLY_RECEIVED → RECEIVED → CANCELLED
    expected_date   DATE,
    notes           TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE purchase_order_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_id           UUID NOT NULL REFERENCES purchase_orders(id),
    product_id      UUID NOT NULL REFERENCES products(id),
    ordered_qty     INTEGER NOT NULL,     -- secondary unit me
    unit_type       VARCHAR(10),          -- BOX ya LADI
    expected_price  DECIMAL(10,2),        -- estimated price at time of PO
    received_qty    INTEGER DEFAULT 0     -- GRN ke baad update hoga
);
```

#### [NEW] V27 — `grn_receipts` Table (Goods Received Note)
```sql
CREATE TABLE grn_receipts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grn_number       VARCHAR(50) UNIQUE NOT NULL,   -- GRN-2026-0001
    po_id            UUID REFERENCES purchase_orders(id),  -- optional
    supplier_id      UUID NOT NULL REFERENCES suppliers(id),
    invoice_number   VARCHAR(100),
    invoice_date     DATE,
    total_amount     DECIMAL(12,2),
    status           VARCHAR(20) DEFAULT 'PENDING',
                     -- PENDING → APPROVED → REJECTED
    approved_by      UUID REFERENCES users(id),
    approved_at      TIMESTAMP,
    received_by      UUID REFERENCES users(id),
    received_at      TIMESTAMP DEFAULT NOW(),
    notes            TEXT
);

CREATE TABLE grn_items (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grn_id           UUID NOT NULL REFERENCES grn_receipts(id),
    product_id       UUID NOT NULL REFERENCES products(id),
    po_item_id       UUID REFERENCES purchase_order_items(id),
    ordered_qty      INTEGER,             -- PO se copied (info only)
    received_qty     INTEGER NOT NULL,    -- actual maal jo aaya
    purchase_price   DECIMAL(10,2),       -- is GRN me price
    batch_number     VARCHAR(100),
    expiry_date      DATE,
    batch_id         UUID REFERENCES stock_batches(id)  -- GRN approve hone par set
);
```

#### [NEW] V28 — `supplier_payments` Table (Payable Ledger)
```sql
CREATE TABLE supplier_payments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id      UUID NOT NULL REFERENCES suppliers(id),
    grn_id           UUID REFERENCES grn_receipts(id),   -- kis GRN ke liye
    payment_type     VARCHAR(20) NOT NULL,
                     -- PAYABLE (debit - hamne maal liya)
                     -- PAYMENT (credit - humne diya)
                     -- RETURN_CREDIT (credit - maal wapas kiya)
    amount           DECIMAL(12,2) NOT NULL,
    payment_mode     VARCHAR(30),          -- CASH, CHEQUE, UPI, NEFT
    reference_no     VARCHAR(100),
    payment_date     DATE,
    notes            TEXT,
    created_by       UUID REFERENCES users(id),
    created_at       TIMESTAMP DEFAULT NOW()
);
```

#### [NEW] V29 — `purchase_returns` Table
```sql
CREATE TABLE purchase_returns (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_number    VARCHAR(50) UNIQUE NOT NULL,  -- PR-2026-0001
    supplier_id      UUID NOT NULL REFERENCES suppliers(id),
    grn_id           UUID REFERENCES grn_receipts(id),
    reason           TEXT NOT NULL,
    status           VARCHAR(20) DEFAULT 'PENDING',
                     -- PENDING → APPROVED → DISPATCHED → CREDITED
    return_date      DATE,
    created_by       UUID REFERENCES users(id),
    created_at       TIMESTAMP DEFAULT NOW()
);

CREATE TABLE purchase_return_items (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id        UUID NOT NULL REFERENCES purchase_returns(id),
    product_id       UUID NOT NULL REFERENCES products(id),
    batch_id         UUID REFERENCES stock_batches(id),
    quantity         INTEGER NOT NULL,
    return_price     DECIMAL(10,2),
    reason           VARCHAR(255)  -- expire, damage, quality, excess
);
```

---

### Phase 2 — Backend Java Modules

#### [NEW] `modules/supplier/` Package
```
Supplier.java            — Entity (V25 table)
SupplierRepository.java  — JPA: findByActive, findByName, findByGstin
SupplierService.java     — CRUD + GSTIN format validation
SupplierController.java  — ADMIN/MANAGER restricted
dto/
  CreateSupplierRequest.java
  SupplierResponse.java
```

**Key logic**:
- GSTIN format regex validate karo: `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$`
- `getOutstandingBalance()` = supplier ke saare `PAYABLE` minus `PAYMENT` minus `RETURN_CREDIT` amounts

#### [NEW] `modules/procurement/` Package
```
PurchaseOrder.java + PurchaseOrderItem.java   — Entities
GrnReceipt.java + GrnItem.java                — Entities
PurchaseReturn.java + PurchaseReturnItem.java — Entities
SupplierPayment.java                          — Entity

PurchaseOrderService.java   — PO CRUD, auto PO number
GrnService.java             — GRN create, approve (triggers stock receive)
PurchaseReturnService.java  — Return create, approve (triggers stock reduce)
SupplierLedgerService.java  — Outstanding balance, payment recording
SequenceService.java        — PO/GRN/PR auto-number generation

ProcurementController.java  — All endpoints, ADMIN/MANAGER
```

**Key business logic**:

```
GrnService.approveGrn(grnId):
  1. GRN status → APPROVED
  2. Har GrnItem ke liye:
     → StockReceiveService.receiveStock() call karo
     → Created batch_id ko grn_item me save karo
  3. Supplier ledger me PAYABLE entry daalo (total_amount)
  4. PO ka status update karo (PARTIALLY_RECEIVED ya RECEIVED)

PurchaseReturnService.approveReturn(returnId):
  1. Har item ke liye:
     → Stock batch se qty reduce karo
     → StockMovement log karo (type: PURCHASE_RETURN)
  2. Supplier ledger me RETURN_CREDIT entry daalo
  3. Return status → APPROVED

SupplierLedgerService.recordPayment():
  1. supplier_payments me PAYMENT entry
  2. Outstanding = SUM(PAYABLE) - SUM(PAYMENT) - SUM(RETURN_CREDIT)
```

**Endpoints**:
```
POST   /api/procurement/suppliers
GET    /api/procurement/suppliers
GET    /api/procurement/suppliers/{id}
PUT    /api/procurement/suppliers/{id}

POST   /api/procurement/purchase-orders
GET    /api/procurement/purchase-orders
PUT    /api/procurement/purchase-orders/{id}/send     (DRAFT→SENT)
DELETE /api/procurement/purchase-orders/{id}          (DRAFT only)

POST   /api/procurement/grn
GET    /api/procurement/grn
PUT    /api/procurement/grn/{id}/approve              (ADMIN/MANAGER)
GET    /api/procurement/grn/{id}/mismatch             (GRN vs PO diff)

GET    /api/procurement/suppliers/{id}/ledger         (outstanding + history)
POST   /api/procurement/suppliers/{id}/payment        (payment record)

POST   /api/procurement/purchase-returns
PUT    /api/procurement/purchase-returns/{id}/approve
```

---

### Phase 3 — Frontend Pages

#### [NEW] `Procurement.jsx` — Main Page (Tab layout)

**Tab 1 — Suppliers**
- Table: Name, Phone, GSTIN, Credit Days, Outstanding Balance (live calculated)
- Add/Edit Supplier form
- Per row: View Ledger button

**Tab 2 — Purchase Orders**
- Table: PO Number, Supplier, Date, Items Count, Status badge
- Create PO form:
  - Supplier dropdown
  - Product rows (product, qty, expected price)
  - Expected delivery date
- Status flow buttons: `Send to Supplier` → `Create GRN from this PO`

**Tab 3 — GRN (Goods Received)**
- Table: GRN Number, Supplier, Invoice No., Date, Total Amount, Status
- Create GRN (with or without PO):
  - Supplier select
  - Items: product, ordered qty (from PO if linked), **actual received qty** (editable)
  - Batch number, expiry date per item
  - Invoice date, invoice number, total amount
- Mismatch alert: agar ordered vs received different → red highlight
- `Approve GRN` button (ADMIN/MANAGER) → stock automatically update hoti hai

**Tab 4 — Supplier Ledger**
- Supplier dropdown → ledger load hoti hai
- Summary cards: Total Purchased | Total Paid | **Outstanding** | Next Due Date
- Transaction table: Date | Type | Amount | Reference | Balance Running
- `Record Payment` button → modal: amount, mode (CASH/CHEQUE/UPI/NEFT), reference no., date

**Tab 5 — Purchase Returns**
- Table: Return No., Supplier, GRN Ref, Status, Date
- Create Return form: supplier, GRN select, items (product, qty, reason)
- Approve button → stock reduce + credit note in ledger

---

## ✅ Verification Plan

### Automated Tests
- `GrnApprovalCreatesStockBatchTest` — GRN approve hone par `StockBatch` create hota hai
- `SupplierOutstandingBalanceCalculationTest` — Payable - Payment - Return = correct outstanding
- `PurchaseReturnReducesStockTest` — Return approve hone par stock kam hoti hai
- `GrnMismatchDetectionTest` — ordered 100, received 80 → mismatch flag

### Manual Verification
1. Supplier banao → GSTIN validate ho
2. PO banao → GRN se link karo → approve karo → stock.jsx me batch dikh jaye
3. Supplier ledger me payable entry auto-aaye
4. Payment record karo → outstanding reduce ho
5. Purchase return karo → stock aur ledger dono update ho

---

## 📅 Estimated Scope

| Phase | Work | Estimated Time |
|---|---|---|
| Phase 1 — DB Migrations (V25–V29) | 5 SQL files | Small |
| Phase 2 — Backend (5 entities, 4 services, 1 controller) | ~1000 lines Java | Medium |
| Phase 3 — Frontend (Procurement.jsx, 5 tabs) | ~600 lines JSX | Medium |
| Tests | 4 JUnit tests | Small |
| **Total** | | **1 focused session** |

---

## 🟡 Open Questions

1. **GRN bina PO ke allow karein?** (Direct receive, bina order ke) — Plan me dono support hai, confirm karo
2. **Credit days alert** — Supplier ko payment 30 din me karni hai, aur 28 din ho gaye — dashboard alert chahiye? (Optional feature)
3. **GST on Purchase (Input Tax Credit)** — Abhi plan me GST amount track nahi kiya purchase pe. Agar GSTR-2B filing bhi karni hai toh alag column chahiye hoga. Is session me include karein ya baad me?
