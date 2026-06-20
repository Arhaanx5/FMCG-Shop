ALTER TABLE stock_batches ADD COLUMN IF NOT EXISTS supplier_invoice_date DATE;
ALTER TABLE stock_batches ADD COLUMN IF NOT EXISTS stock_received_date DATE;
ALTER TABLE stock_batches ADD COLUMN IF NOT EXISTS manufacturing_date DATE;
ALTER TABLE stock_batches ADD COLUMN IF NOT EXISTS remarks VARCHAR(500);
ALTER TABLE stock_batches ADD COLUMN IF NOT EXISTS batch_status VARCHAR(50) DEFAULT 'ACTIVE';
