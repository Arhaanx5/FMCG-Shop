-- V20__add_supplier_details_to_stock_movements.sql
ALTER TABLE stock_movements ADD COLUMN supplier_invoice_date DATE;
ALTER TABLE stock_movements ADD COLUMN supplier_name VARCHAR(255);

-- Backfill existing movements using their associated batch properties
UPDATE stock_movements m
SET supplier_invoice_date = b.supplier_invoice_date,
    supplier_name = b.supplier_name
FROM stock_batches b
WHERE m.batch_id = b.id;
