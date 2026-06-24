-- ============================================================
-- Fix: Insert missing PURCHASE movements for invoice 26-27/SA-0950
-- Root cause: @Async + REQUIRES_NEW deadlock lost 10/11 movements
-- Date: 2026-06-23
-- ============================================================

BEGIN;

INSERT INTO stock_movements (
    id, timestamp, product_id, batch_id,
    movement_type, quantity, quantity_before, quantity_after,
    unit_price, total_value, username, reference_number, remarks
) VALUES

-- FCGF1106: Chips Masala-13Gm/Rs-5, +300 (100->400)
(gen_random_uuid(), '2026-06-22 23:47:58.415005',
 'e263acdc-4fd7-43a1-9f71-9fff02c12faa', '76d6492c-33ba-4cf7-b68e-c67137ad796e',
 'PURCHASE', 300, 100, 400, 46.44, 13932.00, '7084285785', '26-27/SA-0950', null),

-- MAFF03: Snac Lite Tomato Katori MRP 5/20 GM*2.880, +36 (0->36)
(gen_random_uuid(), '2026-06-22 23:47:58.518923',
 'd5416311-c7c8-4b70-b958-d7275b6eb44c', 'b642a054-5ade-4d43-afd4-e93749ef1b1e',
 'PURCHASE', 36, 0, 36, 42.72, 1537.92, '7084285785', '26-27/SA-0950', null),

-- MAFF05: Snaclite Finger-20Gm/Rs-5, +60 (0->60)
(gen_random_uuid(), '2026-06-22 23:47:58.468139',
 '57de4cf6-3a60-4e63-826b-1fad19adde76', '4587eb23-3201-43fa-be42-e08b070e436a',
 'PURCHASE', 60, 0, 60, 43.79, 2627.40, '7084285785', '26-27/SA-0950', null),

-- MAFF06: Snaclite Finger-20Gm/Rs-5, +48 (60->108)
(gen_random_uuid(), '2026-06-22 23:47:58.481696',
 '57de4cf6-3a60-4e63-826b-1fad19adde76', 'cf12b4ad-cddc-46ca-a158-9e2fe6a5aeff',
 'PURCHASE', 48, 60, 108, 42.59, 2044.32, '7084285785', '26-27/SA-0950', null),

-- MAFF07: Snaclite Katori 20Gm/Rs-5, +72 (12->84)
(gen_random_uuid(), '2026-06-22 23:47:58.508239',
 '1f9ba013-a347-4a10-85cc-32d50fb89798', 'c0456a68-b8d0-46c7-af57-ab48b3b0916e',
 'PURCHASE', 72, 12, 84, 43.79, 3152.88, '7084285785', '26-27/SA-0950', null),

-- MAFF17: Chips Pudina-13Gm/Rs-5, +100 (0->100)
(gen_random_uuid(), '2026-06-22 23:47:58.428607',
 'c8233574-2fff-4ead-92ce-8f5f57998528', '9fc3788c-1e7d-460f-8e23-7d72e06f55ea',
 'PURCHASE', 100, 0, 100, 46.44, 4644.00, '7084285785', '26-27/SA-0950', null),

-- MAFF18 (Chips Tomato MRP 5/13 GM*3.12 KG.): +160 (0->160)
(gen_random_uuid(), '2026-06-22 23:47:58.440020',
 'fa46dd01-feed-4f1c-8f2d-818b57112c1e', '1e5d2bfd-4f43-4be7-abdb-2e9be9bd3f88',
 'PURCHASE', 160, 0, 160, 46.44, 7430.40, '7084285785', '26-27/SA-0950', null),

-- MAFF18 (Snaclite Katori 20Gm/Rs-5): +12 (0->12)
(gen_random_uuid(), '2026-06-22 23:47:58.496740',
 '1f9ba013-a347-4a10-85cc-32d50fb89798', 'a5ab720f-1239-4125-a7c5-6e691c2621e6',
 'PURCHASE', 12, 0, 12, 43.79, 525.48, '7084285785', '26-27/SA-0950', null),

-- RAFF02: Panga Tangy-16Gm/Rs-5, +160 (0->160)
(gen_random_uuid(), '2026-06-22 23:47:58.455547',
 'cbc6d684-f556-467b-933f-e83e8450f333', '85acb629-9cc7-4316-83ab-71db89587429',
 'PURCHASE', 160, 0, 160, 46.44, 7430.40, '7084285785', '26-27/SA-0950', null),

-- RAFF05: Whoopies Puffcorn(Cheese)17GM*3.264KG, +48 (0->48)
(gen_random_uuid(), '2026-06-22 23:47:58.529649',
 '08e15853-7398-4335-b4ef-98d6c33078b4', '103dc4b2-fed9-4111-afbe-1e17d6a4a836',
 'PURCHASE', 48, 0, 48, 42.72, 2050.56, '7084285785', '26-27/SA-0950', null);

COMMIT;

-- Verify: should now show 11 total PURCHASE movements for SA-0950
SELECT sb.batch_number, p.name as product, sm.quantity, sm.quantity_before, sm.quantity_after, sm.unit_price, sm.total_value
FROM stock_movements sm
JOIN stock_batches sb ON sm.batch_id = sb.id
JOIN products p ON sm.product_id = p.id
WHERE sm.reference_number = '26-27/SA-0950' AND sm.movement_type = 'PURCHASE'
ORDER BY sm.timestamp;
