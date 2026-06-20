-- Delete June 20 batch and sync stock
BEGIN;

-- 1. Delete adjustment logs for the batch MYBATCH01
DELETE FROM stock_adjustment_logs 
WHERE batch_id = 'f1c27328-0d27-4533-bfa6-dd5bd480e2af';

-- 2. Delete the batch itself from stock_batches
DELETE FROM stock_batches 
WHERE id = 'f1c27328-0d27-4533-bfa6-dd5bd480e2af';

-- 3. Recalculate and update the stock table
WITH batch_sums AS (
    SELECT 
        product_id, 
        COALESCE(SUM(secondary_remaining), 0) AS calculated_secondary
    FROM stock_batches
    GROUP BY product_id
)
UPDATE stock s
SET 
    total_secondary_units = COALESCE(b.calculated_secondary, 0),
    total_primary_units = COALESCE(b.calculated_secondary, 0) / p.secondary_per_primary,
    open_primary_remaining = COALESCE(b.calculated_secondary, 0) % p.secondary_per_primary,
    has_open_primary = (COALESCE(b.calculated_secondary, 0) % p.secondary_per_primary) > 0,
    last_updated = NOW()
FROM products p
LEFT JOIN batch_sums b ON p.id = b.product_id
WHERE s.product_id = p.id;

COMMIT;
