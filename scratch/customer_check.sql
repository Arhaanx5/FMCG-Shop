SELECT id, name, shop_name, total_pending, active FROM customers WHERE name LIKE '%Shivnath%' OR shop_name LIKE '%Shitala%';

-- Let's query all bills for this customer
SELECT id, bill_number, grand_total, paid_amount, pending_amount, payment_mode, status, created_at FROM bills WHERE customer_id IN (
    SELECT id FROM customers WHERE name LIKE '%Shivnath%' OR shop_name LIKE '%Shitala%'
);

-- Let's query all payments for this customer
SELECT id, amount, applied_amount, payment_mode, paid_at, bill_id FROM payments WHERE customer_id IN (
    SELECT id FROM customers WHERE name LIKE '%Shivnath%' OR shop_name LIKE '%Shitala%'
);
