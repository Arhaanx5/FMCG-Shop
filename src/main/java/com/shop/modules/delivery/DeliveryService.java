package com.shop.modules.delivery;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.billing.PaymentMode;
import com.shop.modules.delivery.dto.CompleteDeliveryRequest;
import com.shop.modules.khata.KhataService;
import com.shop.modules.khata.dto.RecordPaymentRequest;
import com.shop.modules.khata.AdjustmentType;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
@Slf4j
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final BillRepository billRepository;
    private final UserRepository userRepository;
    private final CODAuditLogRepository codAuditLogRepository;
    private final KhataService khataService;
    private final SimpMessagingTemplate messagingTemplate;
    private final CODWhatsAppService codWhatsAppService;
    private final CODFraudDetectionService codFraudDetectionService;

    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }

    public List<Delivery> getBoyDeliveriesToday(UUID boyId) {
        return deliveryRepository
            .findByDeliveryBoyAndDate(boyId, LocalDate.now());
    }

    @Transactional
    public Delivery assignDelivery(AssignDeliveryRequest req, User completedBy) {
        Bill bill = billRepository
                .findById(req.getBillId())
                .orElseThrow(() ->
                    new RuntimeException("Bill not found"));

        User boy = req.getDeliveryBoyId() != null
                ? userRepository
                    .findById(req.getDeliveryBoyId())
                    .orElse(null)
                : null;

        Delivery delivery = Delivery.builder()
                .bill(bill)
                .deliveryBoy(boy)
                .type(req.getType())
                .scheduledDate(req.getScheduledDate())
                .status(req.getType() == DeliveryType.SELF_PICKUP ? DeliveryStatus.DELIVERED : DeliveryStatus.PENDING)
                .deliveredAt(req.getType() == DeliveryType.SELF_PICKUP ? LocalDateTime.now() : null)
                .completedBy(req.getType() == DeliveryType.SELF_PICKUP ? completedBy : null)
                .cashCollected(BigDecimal.ZERO)
                .build();

        Delivery saved = deliveryRepository.save(delivery);
        if (saved.getStatus() == DeliveryStatus.DELIVERED) {
            try {
                messagingTemplate.convertAndSend("/topic/deliveries", saved);
            } catch (Exception e) {
                log.error("Failed to broadcast delivery update: {}", e.getMessage());
            }
        }
        return saved;
    }

    @Transactional
    public void cancelDeliveryForBill(UUID billId, String notes) {
        List<Delivery> active = deliveryRepository.findByBillId(billId);
        for (Delivery d : active) {
            if (d.getStatus() == DeliveryStatus.PENDING || d.getStatus() == DeliveryStatus.PACKED || d.getStatus() == DeliveryStatus.OUT || d.getStatus() == DeliveryStatus.COD_PENDING_PAYMENT) {
                d.setStatus(DeliveryStatus.CANCELLED);
                if (notes != null) {
                    d.setNotes(notes);
                }
                deliveryRepository.save(d);
                try {
                    messagingTemplate.convertAndSend("/topic/deliveries", d);
                } catch (Exception e) {
                    log.error("Failed to broadcast delivery cancellation: {}", e.getMessage());
                }
            }
        }
    }

    public Delivery updateStatus(UUID id, DeliveryStatus status) {
        return updateStatus(id, status, null);
    }

    public Delivery updateStatus(UUID id, DeliveryStatus status, String notes) {
        Delivery delivery = deliveryRepository
                .findById(id)
                .orElseThrow(() ->
                    new RuntimeException("Delivery not found"));

        delivery.setStatus(status);
        if (notes != null) {
            delivery.setNotes(notes);
        }

        if (status == DeliveryStatus.OUT) {
            delivery.setDispatchedAt(LocalDateTime.now());
            if (delivery.getBill() != null && delivery.getBill().getPaymentMode() == PaymentMode.COD) {
                Bill bill = delivery.getBill();
                // Only change status to COD_DELIVERED and generate OTP if the bill hasn't been paid/collected yet
                if (bill.getStatus() != BillStatus.PAID && bill.getStatus() != BillStatus.COD_COLLECTED) {
                    bill.setStatus(BillStatus.COD_DELIVERED);
                    billRepository.save(bill);
                    delivery.setStatus(DeliveryStatus.COD_PENDING_PAYMENT);

                    // Generate random 4-digit OTP
                    String otp = String.format("%04d", new java.util.Random().nextInt(10000));
                    delivery.setOtpCode(otp);
                    delivery.setOtpGeneratedAt(LocalDateTime.now());
                    delivery = deliveryRepository.save(delivery);
                    codWhatsAppService.sendOtpNotification(delivery, otp);
                } else {
                    // Already paid/collected, keep status as OUT (no OTP, normal delivery)
                    delivery.setStatus(DeliveryStatus.OUT);
                }
            }
        }
        if (status == DeliveryStatus.DELIVERED) {
            delivery.setDeliveredAt(LocalDateTime.now());
        }

        Delivery saved = deliveryRepository.save(delivery);
        try {
            messagingTemplate.convertAndSend("/topic/deliveries", saved);
        } catch (Exception e) {
            log.error("Failed to broadcast delivery update: {}", e.getMessage());
        }
        return saved;
    }

    public Delivery collectCash(UUID id, BigDecimal amount) {
        Delivery delivery = deliveryRepository
                .findById(id)
                .orElseThrow(() ->
                    new RuntimeException("Delivery not found"));
        delivery.setCashCollected(amount);
        return deliveryRepository.save(delivery);
    }

    @Transactional(rollbackFor = Exception.class)
    public Delivery completeDelivery(UUID id, CompleteDeliveryRequest req, String userPhone) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));

        User user = userRepository.findByPhone(userPhone)
                .orElseThrow(() -> new RuntimeException("User not found"));

        delivery.setCompletedBy(user);

        // Auth check: Admin, Manager or the assigned delivery boy
        if (user.getRole() == com.shop.modules.user.UserRole.DELIVERY_BOY || user.getRole() == com.shop.modules.user.UserRole.SALESMAN) {
            if (delivery.getDeliveryBoy() == null || !delivery.getDeliveryBoy().getId().equals(user.getId())) {
                throw new RuntimeException("Access denied: You are not assigned to this delivery");
            }
        }

        Bill bill = delivery.getBill();
        if (bill == null) {
            throw new RuntimeException("No bill associated with this delivery");
        }

        // If the bill is already PAID or COD_COLLECTED, we don't collect payment again.
        if (bill.getStatus() == BillStatus.PAID || bill.getStatus() == BillStatus.COD_COLLECTED) {
            delivery.setDeliveredAt(LocalDateTime.now());
            delivery.setStatus(DeliveryStatus.DELIVERED);
            delivery.setCashCollected(BigDecimal.ZERO);
            delivery.setNotes(req.getNotes());

            CODAuditLog auditLog = CODAuditLog.builder()
                    .deliveryId(delivery.getId())
                    .deliveryBoyId(delivery.getDeliveryBoy() != null ? delivery.getDeliveryBoy().getId() : user.getId())
                    .event("DELIVERY_COMPLETED_PREPAID")
                    .amount(BigDecimal.ZERO)
                    .oldMode(bill.getPaymentMode().toString())
                    .newMode(bill.getPaymentMode().toString())
                    .timestamp(LocalDateTime.now())
                    .deviceInfo("Prepaid delivery completed. Notes: " + req.getNotes())
                    .build();
            codAuditLogRepository.save(auditLog);

            Delivery savedDelivery = deliveryRepository.save(delivery);

            // WebSocket broadcast
            try {
                messagingTemplate.convertAndSend("/topic/deliveries", savedDelivery);
            } catch (Exception e) {
                log.error("Failed to broadcast delivery update: {}", e.getMessage());
            }

            return savedDelivery;
        }

        BigDecimal amountCollected = req.getAmountCollected() != null ? req.getAmountCollected() : BigDecimal.ZERO;
        String mode = req.getPaymentMode(); // CASH, UPI, UDHAR

        delivery.setDeliveredAt(LocalDateTime.now());
        delivery.setNotes(req.getNotes());

        if (req.getStatus() == DeliveryStatus.FAILED) {
            delivery.setStatus(DeliveryStatus.FAILED);
        } else {
            // Validate OTP for non-FAILED deliveries if paymentMode is COD and OTP was generated
            boolean isAdminOrManager = user.getRole() == com.shop.modules.user.UserRole.ADMIN || user.getRole() == com.shop.modules.user.UserRole.MANAGER;
            if (bill.getPaymentMode() == PaymentMode.COD && delivery.getOtpCode() != null && !isAdminOrManager) {
                if (req.getOtpCode() == null || !req.getOtpCode().trim().equals(delivery.getOtpCode())) {
                    throw new RuntimeException("Invalid OTP. Please enter the correct OTP shared with the customer.");
                }
            }

            if ("UDHAR".equalsIgnoreCase(mode)) {
                // COD -> UDHAR conversion
                bill.setPaymentMode(PaymentMode.UDHAR);
                bill.setStatus(BillStatus.CONFIRMED);
                billRepository.save(bill);

                delivery.setStatus(DeliveryStatus.COD_DEFAULTED);
                delivery.setCashCollected(BigDecimal.ZERO);

                CODAuditLog auditLog = CODAuditLog.builder()
                        .deliveryId(delivery.getId())
                        .deliveryBoyId(delivery.getDeliveryBoy() != null ? delivery.getDeliveryBoy().getId() : user.getId())
                        .event("CONVERT_TO_UDHAR")
                        .amount(bill.getGrandTotal())
                        .oldMode("COD")
                        .newMode("UDHAR")
                        .timestamp(LocalDateTime.now())
                        .deviceInfo("Notes: " + req.getNotes())
                        .build();
                codAuditLogRepository.save(auditLog);
            } else {
                // CASH or UPI collection
                delivery.setCashCollected(amountCollected);
                if (amountCollected.compareTo(BigDecimal.ZERO) == 0) {
                    throw new RuntimeException("Collected amount must be greater than zero for CASH/UPI payments");
                }

                if (amountCollected.compareTo(bill.getGrandTotal()) < 0) {
                    delivery.setStatus(DeliveryStatus.COD_PARTIAL);
                } else {
                    delivery.setStatus(DeliveryStatus.COD_COLLECTED);
                }

                // Record payment in Khata
                RecordPaymentRequest payReq = new RecordPaymentRequest();
                payReq.setCustomerId(bill.getCustomer().getId());
                payReq.setBillId(bill.getId());
                payReq.setAmount(amountCollected);
                payReq.setPaymentMode(mode);
                payReq.setPaymentSource("COD_DELIVERY");
                payReq.setNotes("COD delivery payment. " + (req.getNotes() != null ? req.getNotes() : ""));
                payReq.setConfirmedByUser(true);
                payReq.setAdjustmentType(AdjustmentType.NORMAL);

                khataService.recordPayment(payReq, userPhone);

                CODAuditLog auditLog = CODAuditLog.builder()
                        .deliveryId(delivery.getId())
                        .deliveryBoyId(delivery.getDeliveryBoy() != null ? delivery.getDeliveryBoy().getId() : user.getId())
                        .event(amountCollected.compareTo(bill.getGrandTotal()) < 0 ? "PARTIAL_PAYMENT" : "COLLECTED_PAYMENT")
                        .amount(amountCollected)
                        .oldMode("COD")
                        .newMode(mode)
                        .timestamp(LocalDateTime.now())
                        .deviceInfo("Notes: " + req.getNotes())
                        .build();
                codAuditLogRepository.save(auditLog);
            }
        }

        Delivery savedDelivery = deliveryRepository.save(delivery);

        // WebSocket broadcast
        try {
            messagingTemplate.convertAndSend("/topic/deliveries", savedDelivery);
        } catch (Exception e) {
            log.error("Failed to broadcast delivery update: {}", e.getMessage());
        }

        // Async alerts and fraud checks
        try {
            codFraudDetectionService.checkSuspiciousPattern(savedDelivery);
        } catch (Exception e) {
            log.error("Failed to trigger async tasks: {}", e.getMessage());
        }

        return savedDelivery;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Delivery> assignDeliveriesBulk(BulkAssignRequest req, User completedBy) {
        User boy = req.getDeliveryBoyId() != null
                ? userRepository.findById(req.getDeliveryBoyId()).orElse(null)
                : null;

        if (req.getBillIds() == null || req.getBillIds().isEmpty()) {
            throw new RuntimeException("No bills selected for assignment");
        }

        return req.getBillIds().stream().map(billId -> {
            Bill bill = billRepository.findById(billId)
                    .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));

            List<DeliveryStatus> blockingStatuses = List.of(
                DeliveryStatus.PENDING,
                DeliveryStatus.PACKED,
                DeliveryStatus.OUT,
                DeliveryStatus.COD_PENDING_PAYMENT,
                DeliveryStatus.COD_PARTIAL,
                DeliveryStatus.DELIVERED,
                DeliveryStatus.COD_COLLECTED,
                DeliveryStatus.COD_DEFAULTED
            );
            if (deliveryRepository.existsByBillIdAndStatusIn(billId, blockingStatuses)) {
                throw new RuntimeException("Bill " + bill.getBillNumber() + " is already assigned to a active or completed delivery");
            }

            Delivery delivery = Delivery.builder()
                    .bill(bill)
                    .deliveryBoy(boy)
                    .type(req.getType())
                    .scheduledDate(req.getScheduledDate())
                    .status(req.getType() == DeliveryType.SELF_PICKUP ? DeliveryStatus.DELIVERED : DeliveryStatus.PENDING)
                    .deliveredAt(req.getType() == DeliveryType.SELF_PICKUP ? LocalDateTime.now() : null)
                    .completedBy(req.getType() == DeliveryType.SELF_PICKUP ? completedBy : null)
                    .cashCollected(BigDecimal.ZERO)
                    .build();
            Delivery saved = deliveryRepository.save(delivery);
            if (saved.getStatus() == DeliveryStatus.DELIVERED) {
                try {
                    messagingTemplate.convertAndSend("/topic/deliveries", saved);
                } catch (Exception e) {
                    log.error("Failed to broadcast delivery update: {}", e.getMessage());
                }
            }
            return saved;
        }).collect(Collectors.toList());
    }

    @Data
    public static class AssignDeliveryRequest {
        private UUID billId;
        private UUID deliveryBoyId;
        private DeliveryType type;
        private LocalDate scheduledDate;
    }

    @Data
    public static class BulkAssignRequest {
        private List<UUID> billIds;
        private UUID deliveryBoyId;
        private DeliveryType type;
        private LocalDate scheduledDate;
    }
}