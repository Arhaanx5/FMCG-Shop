package com.shop.modules.billing;

import com.shop.modules.stock.StockBatch;
import com.shop.modules.stock.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SoftReserveScheduler {

    private final BillRepository billRepository;
    private final StockBatchRepository stockBatchRepository;

    // Run every hour (3600000 milliseconds)
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredSoftReservations() {
        log.info("[SOFT-RESERVE CLEANUP] Starting hourly check for expired draft bookings...");
        
        LocalDateTime cutoff = LocalDateTime.now().minusHours(4);

        // Find all draft bills created more than 4 hours ago
        List<Bill> draftBills = billRepository.findAll().stream()
                .filter(b -> b.getStatus() == BillStatus.DRAFT && b.getCreatedAt().isBefore(cutoff))
                .toList();

        if (draftBills.isEmpty()) {
            log.info("[SOFT-RESERVE CLEANUP] No expired draft bookings found.");
            return;
        }

        log.info("[SOFT-RESERVE CLEANUP] Found {} expired draft bookings to cancel and release.", draftBills.size());

        for (Bill bill : draftBills) {
            try {
                // Release soft reservations
                for (BillItem item : bill.getItems()) {
                    StockBatch batch = item.getBatch();
                    if (batch != null && batch.getSecondarySoftReserved() != null) {
                        int qty = item.getQuantity() + item.getFreeQuantity();
                        boolean isPrimary = item.getUnitType().name().equalsIgnoreCase(item.getProduct().getPrimaryUnit());
                        int secondaryQty = isPrimary ? qty * item.getProduct().getSecondaryPerPrimary() : qty;
                        
                        int newReserved = batch.getSecondarySoftReserved() - secondaryQty;
                        batch.setSecondarySoftReserved(Math.max(0, newReserved));
                        stockBatchRepository.save(batch);
                    }
                }
                
                bill.setStatus(BillStatus.CANCELLED);
                bill.setNotes((bill.getNotes() != null ? bill.getNotes() : "") + " [Expired & Cancelled by Auto-Scheduler]");
                bill.setUpdatedAt(LocalDateTime.now());
                billRepository.save(bill);
                
                log.info("[SOFT-RESERVE CLEANUP] Bill {} successfully cancelled and stock reservations released.", bill.getBillNumber());
            } catch (Exception e) {
                log.error("[SOFT-RESERVE CLEANUP] Failed to clean up reservation for bill: {}", bill.getBillNumber(), e);
            }
        }
    }
}
