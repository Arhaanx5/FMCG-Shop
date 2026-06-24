-- ============================================================
-- Fix: Insert missing PURCHASE movements for invoice 26-27/SA-0952
-- Missing: MAFF19, RAFF10, RAFF16, MAFF18 (Takatak Masala)
-- Root cause: Same @Async deadlock bug, occurred before fix was deployed
-- Date: 2026-06-23
-- ============================================================

BEGIN;

INSERT INTO stock_movements (
    id, timestamp, product_id, batch_id,
    movement_type, quantity, quantity_before, quantity_after,
    unit_price, total_value, username, reference_number, remarks
) VALUES

-- MAFF19: Chips Classic Salted-13Gm/Rs-5, +720 (0->720) [new product]
(gen_random_uuid(), '2026-06-23 00:06:48.291487',
 '5fcac496-07c6-4481-a867-2c2df66c54d1', '910ec2c2-29a7-4090-a5f8-b9b6e1a3ca07',
 'PURCHASE', 720, 0, 720, 46.44, 33436.32, '7084285785', '26-27/SA-0952', null),

-- RAFF10: Panga Tangy Tomato MRP 20|82GM*4.92KG, +120 (0->120) [new product]
-- unit_price = 801.09 / 60 = 13.35
(gen_random_uuid(), '2026-06-23 00:06:48.298051',
 '6bb4e685-e04b-42d9-a51a-eee920995a08', 'd398f7e3-bc4b-4315-80f9-288114b591dc',
 'PURCHASE', 120, 0, 120, 13.35, 1602.18, '7084285785', '26-27/SA-0952', null),

-- RAFF16: Snaclite Finger MRP 20|80 GM*3.36 KG, +42 (0->42) [new product]
-- unit_price = 560.76 / 42 = 13.35
(gen_random_uuid(), '2026-06-23 00:06:48.307085',
 'f14179a3-507e-4b73-aab4-222e2619fb8c', 'cc67f351-0abd-4026-bd10-5556a1008f70',
 'PURCHASE', 42, 0, 42, 13.35, 560.76, '7084285785', '26-27/SA-0952', null),

-- MAFF18: Takatak Masala-20Gm/Rs-5, +720 (0->720) [new product]
-- unit_price = 852.17 / 20 = 42.61
(gen_random_uuid(), '2026-06-23 00:06:48.317647',
 'f4c27037-19dd-46ef-9218-c57c23c36c09', '0eb11340-e1d0-4d41-8ad0-6c2803226ded',
 'PURCHASE', 720, 0, 720, 42.61, 30678.12, '7084285785', '26-27/SA-0952', null);

COMMIT;

-- Verify
SELECT sb.batch_number, p.name, sm.quantity, sm.quantity_before, sm.quantity_after
FROM stock_movements sm
JOIN stock_batches sb ON sm.batch_id = sb.id
JOIN products p ON sm.product_id = p.id
WHERE sm.reference_number = '26-27/SA-0952' AND sm.movement_type = 'PURCHASE'
ORDER BY sm.timestamp;
