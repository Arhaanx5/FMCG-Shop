-- ============================================================
-- FULL SYSTEM AUDIT — fmcg_shop_prod
-- Date: 2026-06-23
-- ============================================================

\echo '============================================================'
\echo 'AUDIT 1A: Batch stock vs global stock mismatch'
\echo '============================================================'
SELECT
    p.name,
    s.total_secondary_units as global_stock,
    COALESCE(SUM(sb.secondary_received), 0) as batch_sum_received
FROM stock s
JOIN products p ON p.id = s.product_id
LEFT JOIN stock_batches sb ON sb.product_id = s.product_id
GROUP BY p.name, s.total_secondary_units
ORDER BY p.name;

\echo ''
\echo '============================================================'
\echo 'AUDIT 1B: Negative stock check'
\echo '============================================================'
SELECT p.name, s.total_secondary_units, s.total_primary_units
FROM stock s
JOIN products p ON p.id = s.product_id
WHERE s.total_secondary_units < 0 OR s.total_primary_units < 0;

\echo ''
\echo '============================================================'
\echo 'AUDIT 1C: Orphan batches (batch exists but no stock entry)'
\echo '============================================================'
SELECT sb.batch_number, p.name, sb.secondary_received
FROM stock_batches sb
JOIN products p ON p.id = sb.product_id
LEFT JOIN stock s ON s.product_id = sb.product_id
WHERE s.id IS NULL;

\echo ''
\echo '============================================================'
\echo 'AUDIT 2A: Products with stock but NO movements at all'
\echo '============================================================'
SELECT
    p.name,
    s.total_secondary_units,
    COUNT(sm.id) as total_movements
FROM stock s
JOIN products p ON p.id = s.product_id
LEFT JOIN stock_movements sm ON sm.product_id = p.id
WHERE s.total_secondary_units > 0
GROUP BY p.name, s.total_secondary_units
HAVING COUNT(sm.id) = 0;

\echo ''
\echo '============================================================'
\echo 'AUDIT 2B: Movement type distribution'
\echo '============================================================'
SELECT
    movement_type,
    COUNT(*) as count,
    SUM(quantity) as total_qty
FROM stock_movements
GROUP BY movement_type
ORDER BY count DESC;

\echo ''
\echo '============================================================'
\echo 'AUDIT 2C: Batches with stock but no PURCHASE movement'
\echo '============================================================'
SELECT
    p.name,
    sb.batch_number,
    sb.secondary_received,
    sb.received_at
FROM stock_batches sb
JOIN products p ON p.id = sb.product_id
WHERE sb.secondary_received > 0
AND NOT EXISTS (
    SELECT 1 FROM stock_movements sm
    WHERE sm.batch_id = sb.id
    AND sm.movement_type = 'PURCHASE'
)
ORDER BY sb.received_at;

\echo ''
\echo '============================================================'
\echo 'AUDIT 3A: Inventory value per batch'
\echo '============================================================'
SELECT
    p.name,
    sb.batch_number,
    sb.secondary_received as qty,
    sb.buy_price_without_tax as buy_price_per_box,
    p.secondary_per_primary,
    ROUND((sb.buy_price_without_tax / p.secondary_per_primary)::numeric, 2) as cost_per_unit,
    ROUND((sb.secondary_received * sb.buy_price_without_tax / p.secondary_per_primary)::numeric, 2) as batch_value
FROM stock_batches sb
JOIN products p ON p.id = sb.product_id
WHERE sb.secondary_received > 0
ORDER BY batch_value DESC;

\echo ''
\echo '============================================================'
\echo 'AUDIT 3B: Total inventory value'
\echo '============================================================'
SELECT
    ROUND(SUM(sb.secondary_received * sb.buy_price_without_tax / p.secondary_per_primary)::numeric, 2) as total_inventory_value
FROM stock_batches sb
JOIN products p ON p.id = sb.product_id
WHERE sb.secondary_received > 0;

