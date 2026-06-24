# Reverting a Sales Return (Undo Return) Guide

If a user mistakenly returns items from a bill and needs to revert/restore it back to its original status, follow this guide to adjust the database records safely.

---

## Step 1: Identify the Return Details
Run this query to find the returned product, batch, returned quantity, and movement IDs:
```sql
SELECT 
    sm.id AS movement_id,
    sm.product_id,
    sm.batch_id,
    sm.quantity AS returned_qty_secondary, -- This is positive for returns
    sm.unit_price,
    p.name AS product_name,
    p.secondary_per_primary
FROM stock_movements sm
JOIN products p ON p.id = sm.product_id
WHERE sm.reference_number = 'BILL-XXXXX' -- Replace with your bill number
  AND sm.movement_type = 'RETURN_IN';
```

---

## Step 2: Verify Bill & Item State
Check if the item still exists in `bill_items` with a quantity of `0`, or if it was deleted (if quantity was 0 and `orphanRemoval` was active).
```sql
-- 1. Check Bill Details
SELECT id, subtotal, gst_total, grand_total, paid_amount, pending_amount, status 
FROM bills 
WHERE bill_number = 'BILL-XXXXX';

-- 2. Check Item Details
SELECT id, product_id, quantity, rate, total, gst_amount 
FROM bill_items 
WHERE bill_id = (SELECT id FROM bills WHERE bill_number = 'BILL-XXXXX');
```

*Note: If the item is missing from the list, you will need to `INSERT` a new row into `bill_items`. If it is present with `quantity = 0`, you will `UPDATE` it.*

---

## Step 3: Run the Restoration Script
Fill in the parameters in the template below and execute it inside a transaction.

### CASE A: The item still exists in `bill_items` (with `quantity = 0` or partial quantity)
```sql
BEGIN;

-- 1. Restore the bill item quantity and values
-- [UPDATE VALUES]
-- New quantity = current quantity + returned quantity (in primary unit, e.g. BOX)
-- New total = current total + (returned quantity * rate)
-- New gst_amount = current gst_amount + (returned quantity * rate * gst_percent / 100)
UPDATE bill_items 
SET quantity = <NEW_QTY>, 
    total = <NEW_TOTAL>, 
    gst_amount = <NEW_GST_AMOUNT>
WHERE id = '<BILL_ITEM_UUID>';

-- 2. Update Bill totals and paid/pending balances
-- [UPDATE VALUES]
-- New subtotal = current subtotal + returned subtotal
-- New gst_total = current gst_total + returned gst_total
-- New grand_total = current grand_total + returned total
-- New paid_amount = current paid_amount + returned total (if it was paid cash)
UPDATE bills 
SET subtotal = <NEW_SUBTOTAL>, 
    gst_total = <NEW_GST_TOTAL>, 
    grand_total = <NEW_GRAND_TOTAL>, 
    paid_amount = <NEW_PAID_AMOUNT>, 
    pending_amount = <NEW_PENDING_AMOUNT>,
    status = 'PAID' -- or 'CONFIRMED' / 'PARTIAL' depending on payment state
WHERE id = '<BILL_UUID>';

-- 3. Deduct returned quantity from the specific stock batch
-- [UPDATE VALUES]
-- New secondary_remaining = current secondary_remaining - returned_qty_secondary
UPDATE stock_batches 
SET secondary_remaining = secondary_remaining - <RETURNED_QTY_SECONDARY> 
WHERE id = '<BATCH_UUID>';

-- 4. Deduct returned quantity from main stock record and normalize
-- [UPDATE VALUES]
-- New total_secondary_units = current total_secondary_units - returned_qty_secondary
-- New total_primary_units = (current total_secondary_units - returned_qty_secondary) / secondary_per_primary
-- New open_primary_remaining = (current total_secondary_units - returned_qty_secondary) % secondary_per_primary
UPDATE stock 
SET total_secondary_units = total_secondary_units - <RETURNED_QTY_SECONDARY>, 
    total_primary_units = (total_secondary_units - <RETURNED_QTY_SECONDARY>) / <SECONDARY_PER_PRIMARY>, 
    open_primary_remaining = (total_secondary_units - <RETURNED_QTY_SECONDARY>) % <SECONDARY_PER_PRIMARY>, 
    has_open_primary = ((total_secondary_units - <RETURNED_QTY_SECONDARY>) % <SECONDARY_PER_PRIMARY>) > 0
WHERE product_id = '<PRODUCT_UUID>';

-- 5. Delete the false RETURN_IN stock movement logs to keep audit clean
DELETE FROM stock_movements 
WHERE reference_number = 'BILL-XXXXX' 
  AND movement_type = 'RETURN_IN';

COMMIT;
```

### CASE B: The item was completely deleted from `bill_items`
If the item was completely removed, insert it back:
```sql
BEGIN;

-- 1. Insert the item back into bill_items
INSERT INTO bill_items (id, bill_id, product_id, batch_id, unit_type, quantity, free_quantity, rate, original_rate, gst_percent, gst_amount, cess_percent, cess_amount, total, is_offer)
VALUES (
    gen_random_uuid(), 
    '<BILL_UUID>', 
    '<PRODUCT_UUID>', 
    '<BATCH_UUID>', 
    '<UNIT_TYPE>', -- 'PRIMARY' or 'SECONDARY'
    <RETURNED_QTY_PRIMARY>, 
    0, 
    <RATE>, 
    <ORIGINAL_RATE>, 
    <GST_PERCENT>, 
    <GST_AMOUNT>, 
    0, 
    0, 
    <TOTAL_AMOUNT>, 
    false
);

-- 2. Update Bill totals and paid/pending balances
UPDATE bills 
SET subtotal = subtotal + <RETURNED_SUBTOTAL>, 
    gst_total = gst_total + <RETURNED_GST>, 
    grand_total = grand_total + <RETURNED_TOTAL>, 
    paid_amount = paid_amount + <RETURNED_TOTAL>, 
    pending_amount = pending_amount
WHERE id = '<BILL_UUID>';

-- 3. Deduct returned quantity from specific stock batch
UPDATE stock_batches 
SET secondary_remaining = secondary_remaining - <RETURNED_QTY_SECONDARY> 
WHERE id = '<BATCH_UUID>';

-- 4. Deduct returned quantity from main stock record
UPDATE stock 
SET total_secondary_units = total_secondary_units - <RETURNED_QTY_SECONDARY>, 
    total_primary_units = (total_secondary_units - <RETURNED_QTY_SECONDARY>) / <SECONDARY_PER_PRIMARY>, 
    open_primary_remaining = (total_secondary_units - <RETURNED_QTY_SECONDARY>) % <SECONDARY_PER_PRIMARY>, 
    has_open_primary = ((total_secondary_units - <RETURNED_QTY_SECONDARY>) % <SECONDARY_PER_PRIMARY>) > 0
WHERE product_id = '<PRODUCT_UUID>';

-- 5. Delete the RETURN_IN logs
DELETE FROM stock_movements 
WHERE reference_number = 'BILL-XXXXX' 
  AND movement_type = 'RETURN_IN';

COMMIT;
```
