SELECT 
    p.amount, 
    p.payment_mode, 
    b.bill_number, 
    c.name AS customer_name, 
    c.shop_name 
FROM payments p 
JOIN customers c ON p.customer_id = c.id 
LEFT JOIN bills b ON p.bill_id = b.id 
WHERE p.paid_at >= '2026-06-23 00:00:00' 
  AND p.paid_at < '2026-06-24 00:00:00' 
ORDER BY p.paid_at ASC;

-- Let's also fetch cash bills details (today's cash bills that were immediate cash collections)
SELECT 
    b.bill_number, 
    b.grand_total, 
    b.paid_amount, 
    c.name AS customer_name, 
    c.shop_name 
FROM bills b
LEFT JOIN customers c ON b.customer_id = c.id
WHERE b.created_at >= '2026-06-23 00:00:00' 
  AND b.created_at < '2026-06-24 00:00:00'
  AND b.payment_mode = 'CASH'
ORDER BY b.created_at ASC;
