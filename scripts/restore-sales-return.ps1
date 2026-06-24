# Interactive Script to Undo a Sales Return

Clear-Host
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "         Lari Traders - Undo Sales Return Utility       " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host

# 1. Environment Selection
Write-Host "Select Environment:"
Write-Host "1) Production (fmcg_shop_prod)"
Write-Host "2) UAT / Testing (fmcg_shop)"
Write-Host
$envChoice = Read-Host "Enter choice [1-2]"
$dbName = "fmcg_shop_prod"
if ($envChoice -eq "2") {
    $dbName = "fmcg_shop"
}

# 2. Input Bill Number
Write-Host
$billNumber = Read-Host "Enter the Bill Number to restore (e.g., BILL-00007)"
$billNumber = $billNumber.Trim().ToUpper()

if (-not $billNumber) {
    Write-Host "[ERROR] Bill number cannot be empty." -ForegroundColor Red
    pause
    exit
}

# Set PostgreSQL Password
$env:PGPASSWORD = "9450"
$psqlPath = "C:\Program Files\PostgreSQL\16\bin\psql.exe"

if (-not (Test-Path $psqlPath)) {
    Write-Host "[ERROR] PostgreSQL client not found at $psqlPath" -ForegroundColor Red
    pause
    exit
}

# 3. Check for returned items on this bill
Write-Host
Write-Host "Checking for returns on $billNumber in database $dbName..." -ForegroundColor Yellow

$checkQuery = @"
SELECT 
    sm.id AS movement_id,
    p.name AS product_name,
    sm.quantity AS returned_qty_secondary,
    sm.unit_price
FROM stock_movements sm
JOIN products p ON p.id = sm.product_id
WHERE sm.reference_number = '$billNumber' 
  AND sm.movement_type = 'RETURN_IN';
"@

$returns = & $psqlPath -h localhost -U postgres -d $dbName -c $checkQuery

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Failed to query database." -ForegroundColor Red
    pause
    exit
}

Write-Host $returns

# Check if query returned any rows (usually output has header and dashed line, check if it contains actual data)
# Let's count rows by running a clean count query
$countQuery = "SELECT COUNT(*) FROM stock_movements WHERE reference_number = '$billNumber' AND movement_type = 'RETURN_IN';"
$countResult = & $psqlPath -h localhost -U postgres -d $dbName -t -A -c $countQuery
$count = [int]$countResult.Trim()

if ($count -eq 0) {
    Write-Host "[INFO] No RETURN_IN stock movements found for $billNumber. Nothing to restore." -ForegroundColor Cyan
    Write-Host "Either this bill has no returns, or they have already been restored." -ForegroundColor Cyan
    pause
    exit
}

# 4. Confirmation
Write-Host
$confirm = Read-Host "Do you want to UNDO the return and RESTORE these items on $billNumber? (Y/N)"
if ($confirm.Trim().ToUpper() -ne "Y") {
    Write-Host "Operation cancelled." -ForegroundColor Yellow
    pause
    exit
}

# 5. Generate and execute restoration SQL block
$sqlBlock = @"
DO \$$
DECLARE
    v_bill_number VARCHAR := '$billNumber';
    v_bill_id UUID;
    v_r RECORD;
    v_item_id UUID;
    v_qty_to_restore INT;
    v_rate NUMERIC;
    v_gst_percent NUMERIC;
    v_gst_amount NUMERIC;
    v_total_amount NUMERIC;
    v_unit_type VARCHAR;
    v_is_primary BOOLEAN;
    v_subtotal_change NUMERIC := 0;
    v_gst_change NUMERIC := 0;
    v_grand_total_change NUMERIC := 0;
    v_old_pending NUMERIC;
    v_old_paid NUMERIC;
    v_new_paid NUMERIC;
    v_new_pending NUMERIC;
