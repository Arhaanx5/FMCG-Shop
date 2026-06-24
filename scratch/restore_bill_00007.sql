BEGIN;

-- 1. Restore the returned item (Takatak Masala-20Gm/Rs-5) on BILL-00007
UPDATE bill_items 
SET quantity = 2, 
    total = 1828.58, 
    gst_amount = 91.43 
WHERE id = 'cd385d08-1d8d-40f4-a696-9deecf69e12d';

-- 2. Restore the bill totals and paid/pending balances on BILL-00007
UPDATE bills 
SET subtotal = 4209.54, 
    gst_total = 210.48, 
    grand_total = 4420.00, 
    paid_amount = 4420.00, 
    pending_amount = 0.00,
    status = 'PAID'
WHERE id = '3260127c-8bcb-4f06-b237-e4d7de877b30';

-- 3. Deduct the returned quantity from the stock batch (MAFF18)
-- Current remaining is 475. Reverting the return of 40 LADI means subtracting 40 LADI.
UPDATE stock_batches 
SET secondary_remaining = 435 
WHERE id = '0eb11340-e1d0-4d41-8ad0-6c2803226ded';

-- 4. Re-deduct the returned quantity from the main stock record
-- Current total_secondary_units is 475.
-- 435 LADI / 20 (secondary_per_primary) = 21 primary units (BOX) and 15 open primary remaining (LADI).
UPDATE stock 
SET total_secondary_units = 435, 
    total_primary_units = 21, 
    open_primary_remaining = 15, 
    has_open_primary = true 
WHERE id = '741abb5e-9619-465a-ad70-7d18844b9f13';

-- 5. Delete the false RETURN_IN stock movement logs
DELETE FROM stock_movements 
WHERE id IN ('565bd10c-6055-486b-9e04-6a9ba0406b42', '64f1c2d8-74fa-47f5-8a3f-475870dfd901');

COMMIT;
