-- Reset stock remaining to received quantities
BEGIN;

-- 1. Backup tables before running the updates
CREATE TABLE IF NOT EXISTS stock_batches_backup_june20 AS SELECT * FROM stock_batches;
CREATE TABLE IF NOT EXISTS stock_backup_june20 AS SELECT * FROM stock;

-- 2. Update stock batches
UPDATE stock_batches 
SET secondary_remaining = secondary_received, 
    offer_secondary_remaining = COALESCE(offer_secondary_received, 0), 
    is_exhausted = false;

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
