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

    // Convert entity to DTO
    private DamageResponse toResponse(DamageLog log) {
        return DamageResponse.builder()
                .id(log.getId())
                .productId(log.getProduct().getId())
                .productName(log.getProduct().getName())
                .brand(log.getProduct().getBrand())
                .batchNumber(log.getBatch() != null
                        ? log.getBatch().getBatchNumber() : null)
                .unitType(log.getUnitType())
                .quantity(log.getQuantity())
                .reason(log.getReason())
                .valueLoss(log.getValueLoss())
                .unitLevel(log.getUnitLevel())
                .claimStatus(log.getClaimStatus())
                .notes(log.getNotes())
                .loggedBy(log.getLoggedBy() != null
                        ? log.getLoggedBy().getName() : null)
                .loggedAt(log.getLoggedAt())
                .supplierName(log.getSupplierName())
                .build();
    }

    public List<DamageResponse> getAllDamageLogs() {
        return damageLogRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<DamageResponse> getMonthReport(
            int year, int month) {
        LocalDateTime start =
                LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);
        return damageLogRepository.findForMonth(start, end)
                .stream()
                .map(this::toResponse)
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

        return toResponse(damageLogRepository.save(log));
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

        return toResponse(damageLogRepository.save(log));
    }
}