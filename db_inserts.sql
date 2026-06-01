-- ========================================================================================
-- FMCG SHOP DATABASE SEED SCRIPT (PostgreSQL)
-- Seeds a rich, professional catalog of 8 FMCG products across categories with active stock
-- ========================================================================================

BEGIN;

-- Cleanup existing product and stock records to prevent primary key or unique code conflicts
TRUNCATE TABLE stock_batches CASCADE;
TRUNCATE TABLE products CASCADE;

-- ==========================================
-- 1. SEED PRODUCTS
-- ==========================================
INSERT INTO products (
    id, product_code, name, brand, category, 
    gst_percent, cess_percent, primary_unit, secondary_unit, secondary_per_primary, 
    can_sell_primary, can_sell_secondary, buy_price_without_tax, buy_price_with_tax, 
    sell_price_primary, sell_price_secondary, low_stock_alert, low_stock_unit, 
    is_active, created_at, updated_at
) VALUES 
-- 1. Snacks (Lays)
(
    'd1a1b1c1-1111-2222-3333-444455556666', 'PROD-LAYS-001', 'Lays Chips Classic', 'PepsiCo', 'SNACKS',
    18.00, 0.00, 'BOX', 'PACK', 10,
    true, true, 150.00, 177.00,
    200.00, 20.00, 15, 'SECONDARY',
    true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
-- 2. Snacks (Kurkure)
(
    'd2a2b2c2-1111-2222-3333-444455556666', 'PROD-KURK-001', 'Kurkure Masala Munch', 'PepsiCo', 'SNACKS',
    18.00, 0.00, 'BOX', 'PACK', 10,
    true, true, 150.00, 177.00,
    200.00, 20.00, 15, 'SECONDARY',
    true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
-- 3. Beverages (Coca-Cola)
(
    'd3a3b3c3-1111-2222-3333-444455556666', 'PROD-COKE-001', 'Coca-Cola 250ml', 'Coca-Cola', 'BEVERAGES',
    28.00, 12.00, 'CRATE', 'BOTTLE', 24,
    true, true, 300.00, 420.00,
    400.00, 20.00, 30, 'SECONDARY',
    true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
-- 4. Beverages (Sprite)
(
    'd4a4b4c4-1111-2222-3333-444455556666', 'PROD-SPRI-001', 'Sprite 250ml', 'Coca-Cola', 'BEVERAGES',
    28.00, 12.00, 'CRATE', 'BOTTLE', 24,
    true, true, 300.00, 420.00,
    400.00, 20.00, 30, 'SECONDARY',
    true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
-- 5. Biscuits (Parle-G)
(
    'd5a5b5c5-1111-2222-3333-444455556666', 'PROD-PARL-001', 'Parle-G Biscuit Gold', 'Parle', 'BISCUITS',
    18.00, 0.00, 'BOX', 'PACK', 20,
    true, true, 80.00, 94.40,
    120.00, 7.00, 40, 'SECONDARY',
    true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
-- 6. Biscuits (Oreo)
(
    'd6a6b6c6-1111-2222-3333-444455556666', 'PROD-OREO-001', 'Oreo Chocolate Cookies', 'Cadbury', 'BISCUITS',
    18.00, 0.00, 'BOX', 'PACK', 15,
    true, true, 200.00, 236.00,
    270.00, 20.00, 20, 'SECONDARY',
    true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
-- 7. Namkeen (Haldiram Bhujia)
(
    'd7a7b7c7-1111-2222-3333-444455556666', 'PROD-HALD-001', 'Haldiram Bhujia Sev', 'Haldiram', 'NAMKEEN',
    12.00, 0.00, 'BOX', 'PACK', 12,
    true, true, 120.00, 134.40,
    160.00, 15.00, 20, 'SECONDARY',
    true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
-- 8. Cigarettes (Gold Flake)
(
    'd8a8b8c8-1111-2222-3333-444455556666', 'PROD-GFK-001', 'Gold Flake Kings', 'ITC', 'CIGARETTES',
    28.00, 36.00, 'BOX', 'PACK', 10,
    true, true, 1200.00, 1968.00,
    1500.00, 160.00, 10, 'SECONDARY',
    true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- ==========================================
-- 2. SEED STOCK BATCHES ( FIFO Inventory )
-- ==========================================
INSERT INTO stock_batches (
    id, product_id, batch_number, 
    primary_received, secondary_received, secondary_remaining, secondary_soft_reserved, 
    buy_price_without_tax, buy_price_with_tax, gst_percent, 
    expiry_date, received_at, supplier_name, is_exhausted
) VALUES
-- Batch for Lays (50 boxes = 500 packs)
(
    gen_random_uuid(), 'd1a1b1c1-1111-2222-3333-444455556666', 'BATCH-LAYS-001',
    50, 500, 500, 0,
    150.00, 177.00, 18.00,
    CURRENT_DATE + INTERVAL '1 year', CURRENT_TIMESTAMP, 'PepsiCo Distributors Ltd', false
),
-- Batch for Kurkure (50 boxes = 500 packs)
(
    gen_random_uuid(), 'd2a2b2c2-1111-2222-3333-444455556666', 'BATCH-KURK-001',
    50, 500, 500, 0,
    150.00, 177.00, 18.00,
    CURRENT_DATE + INTERVAL '1 year', CURRENT_TIMESTAMP, 'PepsiCo Distributors Ltd', false
),
-- Batch for Coca-Cola (40 crates = 960 bottles)
(
    gen_random_uuid(), 'd3a3b3c3-1111-2222-3333-444455556666', 'BATCH-COKE-001',
    40, 960, 960, 0,
    300.00, 420.00, 28.00,
    CURRENT_DATE + INTERVAL '6 months', CURRENT_TIMESTAMP, 'Coca-Cola Beverages Agency', false
),
-- Batch for Sprite (40 crates = 960 bottles)
(
    gen_random_uuid(), 'd4a4b4c4-1111-2222-3333-444455556666', 'BATCH-SPRI-001',
    40, 960, 960, 0,
    300.00, 420.00, 28.00,
    CURRENT_DATE + INTERVAL '6 months', CURRENT_TIMESTAMP, 'Coca-Cola Beverages Agency', false
),
-- Batch for Parle-G (100 boxes = 2000 packs)
(
    gen_random_uuid(), 'd5a5b5c5-1111-2222-3333-444455556666', 'BATCH-PARL-001',
    100, 2000, 2000, 0,
    80.00, 94.40, 18.00,
    CURRENT_DATE + INTERVAL '8 months', CURRENT_TIMESTAMP, 'Parle Biscuit Agency', false
),
-- Batch for Oreo (60 boxes = 900 packs)
(
    gen_random_uuid(), 'd6a6b6c6-1111-2222-3333-444455556666', 'BATCH-OREO-001',
    60, 900, 900, 0,
    200.00, 236.00, 18.00,
    CURRENT_DATE + INTERVAL '10 months', CURRENT_TIMESTAMP, 'Cadbury Confectionery Pvt Ltd', false
),
-- Batch for Haldiram Bhujia (80 boxes = 960 packs)
(
    gen_random_uuid(), 'd7a7b7c7-1111-2222-3333-444455556666', 'BATCH-HALD-001',
    80, 960, 960, 0,
    120.00, 134.40, 12.00,
    CURRENT_DATE + INTERVAL '1 year', CURRENT_TIMESTAMP, 'Haldiram Foods Distributors', false
),
-- Batch for Gold Flake Kings (20 boxes = 200 packs)
(
    gen_random_uuid(), 'd8a8b8c8-1111-2222-3333-444455556666', 'BATCH-GFK-001',
    20, 200, 200, 0,
    1200.00, 1968.00, 28.00,
    CURRENT_DATE + INTERVAL '2 years', CURRENT_TIMESTAMP, 'ITC Wholesales North', false
);

COMMIT;
