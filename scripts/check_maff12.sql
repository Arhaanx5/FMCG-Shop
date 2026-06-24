SELECT sm.id as movement_id, sm.batch_id as movement_batch_id, sm.quantity, sm.quantity_before, sm.quantity_after, sm.movement_type, sm.timestamp
FROM stock_movements sm
JOIN products p ON p.id = sm.product_id
WHERE p.name = 'Chips Masala-13Gm/Rs-5' AND sm.movement_type = 'PURCHASE'
ORDER BY sm.timestamp;

SELECT id, batch_number, invoice_number, received_at FROM stock_batches WHERE batch_number = 'MAFF12' ORDER BY received_at;

SELECT total_secondary_units FROM stock s JOIN products p ON p.id = s.product_id WHERE p.name = 'Chips Masala-13Gm/Rs-5';
