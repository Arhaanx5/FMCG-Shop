CREATE TABLE IF NOT EXISTS cod_audit_logs (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL,
    delivery_boy_id UUID NOT NULL,
    event VARCHAR(100) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    old_mode VARCHAR(50),
    new_mode VARCHAR(50),
    timestamp TIMESTAMP NOT NULL,
    device_info VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_cod_audit_logs_delivery ON cod_audit_logs(delivery_id);
