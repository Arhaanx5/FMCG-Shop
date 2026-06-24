-- Drop old status check constraints and recreate with new enums
ALTER TABLE bills DROP CONSTRAINT IF EXISTS bills_status_check;
ALTER TABLE bills ADD CONSTRAINT bills_status_check CHECK (status::text = ANY (ARRAY['DRAFT'::text, 'CONFIRMED'::text, 'PARTIAL'::text, 'PAID'::text, 'CANCELLED'::text, 'COD_PENDING'::text, 'COD_DELIVERED'::text, 'COD_COLLECTED'::text]));

ALTER TABLE bills DROP CONSTRAINT IF EXISTS bills_payment_mode_check;
ALTER TABLE bills ADD CONSTRAINT bills_payment_mode_check CHECK (payment_mode::text = ANY (ARRAY['CASH'::text, 'UPI'::text, 'UDHAR'::text, 'PARTIAL'::text, 'COD'::text]));

ALTER TABLE deliveries DROP CONSTRAINT IF EXISTS deliveries_status_check;
ALTER TABLE deliveries ADD CONSTRAINT deliveries_status_check CHECK (status::text = ANY (ARRAY['PENDING'::text, 'PACKED'::text, 'OUT'::text, 'DELIVERED'::text, 'FAILED'::text, 'PARTIAL'::text, 'COD_PENDING_PAYMENT'::text, 'COD_PARTIAL'::text, 'COD_COLLECTED'::text, 'COD_DEFAULTED'::text]));
