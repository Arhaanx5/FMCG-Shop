package com.shop.modules.khata;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.khata.dto.PaymentResponse;
import com.shop.modules.khata.dto.RecordPaymentRequest;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.common.ledger.CustomerLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for allocating, validating, and recording customer payment events.
 */
@Service
@RequiredArgsConstructor
public class PaymentRecordingService {

    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final BillRepository billRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CustomerLedgerService customerLedgerService;
    private final KhataMapper khataMapper;

    /**
     * Main transactional method to record a customer payment.
     */
    @Transactional
    public PaymentResponse recordPayment(
            RecordPaymentRequest req,
            String collectedByPhone) {

        Customer customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        User collector = userRepository.findByPhone(collectedByPhone)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // --- STANDALONE WAIVE-OFF DETECTION ---
        if (!"WAIVE_OFF".equalsIgnoreCase(req.getPaymentMode()) && (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0)) {
            BigDecimal waivedAmount = req.getWaivedAmount();
            if (waivedAmount == null || waivedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Payment amount ya waive-off amount positive hona chahiye.");
            }
            if (waivedAmount.compareTo(new BigDecimal("200")) > 0) {
                throw new RuntimeException("Waive-off ₹200 se zyada nahi ho sakta. Entered: ₹" + String.format("%.2f", waivedAmount));
            }

            PaymentResponse response;
            if (req.getBillId() != null) {
                Bill targetBill = billRepository.findById(req.getBillId())
                        .orElseThrow(() -> new RuntimeException("Bill not found"));
                if (targetBill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new RuntimeException("Bill already paid.");
                }
                
                BigDecimal waiveToApply = waivedAmount.min(targetBill.getPendingAmount());
                khataMapper.applyPaymentToBill(targetBill, waiveToApply);
                
                Payment waiveSaved = paymentRepository.save(Payment.builder()
                        .customer(customer)
                        .bill(targetBill)
                        .amount(waiveToApply)
                        .appliedAmount(waiveToApply)
                        .excessAmount(BigDecimal.ZERO)
                        .adjustmentType(AdjustmentType.NORMAL)
                        .paymentMode("WAIVE_OFF")
                        .paymentSource(req.getPaymentSource())
                        .notes(req.getNotes() != null && !req.getNotes().trim().isEmpty() 
                                ? "Waive-off: " + req.getNotes() 
                                : "Waive-off Adjustment")
                        .collectedBy(collector)
                        .paidAt(LocalDateTime.now())
                        .build());
                        
                response = khataMapper.toResponse(waiveSaved);
            } else {
                RecordPaymentRequest waiveReq = new RecordPaymentRequest();
                waiveReq.setCustomerId(req.getCustomerId());
                waiveReq.setAmount(waivedAmount);
                waiveReq.setPaymentMode("WAIVE_OFF");
                waiveReq.setNotes(req.getNotes() != null && !req.getNotes().trim().isEmpty() 
                        ? "Waive-off: " + req.getNotes() 
                        : "Waive-off Adjustment");
                waiveReq.setPaymentSource(req.getPaymentSource());
                
                response = recordGeneralPayment(waiveReq, customer, collector);
            }

            customerLedgerService.recalculateCustomerPending(customer);

            // Broadcast live payment activity over WebSocket
            try {
                messagingTemplate.convertAndSend("/topic/payments", response);
            } catch (Exception e) {
                System.err.println("Failed to broadcast live payment: " + e.getMessage());
            }

            return response;
        }

        if ("WAIVE_OFF".equalsIgnoreCase(req.getPaymentMode())) {
            if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Waive-off amount zero ya negative nahi ho sakta.");
            }
            if (req.getAmount().compareTo(new BigDecimal("200")) > 0) {
                throw new RuntimeException("Waive-off ₹200 se zyada nahi ho sakta. Entered: ₹" + String.format("%.2f", req.getAmount()));
            }
        }

        // Prevent duplicate payment recording (within last 5 seconds)
        LocalDateTime fiveSecondsAgo = LocalDateTime.now().minusSeconds(5);
        List<Payment> recentPayments = paymentRepository.findByCustomerIdOrderByPaidAtDesc(customer.getId());
        for (Payment rp : recentPayments) {
            if (rp.getPaidAt() != null && rp.getPaidAt().isAfter(fiveSecondsAgo)) {
                boolean sameBill = (req.getBillId() == null && rp.getBill() == null) 
                        || (req.getBillId() != null && rp.getBill() != null && req.getBillId().equals(rp.getBill().getId()));
                if (sameBill && rp.getAmount().compareTo(req.getAmount()) == 0 
                        && rp.getPaymentMode().equalsIgnoreCase(req.getPaymentMode())
                        && rp.getCollectedBy() != null && rp.getCollectedBy().getId().equals(collector.getId())) {
                    throw new RuntimeException("Duplicate payment detected. Please wait 5 seconds before retrying.");
                }
            }
        }

