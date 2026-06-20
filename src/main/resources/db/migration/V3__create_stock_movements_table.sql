CREATE TABLE IF NOT EXISTS stock_movements (
    id UUID PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    product_id UUID NOT NULL,
    batch_id UUID,
    movement_type VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    quantity_before INT,
    quantity_after INT,
    unit_price DECIMAL(15, 2),
    total_value DECIMAL(15, 2),
    username VARCHAR(100),
    reference_number VARCHAR(100),
    remarks VARCHAR(500),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_stock_movements_batch FOREIGN KEY (batch_id) REFERENCES stock_batches(id)
);

CREATE INDEX IF NOT EXISTS idx_stock_movements_product_time ON stock_movements(product_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_stock_movements_timestamp ON stock_movements(timestamp DESC);
