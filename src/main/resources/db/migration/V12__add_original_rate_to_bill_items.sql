ALTER TABLE bill_items ADD COLUMN original_rate NUMERIC(19, 4);
UPDATE bill_items SET original_rate = rate WHERE original_rate IS NULL;
