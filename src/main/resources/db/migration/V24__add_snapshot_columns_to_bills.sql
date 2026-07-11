ALTER TABLE bills ADD COLUMN shop_name VARCHAR(150) DEFAULT NULL;
ALTER TABLE bills ADD COLUMN shop_gstin VARCHAR(15) DEFAULT NULL;
ALTER TABLE bills ADD COLUMN shop_fssai VARCHAR(20) DEFAULT NULL;
ALTER TABLE bills ADD COLUMN shop_state_code VARCHAR(2) DEFAULT NULL;
ALTER TABLE bills ADD COLUMN is_legacy_snapshot BOOLEAN DEFAULT FALSE;

-- Mark all existing bills as legacy snapshots
UPDATE bills SET is_legacy_snapshot = TRUE;
