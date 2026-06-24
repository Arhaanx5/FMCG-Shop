ALTER TABLE damage_log ADD COLUMN supplier_name VARCHAR(255);

ALTER TABLE damage_log DROP CONSTRAINT IF EXISTS damage_log_reason_check;
ALTER TABLE damage_log ADD CONSTRAINT damage_log_reason_check CHECK (reason::text = ANY (ARRAY['LEAK'::text, 'CRUSH'::text, 'EXPIRE'::text, 'OTHER'::text, 'SUPPLIER_RETURN'::text]));
