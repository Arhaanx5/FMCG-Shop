-- Indexes to optimize case-insensitive searches on stock batches
CREATE INDEX idx_batch_number_lower ON stock_batches (LOWER(batch_number));
CREATE INDEX idx_supplier_name_lower ON stock_batches (LOWER(supplier_name));
CREATE INDEX idx_invoice_number_lower ON stock_batches (LOWER(invoice_number));