BEGIN
    -- Find bill details
    SELECT id, pending_amount, paid_amount INTO v_bill_id, v_old_pending, v_old_paid
    FROM bills WHERE bill_number = v_bill_number;
    
    IF v_bill_id IS NULL THEN
        RAISE EXCEPTION 'Bill % not found', v_bill_number;
    END IF;
    
    -- Loop through all RETURN_IN movements for this bill
    FOR v_r IN (
        SELECT sm.id AS movement_id, sm.product_id, sm.batch_id, sm.quantity AS returned_qty_secondary, 
               p.secondary_per_primary, p.primary_unit, p.secondary_unit, p.gst_percent
        FROM stock_movements sm
        JOIN products p ON p.id = sm.product_id
        WHERE sm.reference_number = v_bill_number AND sm.movement_type = 'RETURN_IN'
    ) LOOP
        -- Check if bill_item already exists
        SELECT id, unit_type, rate INTO v_item_id, v_unit_type, v_rate
        FROM bill_items 
        WHERE bill_id = v_bill_id AND product_id = v_r.product_id AND batch_id = v_r.batch_id
        LIMIT 1;
        
        -- Fallback default values
        IF v_unit_type IS NULL THEN
            v_unit_type := v_r.primary_unit;
        END IF;
        v_is_primary := (v_unit_type = v_r.primary_unit);
        
        IF v_is_primary THEN
            v_qty_to_restore := v_r.returned_qty_secondary / v_r.secondary_per_primary;
        ELSE
            v_qty_to_restore := v_r.returned_qty_secondary;
        END IF;
        
        IF v_rate IS NULL THEN
            -- Check from a sale movement
            SELECT ABS(unit_price) INTO v_rate 
            FROM stock_movements 
            WHERE product_id = v_r.product_id AND reference_number = v_bill_number AND movement_type = 'SALE'
            LIMIT 1;
            
            IF v_rate IS NULL THEN
                SELECT sell_price_primary INTO v_rate FROM products WHERE id = v_r.product_id;
            END IF;
        END IF;
        
        v_gst_percent := v_r.gst_percent;
        v_total_amount := v_qty_to_restore * v_rate;
        v_gst_amount := ROUND(v_total_amount * v_gst_percent / 100.0, 2);
        
        -- Cumulative bill changes
        v_subtotal_change := v_subtotal_change + v_total_amount;
        v_gst_change := v_gst_change + v_gst_amount;
        v_grand_total_change := v_grand_total_change + v_total_amount + v_gst_amount;
        
        -- Update or Insert bill_item
        IF v_item_id IS NOT NULL THEN
            UPDATE bill_items 
            SET quantity = quantity + v_qty_to_restore,
                total = total + v_total_amount + v_gst_amount,
                gst_amount = gst_amount + v_gst_amount
            WHERE id = v_item_id;
        ELSE
            INSERT INTO bill_items (id, bill_id, product_id, batch_id, unit_type, quantity, free_quantity, rate, original_rate, gst_percent, gst_amount, cess_percent, cess_amount, total, is_offer)
            VALUES (
                gen_random_uuid(), 
                v_bill_id, 
                v_r.product_id, 
                v_r.batch_id, 
                v_unit_type, 
                v_qty_to_restore, 
                0, 
                v_rate, 
                v_rate, 
                v_gst_percent, 
                v_gst_amount, 
                0, 
                0, 
                v_total_amount + v_gst_amount, 
                false
            );
        END IF;
        
        -- Deduct from stock batch remaining
        UPDATE stock_batches 
        SET secondary_remaining = secondary_remaining - v_r.returned_qty_secondary 
        WHERE id = v_r.batch_id;
        
        -- Deduct from main stock record and normalize
        UPDATE stock 
        SET total_secondary_units = total_secondary_units - v_r.returned_qty_secondary
        WHERE product_id = v_r.product_id;
        
        UPDATE stock 
        SET total_primary_units = total_secondary_units / v_r.secondary_per_primary,
            open_primary_remaining = total_secondary_units % v_r.secondary_per_primary,
            has_open_primary = (total_secondary_units % v_r.secondary_per_primary) > 0
        WHERE product_id = v_r.product_id;
        
    END LOOP;
    
    -- Update bill totals
    IF v_old_pending = 0 THEN
        v_new_paid := v_old_paid + v_grand_total_change;
        v_new_pending := 0;
    ELSE
        v_new_paid := v_old_paid;
        v_new_pending := v_old_pending + v_grand_total_change;
    END IF;
    
    UPDATE bills 
    SET subtotal = subtotal + v_subtotal_change,
        gst_total = gst_total + v_gst_change,
        grand_total = grand_total + v_grand_total_change,
        paid_amount = v_new_paid,
        pending_amount = v_new_pending,
        status = CASE WHEN v_new_pending = 0 THEN 'PAID' ELSE 'PARTIAL' END
    WHERE id = v_bill_id;
    
    -- Delete RETURN_IN movements
    DELETE FROM stock_movements 
    WHERE reference_number = v_bill_number AND movement_type = 'RETURN_IN';
    
END \$$;
"@

Write-Host "Restoring..." -ForegroundColor Yellow
$executionResult = & $psqlPath -h localhost -U postgres -d $dbName -c $sqlBlock

if ($LASTEXITCODE -eq 0) {
    Write-Host "[SUCCESS] Bill $billNumber successfully restored!" -ForegroundColor Green
    Write-Host "Stock amounts reverted and return logs cleared." -ForegroundColor Green
} else {
    Write-Host "[ERROR] Failed to restore bill." -ForegroundColor Red
}

pause
