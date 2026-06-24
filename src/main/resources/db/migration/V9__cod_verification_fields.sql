-- Delivery verification state tracking (Phase 1 fields only)
ALTER TABLE deliveries
    ADD COLUMN IF NOT EXISTS goods_received_by_customer       BOOLEAN DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS goods_received_at                TIMESTAMP,
    ADD COLUMN IF NOT EXISTS payment_acknowledged_by_customer BOOLEAN DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS payment_acknowledged_at          TIMESTAMP,
    ADD COLUMN IF NOT EXISTS awaiting_reply_type               VARCHAR(30) DEFAULT 'NONE';

-- Lookup index for webhook matching
CREATE INDEX IF NOT EXISTS idx_delivery_awaiting_reply
    ON deliveries(awaiting_reply_type)
    WHERE awaiting_reply_type != 'NONE';

-- Dispute audit trail
CREATE TABLE IF NOT EXISTS delivery_disputes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id      UUID NOT NULL REFERENCES deliveries(id),
    bill_id          UUID NOT NULL REFERENCES bills(id),
    dispute_type     VARCHAR(20) NOT NULL,        -- DELIVERY | PAYMENT
    status           VARCHAR(20) DEFAULT 'OPEN',  -- OPEN | RESOLVED | DISMISSED
    customer_phone   VARCHAR(20),
    raised_at        TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMP,
    resolution_notes VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_disputes_delivery ON delivery_disputes(delivery_id);
CREATE INDEX IF NOT EXISTS idx_disputes_status   ON delivery_disputes(status);

-- DB-level race guard. @Transactional alone does not stop two parallel
-- READ_COMMITTED transactions both seeing "no open dispute" and both
-- inserting (e.g. customer double-taps "NAHI MILA"). This index makes
-- the second concurrent insert fail at Postgres level; DisputeService
-- catches DataIntegrityViolationException and treats it as a no-op.
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_open_dispute
    ON delivery_disputes(delivery_id, dispute_type)
    WHERE status = 'OPEN';
