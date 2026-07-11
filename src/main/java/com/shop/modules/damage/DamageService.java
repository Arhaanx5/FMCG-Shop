package com.shop.modules.damage;

import com.shop.modules.damage.dto.DamageResponse;
import com.shop.modules.damage.dto.LogDamageRequest;
import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.product.ProductService;
import com.shop.modules.product.UnitType;
import com.shop.modules.stock.StockBatch;
import com.shop.modules.stock.StockBatchRepository;
import com.shop.modules.stock.StockService;
import com.shop.modules.stock.StockRepository;
import com.shop.modules.stock.StockInventoryService;
import com.shop.modules.stock.StockMovementService;
import com.shop.modules.stock.Stock;
import com.shop.modules.stock.BatchStatus;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DamageService {

    private final DamageLogRepository damageLogRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final StockBatchRepository batchRepository;
    private final StockService stockService;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final StockInventoryService inventoryService;
    private final StockMovementService movementService;
    private final DamageMapper damageMapper;



    public List<DamageResponse> getAllDamageLogs() {
        return damageLogRepository.findAll()
                .stream()
                .map(damageMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<DamageResponse> getMonthReport(
            int year, int month) {
        LocalDateTime start =
                LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);
        return damageLogRepository.findForMonth(start, end)
                .stream()
                .map(damageMapper::toResponse)
                .collect(Collectors.toList());
    }

    public BigDecimal getMonthTotalLoss(
            int year, int month) {
        LocalDateTime start =
                LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);
        BigDecimal total =
                damageLogRepository.getTotalDamageLoss(start, end);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Transactional
    public DamageResponse logDamage(
            LogDamageRequest req,
            String loggedByPhone) {

        if (req.getReason() == DamageReason.SUPPLIER_RETURN) {
            if (req.getSupplierName() == null || req.getSupplierName().trim().isEmpty()) {
                throw new IllegalArgumentException("Supplier name is required for Supplier Returns");
            }
            if (req.getBatchId() == null) {
                throw new IllegalArgumentException("Batch selection is required for Supplier Returns");
            }
        }

        Product product = productService
                .findProductByIdentifier(req.getProductId());

        User user = userRepository
                .findByPhone(loggedByPhone)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        StockBatch batch = null;
        if (req.getBatchId() != null) {
            batch = batchRepository
                    .findById(req.getBatchId())
                    .orElse(null);
        }

        // Determine unit level (fallback if null)
        UnitLevel level = req.getUnitLevel();
        if (level == null) {
            if (req.getUnitType() != null && req.getUnitType().name().equalsIgnoreCase(product.getPrimaryUnit())) {
                level = UnitLevel.PRIMARY;
            } else {
                level = UnitLevel.SECONDARY;
            }
        }

        // Determine claim status (fallback if null)
        ClaimStatus claimStatus = req.getClaimStatus();
        if (claimStatus == null) {
            if (req.getReason() == DamageReason.SUPPLIER_RETURN) {
                claimStatus = ClaimStatus.CLAIMABLE;
            } else {
                claimStatus = ClaimStatus.NON_CLAIMABLE;
            }
        }

        // Determine unit type to persist
        UnitType finalUnitType = req.getUnitType();
        if (finalUnitType == null) {
            if (level == UnitLevel.PRIMARY) {
                finalUnitType = UnitType.BOX; // basic fallback
            } else if (level == UnitLevel.SECONDARY) {
                finalUnitType = UnitType.LADI; // basic fallback
            } else {
                finalUnitType = UnitType.SINGLE;
            }
        }

        // Calculate value loss and total secondary units
        BigDecimal buyPrice = batch != null
                ? batch.getBuyPriceWithTax()
                : product.getBuyPriceWithTax();

        int totalSecondaryQuantity = 0;
        BigDecimal valueLoss = BigDecimal.ZERO;

        BigDecimal buyPricePerSecondary = product.getBuyPricePerSecondary();
        if (batch != null) {
            buyPricePerSecondary = batch.getBuyPricePerSecondary(product.getSecondaryPerPrimary());
        }

        if (level == UnitLevel.PRIMARY) {
            totalSecondaryQuantity = req.getQuantity() * product.getSecondaryPerPrimary();
            valueLoss = buyPrice.multiply(BigDecimal.valueOf(req.getQuantity()));
        } else if (level == UnitLevel.SECONDARY) {
            totalSecondaryQuantity = req.getQuantity();
            valueLoss = buyPricePerSecondary.multiply(BigDecimal.valueOf(req.getQuantity()));
        } else if (level == UnitLevel.SINGLE) {
            int divisor = (product.getSecondaryUnit() != null && product.getSecondaryUnit().equalsIgnoreCase("LADI")) ? 10 : 1;
            totalSecondaryQuantity = (req.getQuantity() + divisor - 1) / divisor;
            BigDecimal buyPricePerSingle = buyPricePerSecondary.divide(BigDecimal.valueOf(divisor), 4, java.math.RoundingMode.HALF_UP);
            valueLoss = buyPricePerSingle.multiply(BigDecimal.valueOf(req.getQuantity()));
            finalUnitType = UnitType.SINGLE;
        }

        DamageLog log = DamageLog.builder()
                .product(product)
                .batch(batch)
                .unitType(finalUnitType)
                .unitLevel(level)
                .claimStatus(claimStatus)
                .quantity(req.getQuantity())
                .reason(req.getReason())
                .valueLoss(valueLoss)
                .notes(req.getNotes())
                .loggedBy(user)
                .supplierName(req.getReason() == DamageReason.SUPPLIER_RETURN ? req.getSupplierName() : null)
                .build();

        // Deduct exact secondary quantity from stock
        String movementType = req.getReason() == DamageReason.SUPPLIER_RETURN ? "RETURN_OUT" : "DAMAGE";
        if (totalSecondaryQuantity > 0) {
            stockService.deductBySecondary(
                    product.getId(),
                    totalSecondaryQuantity,
                    req.getBatchId(),
                    user.getPhone(),
                    null,
                    buyPricePerSecondary,
                    req.getNotes() != null ? req.getNotes() : (req.getReason() == DamageReason.SUPPLIER_RETURN ? "Return to Supplier" : "Damage Logged"),
                    movementType
            );
        }

        return damageMapper.toResponse(damageLogRepository.save(log));
    }

    // ── Delete damage log (ADMIN only) ──
    // Restores the stock that was deducted when this damage was logged,
    // then permanently removes the damage record.
    @Transactional
    public void deleteDamageLog(UUID logId) {

        DamageLog log = damageLogRepository
                .findById(logId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Damage log not found: " + logId));

        Product product = log.getProduct();
        UnitLevel level = log.getUnitLevel();

        // Recalculate the secondary units that were originally deducted
        int secondaryToRestore = 0;
        if (level == UnitLevel.PRIMARY) {
            secondaryToRestore = log.getQuantity()
                    * product.getSecondaryPerPrimary();
        } else if (level == UnitLevel.SECONDARY) {
            secondaryToRestore = log.getQuantity();
        } else if (level == UnitLevel.SINGLE) {
            int divisor = (product.getSecondaryUnit() != null
                    && product.getSecondaryUnit()
                    .equalsIgnoreCase("LADI")) ? 10 : 1;
            secondaryToRestore = (log.getQuantity()
                    + divisor - 1) / divisor;
        }

        // Add stock back to inventory
        String movementType = log.getReason() == DamageReason.SUPPLIER_RETURN ? "RETURN_IN" : "DAMAGE_REVERSAL";
        if (secondaryToRestore > 0) {
            int primaryToRestore = (level == UnitLevel.PRIMARY)
                    ? log.getQuantity() : 0;
            stockService.addBackStockToBatch(
                    product.getId(),
                    log.getBatch() != null ? log.getBatch().getId() : null,
                    primaryToRestore,
                    secondaryToRestore,
                    "System",
                    null,
                    product.getBuyPricePerSecondary(),
                    log.getBatch() != null ? "Restored specific batch stock" : "Restored general inventory stock",
                    movementType);
        }

        damageLogRepository.delete(log);
    }

    // ── Update damage log details (ADMIN/MANAGER only) ──
    @Transactional
    public DamageResponse updateDamageLog(UUID id, ClaimStatus claimStatus, String notes) {
        DamageLog log = damageLogRepository
                .findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Damage log not found: " + id));

        if (claimStatus != null) {
            log.setClaimStatus(claimStatus);
        }
        if (notes != null) {
            log.setNotes(notes);
        }

        return damageMapper.toResponse(damageLogRepository.save(log));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markBatchDamage(UUID batchId, int quantity, String damageType, String reason, String username) {
        if (quantity <= 0) {
            throw new RuntimeException("Quantity must be greater than 0");
        }

        StockBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found: " + batchId));

        int available = batch.getSecondaryRemaining() != null ? batch.getSecondaryRemaining() : 0;
        if (available < quantity) {
            throw new RuntimeException("Insufficient stock in batch " + batch.getBatchNumber()
                    + " | Available: " + available + " | Requested: " + quantity);
        }

        Product product = batch.getProduct();
        
        // Compute value loss
        BigDecimal buyPricePerSecondary = product.getBuyPricePerSecondary();
        if (buyPricePerSecondary == null || buyPricePerSecondary.compareTo(BigDecimal.ZERO) == 0) {
            buyPricePerSecondary = batch.getBuyPricePerSecondary(product.getSecondaryPerPrimary());
        }
        BigDecimal valueLoss = buyPricePerSecondary.multiply(BigDecimal.valueOf(quantity));

        User user = null;
        if (username != null && !username.equals("System")) {
            user = userRepository.findByPhone(username).orElse(null);
        }

        // Map damageType to ClaimStatus
        ClaimStatus claimStatus = ClaimStatus.NON_CLAIMABLE;
        if ("PERMANENT".equalsIgnoreCase(damageType)) {
            claimStatus = ClaimStatus.PERMANENT_LOSS;
        } else if ("RECLAIMABLE".equalsIgnoreCase(damageType)) {
            claimStatus = ClaimStatus.CLAIMABLE;
        }

        // Map reason string to DamageReason enum
        DamageReason damageReason = DamageReason.OTHER;
        String notes = reason;
        try {
            damageReason = DamageReason.valueOf(reason.toUpperCase().trim());
            notes = "Marked as damage from batch action.";
        } catch (IllegalArgumentException e) {
            // Keep DamageReason.OTHER and use the original reason as notes
        }

        // Create DamageLog
        UnitType unitType = UnitType.SINGLE;
        if (product.getSecondaryUnit() != null) {
            try {
                unitType = UnitType.valueOf(product.getSecondaryUnit().toUpperCase().trim());
            } catch (Exception ignored) {}
        }

        DamageLog damage = DamageLog.builder()
                .product(product)
                .batch(batch)
                .unitType(unitType)
                .unitLevel(UnitLevel.SECONDARY)
                .claimStatus(claimStatus)
                .quantity(quantity)
                .reason(damageReason)
                .valueLoss(valueLoss)
                .notes(notes)
                .loggedBy(user)
                .build();
        damageLogRepository.save(damage);

        // Deduct from batch
        int qtyBefore = batch.getSecondaryRemaining();
        int qtyAfter = qtyBefore - quantity;
        batch.setSecondaryRemaining(qtyAfter);
        batch.setExhausted(qtyAfter == 0);
        batchRepository.save(batch);

        // Deduct from total stock
        Stock stock = inventoryService.getOrCreateStock(product.getId());
        int totalQtyBefore = stock.getTotalSecondaryUnits();
        int totalQtyAfter = Math.max(0, totalQtyBefore - quantity);
        stock.setTotalSecondaryUnits(totalQtyAfter);
        inventoryService.normalizeStock(stock, product);
        stockRepository.save(stock);

        // Log movement as DAMAGE
        movementService.logMovement(
                product,
                batch,
                "DAMAGE",
                -quantity,
                totalQtyBefore,
                totalQtyAfter,
                buyPricePerSecondary,
                username,
                batch.getInvoiceNumber(),
                reason
        );
    }
}