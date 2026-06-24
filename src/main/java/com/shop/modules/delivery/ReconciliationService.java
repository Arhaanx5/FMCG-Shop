package com.shop.modules.delivery;

import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.modules.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final DailyReconciliationRepository dailyReconciliationRepository;
    private final DeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final CODWhatsAppService codWhatsAppService;

    public List<DailyReconciliation> getAllReconciliations() {
        return dailyReconciliationRepository.findAll();
    }

    public List<DailyReconciliation> getReconciliationsByDate(LocalDate date) {
        return dailyReconciliationRepository.findByDate(date);
    }

    public List<DailyReconciliation> getBoyReconciliations(UUID boyId) {
        return dailyReconciliationRepository.findByDeliveryBoyIdOrderByDateDesc(boyId);
    }

    @Transactional
    public DailyReconciliation submitCollection(UUID deliveryBoyId, LocalDate date, BigDecimal submittedCollection, String adminNotes) {
        User boy = userRepository.findById(deliveryBoyId)
                .orElseThrow(() -> new RuntimeException("Delivery boy not found"));

        BigDecimal expected = calculateExpectedCollectionForBoyAndDate(deliveryBoyId, date);
        BigDecimal gap = expected.subtract(submittedCollection);

        Optional<DailyReconciliation> existingOpt = dailyReconciliationRepository.findByDeliveryBoyIdAndDate(deliveryBoyId, date);
        DailyReconciliation recon;
        if (existingOpt.isPresent()) {
            recon = existingOpt.get();
            recon.setExpectedCollection(expected);
            recon.setSubmittedCollection(submittedCollection);
            recon.setGap(gap);
            recon.setAdminNotes(adminNotes);
            if (gap.compareTo(BigDecimal.ZERO) == 0) {
                recon.setStatus("APPROVED");
            } else {
                recon.setStatus("PENDING");
            }
        } else {
            recon = DailyReconciliation.builder()
                    .deliveryBoyId(deliveryBoyId)
                    .date(date)
                    .expectedCollection(expected)
                    .submittedCollection(submittedCollection)
                    .gap(gap)
                    .status(gap.compareTo(BigDecimal.ZERO) == 0 ? "APPROVED" : "PENDING")
                    .adminNotes(adminNotes)
                    .build();
        }

        DailyReconciliation saved = dailyReconciliationRepository.save(recon);

        // Send alert to admin if gap exists
        if (gap.compareTo(BigDecimal.ZERO) != 0) {
            List<User> admins = userRepository.findByRole(UserRole.ADMIN);
            for (User admin : admins) {
                if (admin.getPhone() != null && !admin.getPhone().isEmpty()) {
                    codWhatsAppService.sendDailyReconciliationReport(saved, boy.getName(), admin.getPhone());
                }
            }
        }

        return saved;
    }

    @Transactional
    public DailyReconciliation updateReconciliationStatus(UUID id, String status, String adminNotes) {
        DailyReconciliation recon = dailyReconciliationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reconciliation record not found"));
        recon.setStatus(status);
        if (adminNotes != null) {
            recon.setAdminNotes(adminNotes);
        }
        return dailyReconciliationRepository.save(recon);
    }

    public BigDecimal calculateExpectedCollectionForBoyAndDate(UUID boyId, LocalDate date) {
        // Find all deliveries completed by this delivery boy on this date
        List<Delivery> deliveries = deliveryRepository.findByDeliveryBoyAndDate(boyId, date);
        
        return deliveries.stream()
                .filter(d -> d.getStatus() == DeliveryStatus.COD_COLLECTED || d.getStatus() == DeliveryStatus.COD_PARTIAL)
                .map(d -> d.getCashCollected() != null ? d.getCashCollected() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public List<DailyReconciliation> generateEodReconciliations(LocalDate date) {
        log.info("Generating EOD reconciliations for {}", date);
        List<DailyReconciliation> created = new ArrayList<>();

        // Find all delivery boys who have deliveries on this date
        List<User> deliveryBoys = userRepository.findByRole(UserRole.DELIVERY_BOY);
        for (User boy : deliveryBoys) {
            BigDecimal expected = calculateExpectedCollectionForBoyAndDate(boy.getId(), date);
            
            // Only generate reconciliation if there was some expected collection or active deliveries today
            List<Delivery> todayDeliveries = deliveryRepository.findByDeliveryBoyAndDate(boy.getId(), date);
            if (!todayDeliveries.isEmpty() || expected.compareTo(BigDecimal.ZERO) > 0) {
                Optional<DailyReconciliation> existingOpt = dailyReconciliationRepository.findByDeliveryBoyIdAndDate(boy.getId(), date);
                if (existingOpt.isEmpty()) {
                    DailyReconciliation recon = DailyReconciliation.builder()
                            .deliveryBoyId(boy.getId())
                            .date(date)
                            .expectedCollection(expected)
                            .submittedCollection(BigDecimal.ZERO)
                            .gap(expected)
                            .status("PENDING")
                            .adminNotes("Generated automatically by EOD Scheduler")
                            .build();
                    created.add(dailyReconciliationRepository.save(recon));
                    log.info("Auto-created PENDING EOD reconciliation for boy {}: Expected ₹{}", boy.getName(), expected);
                } else {
                    DailyReconciliation existing = existingOpt.get();
                    existing.setExpectedCollection(expected);
                    existing.setGap(expected.subtract(existing.getSubmittedCollection()));
                    dailyReconciliationRepository.save(existing);
                    created.add(existing);
                }
            }
        }
        return created;
    }
}
