CREATE TABLE IF NOT EXISTS daily_reconciliations (
    id UUID PRIMARY KEY,
    delivery_boy_id UUID NOT NULL,
    date DATE NOT NULL,
    expected_collection DECIMAL(15, 2) NOT NULL,
    submitted_collection DECIMAL(15, 2) NOT NULL,
    gap DECIMAL(15, 2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    admin_notes VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_daily_reconciliation_boy ON daily_reconciliations(delivery_boy_id, date);
