-- 1. Period Payments (Udhar Recovery) in June 2026
WITH JunePayments AS (
    SELECT 
        COALESCE(SUM(CASE WHEN payment_mode = 'CASH' THEN amount ELSE 0 END), 0) AS khata_cash,
        COALESCE(SUM(CASE WHEN payment_mode = 'UPI' THEN amount ELSE 0 END), 0) AS khata_upi,
        COALESCE(SUM(amount), 0) AS total_khata
    FROM payments 
    WHERE paid_at >= '2026-06-01 00:00:00' 
      AND paid_at < '2026-07-01 00:00:00'
),

-- 2. Bills generated in June 2026
JuneBills AS (
    SELECT id, paid_amount, payment_mode
    FROM bills
    WHERE created_at >= '2026-06-01 00:00:00'
      AND created_at < '2026-07-01 00:00:00'
      AND status IN ('CONFIRMED', 'PARTIAL', 'PAID')
),

-- 3. Payments applied to June bills
AppliedPayments AS (
    SELECT 
        bill_id,
        COALESCE(SUM(applied_amount), 0) AS total_applied
    FROM payments
    WHERE bill_id IN (SELECT id FROM JuneBills)
    GROUP BY bill_id
),

-- 4. Calculate Net Immediate for June bills
NetImmediateCalculated AS (
    SELECT 
        b.id,
        b.payment_mode,
        b.paid_amount,
        COALESCE(ap.total_applied, 0) AS total_applied,
        (b.paid_amount - COALESCE(ap.total_applied, 0)) AS net_immediate
    FROM JuneBills b
    LEFT JOIN AppliedPayments ap ON b.id = ap.bill_id
),

ImmediateTotals AS (
    SELECT
        COALESCE(SUM(CASE WHEN net_immediate > 0 AND payment_mode = 'UPI' THEN net_immediate ELSE 0 END), 0) AS immediate_upi,
        COALESCE(SUM(CASE WHEN net_immediate > 0 AND payment_mode != 'UPI' THEN net_immediate ELSE 0 END), 0) AS immediate_cash
    FROM NetImmediateCalculated
)

SELECT 
    jp.total_khata AS udhar_recovery_total,
    jp.khata_cash AS udhar_recovery_cash,
    jp.khata_upi AS udhar_recovery_upi,
    it.immediate_cash AS immediate_cash,
    it.immediate_upi AS immediate_upi,
    (jp.khata_cash + it.immediate_cash) AS total_collected_cash,
    (jp.khata_upi + it.immediate_upi) AS total_collected_upi,
    (jp.khata_cash + it.immediate_cash + jp.khata_upi + it.immediate_upi) AS grand_total_collected
FROM JunePayments jp, ImmediateTotals it;