        Bill bill = null;
        if (req.getBillId() != null) {
            bill = billRepository.findById(req.getBillId()).orElse(null);
        }

        PaymentResponse response;

        // ── Case A: No specific bill → legacy FIFO auto-allocation ──
        if (bill == null) {
            response = recordGeneralPayment(req, customer, collector);
        } else {
            BigDecimal amount = req.getAmount();
            BigDecimal pending = bill.getPendingAmount();

            // ── Case B: Normal payment (amount ≤ pending) ──
            if (amount.compareTo(pending) <= 0) {
                response = recordNormalPayment(req, customer, bill, collector, amount);
            } else {
                // ── Case C: Overpayment ──
                // Require user to have confirmed a resolution
                if (!req.isConfirmedByUser() || req.getAdjustmentType() == null) {
                    throw new RuntimeException(
                            "Overpayment detected. Please choose an adjustment option and confirm.");
                }

                response = switch (req.getAdjustmentType()) {
                    case MANUAL_ADJUST -> recordManualAdjust(req, customer, bill, collector, amount, pending);
                    case AUTO_ADJUST   -> recordAutoAdjust(req, customer, bill, collector, amount, pending);
                    default -> throw new RuntimeException("Invalid adjustment type for overpayment.");
                };
            }
        }

        // --- WAIVE-OFF RECORDING FOR COMBINED PAYMENT (OPTION 1) ---
        BigDecimal waivedAmount = req.getWaivedAmount();
        if (waivedAmount != null && waivedAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (waivedAmount.compareTo(new BigDecimal("200")) > 0) {
                throw new RuntimeException("Waive-off ₹200 se zyada nahi ho sakta. Entered: ₹" + String.format("%.2f", waivedAmount));
            }

            if (req.getBillId() != null) {
                Bill updatedBill = billRepository.findById(req.getBillId()).orElse(null);
                if (updatedBill != null && updatedBill.getPendingAmount().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal waiveToApply = waivedAmount.min(updatedBill.getPendingAmount());
                    khataMapper.applyPaymentToBill(updatedBill, waiveToApply);
                    
                    Payment waiveSaved = paymentRepository.save(Payment.builder()
                            .customer(customer)
                            .bill(updatedBill)
                            .amount(waiveToApply)
                            .appliedAmount(waiveToApply)
                            .excessAmount(BigDecimal.ZERO)
                            .adjustmentType(AdjustmentType.NORMAL)
                            .paymentMode("WAIVE_OFF")
                            .paymentSource(req.getPaymentSource())
                            .notes(req.getNotes() != null && !req.getNotes().trim().isEmpty() 
                                    ? "Waive-off Auto-recorded · " + req.getNotes() 
                                    : "Waive-off Auto-recorded")
                            .collectedBy(collector)
                            .paidAt(LocalDateTime.now())
                            .build());

                    try {
                        messagingTemplate.convertAndSend("/topic/payments", khataMapper.toResponse(waiveSaved));
                    } catch (Exception e) {
                        System.err.println("Failed to broadcast waive-off payment: " + e.getMessage());
                    }
                }
            } else {
                RecordPaymentRequest waiveReq = new RecordPaymentRequest();
                waiveReq.setCustomerId(req.getCustomerId());
                waiveReq.setAmount(waivedAmount);
                waiveReq.setPaymentMode("WAIVE_OFF");
                waiveReq.setNotes(req.getNotes() != null && !req.getNotes().trim().isEmpty() 
                        ? "Waive-off Auto-recorded · " + req.getNotes() 
                        : "Waive-off Auto-recorded");
                waiveReq.setPaymentSource(req.getPaymentSource());
                
                PaymentResponse waiveResponse = recordGeneralPayment(waiveReq, customer, collector);
                try {
                    messagingTemplate.convertAndSend("/topic/payments", waiveResponse);
                } catch (Exception e) {
                    System.err.println("Failed to broadcast waive-off payment: " + e.getMessage());
                }
            }
            customerLedgerService.recalculateCustomerPending(customer);
        }

        // Broadcast live payment activity over WebSocket
        try {
            messagingTemplate.convertAndSend("/topic/payments", response);
        } catch (Exception e) {
            System.err.println("Failed to broadcast live payment: " + e.getMessage());
        }

