-- Bug #5: Add is_returned to bill_items to support soft delete/flagging returned items
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS is_returned BOOLEAN DEFAULT FALSE;

-- Bug #10: Add partial_payment_mode to bills to track whether immediate partial amount was paid in CASH or UPI
ALTER TABLE bills ADD COLUMN IF NOT EXISTS partial_payment_mode VARCHAR(20) DEFAULT NULL;