\echo ''
\echo '============================================================'
\echo 'AUDIT 4A: Bills with no items'
\echo '============================================================'
SELECT
    b.id,
    b.bill_number,
    b.created_at,
    b.grand_total,
    COUNT(bi.id) as item_count
FROM bills b
LEFT JOIN bill_items bi ON bi.bill_id = b.id
GROUP BY b.id, b.bill_number, b.created_at, b.grand_total
HAVING COUNT(bi.id) = 0
LIMIT 10;

\echo ''
\echo '============================================================'
\echo 'AUDIT 4B: Bill total vs items sum mismatch (>1 rupee diff)'
\echo '============================================================'
SELECT
    b.bill_number,
    b.grand_total as header_total,
    ROUND(SUM(bi.total)::numeric, 2) as items_sum,
    b.discount,
    ROUND((b.grand_total - (SUM(bi.total) - b.discount))::numeric, 2) as mismatch
FROM bills b
JOIN bill_items bi ON bi.bill_id = b.id
GROUP BY b.id, b.bill_number, b.grand_total, b.discount
HAVING ABS(b.grand_total - (SUM(bi.total) - b.discount)) > 1
ORDER BY mismatch DESC
LIMIT 10;

\echo ''
\echo '============================================================'
\echo 'AUDIT 5A: Customers with no area assigned'
\echo '============================================================'
SELECT id, name, phone
FROM customers
WHERE area_id IS NULL
ORDER BY name;

\echo ''
\echo '============================================================'
\echo 'AUDIT 5B: Duplicate customer phone numbers'
\echo '============================================================'
SELECT
    phone,
    COUNT(*) as count,
    STRING_AGG(name, ', ') as customer_names
FROM customers
WHERE phone IS NOT NULL
GROUP BY phone
HAVING COUNT(*) > 1
ORDER BY count DESC;

\echo ''
\echo '============================================================'
\echo 'AUDIT 6A: Monthly expense summary'
\echo '============================================================'
SELECT
    TO_CHAR(DATE_TRUNC('month', created_at), 'YYYY-MM') as month,
    category,
    COUNT(*) as count,
    ROUND(SUM(amount)::numeric, 2) as total
FROM expenses
GROUP BY DATE_TRUNC('month', created_at), category
ORDER BY month DESC, total DESC;

\echo ''
\echo '============================================================'
\echo 'AUDIT 7A: Products with missing or zero selling price'
\echo '============================================================'
SELECT id, name, category, sell_price_primary, sell_price_secondary
FROM products
WHERE sell_price_primary IS NULL
   OR sell_price_primary = 0
   OR sell_price_secondary IS NULL
   OR sell_price_secondary = 0
ORDER BY name;

\echo ''
\echo '============================================================'
\echo 'AUDIT 7B: Products with no category'
\echo '============================================================'
SELECT id, name, category
FROM products
WHERE category IS NULL OR category = ''
ORDER BY name;

\echo ''
\echo '============================================================'
\echo 'AUDIT 7C: Batches with expiry in past (expired stock)'
\echo '============================================================'
SELECT
    p.name,
    sb.batch_number,
    sb.expiry_date,
    sb.secondary_received as qty,
    CURRENT_DATE - sb.expiry_date as days_expired
FROM stock_batches sb
JOIN products p ON p.id = sb.product_id
WHERE sb.expiry_date < CURRENT_DATE
AND sb.secondary_received > 0
ORDER BY days_expired DESC;

\echo ''
\echo '============================================================'
\echo 'AUDIT 7D: Batches with NULL expiry date (active stock)'
\echo '============================================================'
SELECT
    p.name,
    sb.batch_number,
    sb.secondary_received as qty,
    sb.received_at
FROM stock_batches sb
JOIN products p ON p.id = sb.product_id
WHERE sb.expiry_date IS NULL
AND sb.secondary_received > 0
ORDER BY sb.received_at;

\echo ''
\echo '============================================================'
\echo 'AUDIT COMPLETE'
\echo '============================================================'
