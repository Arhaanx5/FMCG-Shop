SELECT MIN(created_at) AS min_bill_date, MAX(created_at) AS max_bill_date, COUNT(*) AS bill_count FROM bills;
SELECT MIN(paid_at) AS min_payment_date, MAX(paid_at) AS max_payment_date, COUNT(*) AS payment_count FROM payments;
