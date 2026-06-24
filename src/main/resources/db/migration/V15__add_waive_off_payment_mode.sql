-- Recreate payment mode check constraint to include WAIVE_OFF
ALTER TABLE bills DROP CONSTRAINT IF EXISTS bills_payment_mode_check;
ALTER TABLE bills ADD CONSTRAINT bills_payment_mode_check CHECK (payment_mode::text = ANY (ARRAY['CASH'::text, 'UPI'::text, 'UDHAR'::text, 'PARTIAL'::text, 'COD'::text, 'WAIVE_OFF'::text]));

-- Recreate status check constraint to ensure COD states are included
ALTER TABLE bills DROP CONSTRAINT IF EXISTS bills_status_check;
ALTER TABLE bills ADD CONSTRAINT bills_status_check CHECK (status::text = ANY (ARRAY['DRAFT'::text, 'CONFIRMED'::text, 'PARTIAL'::text, 'PAID'::text, 'CANCELLED'::text, 'COD_PENDING'::text, 'COD_DELIVERED'::text, 'COD_COLLECTED'::text]));