        return response;
    }

    /**
     * Case B: Normal payment (amount <= pending)
     */
    private PaymentResponse recordNormalPayment(
            RecordPaymentRequest req, Customer customer,
            Bill bill, User collector, BigDecimal amount) {

        khataMapper.applyPaymentToBill(bill, amount);
        customerLedgerService.recalculateCustomerPending(customer);

        Payment saved = paymentRepository.save(Payment.builder()
                .customer(customer)
                .bill(bill)
                .amount(amount)
                .appliedAmount(amount)
                .excessAmount(BigDecimal.ZERO)
                .adjustmentType(AdjustmentType.NORMAL)
                .paymentMode(req.getPaymentMode())
                .paymentSource(req.getPaymentSource())
                .notes(req.getNotes())
                .collectedBy(collector)
                .paidAt(LocalDateTime.now())
                .build());

        return khataMapper.toResponse(saved);
    }

    /**
     * Case C1: Manual adjust overpayment
     */
    private PaymentResponse recordManualAdjust(
            RecordPaymentRequest req, Customer customer,
            Bill sourceBill, User collector,
            BigDecimal amount, BigDecimal sourcePending) {

        if (req.getTargetBillId() == null) {
            throw new RuntimeException("Target bill must be selected for manual adjustment.");
        }

        Bill targetBill = billRepository.findById(req.getTargetBillId())
                .orElseThrow(() -> new RuntimeException("Target bill not found"));

        if (!targetBill.getCustomer().getId().equals(customer.getId())) {
            throw new RuntimeException("Target bill does not belong to this customer.");
        }

        BigDecimal excess = amount.subtract(sourcePending);
        BigDecimal applyToTarget = excess.min(targetBill.getPendingAmount());

        // Apply to source bill (fully pays it)
        khataMapper.applyPaymentToBill(sourceBill, sourcePending);
        // Apply excess to target bill
        khataMapper.applyPaymentToBill(targetBill, applyToTarget);

        // Reduce customer pending by full payment amount
        customerLedgerService.recalculateCustomerPending(customer);

        LocalDateTime now = LocalDateTime.now();
        String groupNote = String.format("Adjustment: ₹%.2f on %s + ₹%.2f on %s",
                sourcePending, sourceBill.getBillNumber(),
                applyToTarget, targetBill.getBillNumber());

        // Save separate payment record for source bill
        Payment sourceSaved = paymentRepository.save(Payment.builder()
                .customer(customer)
                .bill(sourceBill)
                .amount(sourcePending)
                .appliedAmount(sourcePending)
                .excessAmount(BigDecimal.ZERO)
                .adjustmentType(AdjustmentType.MANUAL_ADJUST)
                .adjustmentNote(groupNote)
                .paymentMode(req.getPaymentMode())
                .paymentSource(req.getPaymentSource())
                .notes(req.getNotes())
                .collectedBy(collector)
                .paidAt(now)
                .build());

        // Save separate payment record for target bill (the excess portion)
        paymentRepository.save(Payment.builder()
                .customer(customer)
                .bill(targetBill)
                .amount(applyToTarget)
                .appliedAmount(applyToTarget)
                .excessAmount(BigDecimal.ZERO)
                .adjustmentType(AdjustmentType.MANUAL_ADJUST)
                .adjustmentNote(groupNote)
                .paymentMode(req.getPaymentMode())
                .paymentSource(req.getPaymentSource())
                .notes(req.getNotes())
                .collectedBy(collector)
                .paidAt(now)
                .build());

        return khataMapper.toResponse(sourceSaved);
    }

    /**
     * Case C2: Auto FIFO adjust overpayment
     */
    private PaymentResponse recordAutoAdjust(
            RecordPaymentRequest req, Customer customer,
            Bill sourceBill, User collector,
            BigDecimal amount, BigDecimal sourcePending) {

        BigDecimal excess = amount.subtract(sourcePending);

        // Validate: check there are enough pending bills to absorb excess
        List<Bill> otherBills = billRepository
                .findPendingBillsForCustomerExcluding(customer.getId(), sourceBill.getId());

        BigDecimal totalOtherPending = otherBills.stream()
                .map(Bill::getPendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalOtherPending.compareTo(excess) < 0) {
            throw new RuntimeException(String.format(
                    "Excess amount ₹%.2f exceeds total other pending bills ₹%.2f. " +
                    "No advance option available — please reduce payment amount.",
                    excess, totalOtherPending));
        }

        // Apply to source bill
        khataMapper.applyPaymentToBill(sourceBill, sourcePending);

        // FIFO distribute excess — collect applied amounts per bill
        BigDecimal remaining = excess;
        List<String> adjustedBillNumbers = new ArrayList<>();
        List<Map.Entry<Bill, BigDecimal>> allocations = new ArrayList<>();

        for (Bill ob : otherBills) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal apply = remaining.min(ob.getPendingAmount());
            khataMapper.applyPaymentToBill(ob, apply);
            adjustedBillNumbers.add(String.format("%s(₹%.2f)", ob.getBillNumber(), apply));
            allocations.add(Map.entry(ob, apply));
            remaining = remaining.subtract(apply);
        }

        // Reduce customer pending by full amount
        customerLedgerService.recalculateCustomerPending(customer);

        String groupNote = String.format(
                "Auto-adjust: ₹%.2f on %s; excess ₹%.2f → %s",
                sourcePending, sourceBill.getBillNumber(),
                excess, String.join(", ", adjustedBillNumbers));

        LocalDateTime now = LocalDateTime.now();

        // Save payment record for source bill
        Payment sourceSaved = paymentRepository.save(Payment.builder()
                .customer(customer)
                .bill(sourceBill)
                .amount(sourcePending)
                .appliedAmount(sourcePending)
                .excessAmount(BigDecimal.ZERO)
                .adjustmentType(AdjustmentType.AUTO_ADJUST)
                .adjustmentNote(groupNote)
                .paymentMode(req.getPaymentMode())
                .paymentSource(req.getPaymentSource())
                .notes(req.getNotes())
                .collectedBy(collector)
                .paidAt(now)
                .build());

        // Save separate payment record for EACH adjusted bill
        for (Map.Entry<Bill, BigDecimal> entry : allocations) {
            paymentRepository.save(Payment.builder()
                    .customer(customer)
                    .bill(entry.getKey())
                    .amount(entry.getValue())
                    .appliedAmount(entry.getValue())
                    .excessAmount(BigDecimal.ZERO)
                    .adjustmentType(AdjustmentType.AUTO_ADJUST)
                    .adjustmentNote(groupNote)
                    .paymentMode(req.getPaymentMode())
                    .paymentSource(req.getPaymentSource())
                    .notes(req.getNotes())
                    .collectedBy(collector)
                    .paidAt(now)
                    .build());
        }

        return khataMapper.toResponse(sourceSaved);
    }

    /**
     * Case A: General payment (no bill linked) — legacy FIFO logic preserved
     */
    private PaymentResponse recordGeneralPayment(
            RecordPaymentRequest req, Customer customer, User collector) {

        List<Bill> pendingBills = billRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .filter(b -> (b.getStatus() == BillStatus.CONFIRMED || b.getStatus() == BillStatus.PARTIAL)
                        && b.getPendingAmount().compareTo(BigDecimal.ZERO) > 0)
                .sorted(java.util.Comparator.comparing(Bill::getCreatedAt))
                .collect(Collectors.toList());

        BigDecimal remaining = req.getAmount();
        Payment lastSaved = null;

        if (pendingBills.isEmpty()) {
            lastSaved = paymentRepository.save(Payment.builder()
                    .customer(customer).bill(null)
                    .amount(req.getAmount())
                    .appliedAmount(req.getAmount())
                    .excessAmount(BigDecimal.ZERO)
                    .adjustmentType(AdjustmentType.NORMAL)
                    .paymentMode(req.getPaymentMode())
                    .paymentSource(req.getPaymentSource())
                    .notes(req.getNotes()).collectedBy(collector)
                    .paidAt(LocalDateTime.now()).build());
        } else {
            for (Bill pb : pendingBills) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal alloc = remaining.min(pb.getPendingAmount());
                khataMapper.applyPaymentToBill(pb, alloc);
                lastSaved = paymentRepository.save(Payment.builder()
                        .customer(customer).bill(pb)
                        .amount(alloc).appliedAmount(alloc)
                        .excessAmount(BigDecimal.ZERO)
                        .adjustmentType(AdjustmentType.NORMAL)
                        .paymentMode(req.getPaymentMode())
                        .paymentSource(req.getPaymentSource())
                        .notes(req.getNotes()).collectedBy(collector)
                        .paidAt(LocalDateTime.now()).build());
                remaining = remaining.subtract(alloc);
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                // Remaining after all bills cleared — general payment record
                lastSaved = paymentRepository.save(Payment.builder()
                        .customer(customer).bill(null)
                        .amount(remaining).appliedAmount(remaining)
                        .excessAmount(BigDecimal.ZERO)
                        .adjustmentType(AdjustmentType.NORMAL)
                        .paymentMode(req.getPaymentMode())
                        .paymentSource(req.getPaymentSource())
                        .notes(req.getNotes()).collectedBy(collector)
                        .paidAt(LocalDateTime.now()).build());
            }
        }
        customerLedgerService.recalculateCustomerPending(customer);
        return khataMapper.toResponse(lastSaved);
    }
}
