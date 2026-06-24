CREATE SEQUENCE IF NOT EXISTS bill_number_seq;

SELECT setval('bill_number_seq', 
    COALESCE(
        (SELECT MAX(CAST(SUBSTRING(bill_number, 6) AS INT)) FROM bills WHERE bill_number LIKE 'BILL-%'), 
        0
    ) + 1, 
    false
);

CREATE TABLE bill_item_batch_deductions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_item_id UUID NOT NULL REFERENCES bill_items(id) ON DELETE CASCADE,
    batch_id UUID NOT NULL REFERENCES stock_batches(id),
    quantity_deducted INT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_bill_item_batch_deductions_item ON bill_item_batch_deductions(bill_item_id);
