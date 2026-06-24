-- Remaining audit queries with correct column names

\echo '=== AUDIT 4B: Bill total vs items sum mismatch ==='
SELECT b.bill_number, b.grand_total as header_total,
    ROUND(SUM(bi.total)::numeric, 2) as items_sum,
    ROUND((b.grand_total - SUM(bi.total))::numeric, 2) as mismatch
FROM bills b JOIN bill_items bi ON bi.bill_id = b.id
GROUP BY b.id, b.bill_number, b.grand_total
HAVING ABS(b.grand_total - SUM(bi.total)) > 1
ORDER BY mismatch DESC LIMIT 10;

\echo '=== AUDIT 5A: Customers with no area assigned ==='
SELECT id, name, phone FROM customers WHERE area_id IS NULL ORDER BY name;

\echo '=== AUDIT 5B: Duplicate phone numbers ==='
SELECT phone, COUNT(*) as count, STRING_AGG(name, ', ') as names
FROM customers WHERE phone IS NOT NULL
GROUP BY phone HAVING COUNT(*) > 1 ORDER BY count DESC;

\echo '=== AUDIT 6A: Monthly expense summary ==='
SELECT TO_CHAR(DATE_TRUNC('month', expense_date), 'YYYY-MM') as month,
    category, COUNT(*) as count,
    ROUND(SUM(amount)::numeric, 2) as total
FROM expenses
GROUP BY DATE_TRUNC('month', expense_date), category
ORDER BY month DESC, total DESC LIMIT 30;

\echo '=== Bills count and status summary ==='
SELECT status, COUNT(*) as count, ROUND(SUM(grand_total)::numeric,2) as total
FROM bills GROUP BY status ORDER BY count DESC;

\echo '=== Total bills billed ==='
SELECT COUNT(*) as total_bills,
    ROUND(SUM(grand_total)::numeric,2) as total_billed
FROM bills WHERE status != 'CANCELLED';

\echo '=== 4 new batches invoice details ==='
SELECT sb.batch_number, p.name, sb.invoice_number, sb.secondary_received, sb.received_at
FROM stock_batches sb JOIN products p ON p.id = sb.product_id
WHERE sb.received_at > '2026-06-23 00:00:00'
ORDER BY sb.received_at;

\echo '=== Customer outstanding total ==='
SELECT COUNT(*) as total_customers,
    ROUND(SUM(total_pending)::numeric,2) as total_outstanding
FROM customers WHERE is_active = true;
