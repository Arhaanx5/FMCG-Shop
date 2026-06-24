-- Today's bills
SELECT 
    id, 
    bill_number, 
    payment_mode, 
    status, 
    grand_total, 
    paid_amount, 
    pending_amount, 
    created_at 
FROM bills 
WHERE created_at >= '2026-06-23 00:00:00' 
  AND created_at < '2026-06-24 00:00:00' 
ORDER BY created_at ASC;

-- Today's payments (Udhar Recovery)
SELECT 
    id, 
    amount, 
    payment_mode, 
    payment_source,
    paid_at, 
    bill_id, 
    customer_id 
FROM payments 
WHERE paid_at >= '2026-06-23 00:00:00' 
  AND paid_at < '2026-06-24 00:00:00' 
ORDER BY paid_at ASC;

-- Payments associated with today's bills (to get applied_amount)
SELECT 
    p.id, 
    p.amount, 
    p.applied_amount, 
    p.bill_id, 
    b.bill_number,
    p.payment_mode, 
    p.paid_at 
FROM payments p
JOIN bills b ON p.bill_id = b.id
WHERE b.created_at >= '2026-06-23 00:00:00' 
  AND b.created_at < '2026-06-24 00:00:00';
