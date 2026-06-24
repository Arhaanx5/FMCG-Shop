package com.shop.modules.khata;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.khata.dto.OverpaymentPreviewResponse;
import com.shop.modules.khata.dto.PaymentResponse;
import com.shop.modules.khata.dto.RecordPaymentRequest;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KhataService {

    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final BillRepository billRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private PaymentResponse toResponse(Payment payment) {
        String adjustedBillNumber = null;
        if (payment.getAdjustedBillId() != null) {
            adjustedBillNumber = billRepository.findById(payment.getAdjustedBillId())
                    .map(Bill::getBillNumber).orElse(null);
        }
        return PaymentResponse.builder()
                .id(payment.getId())
                .customerId(payment.getCustomer().getId())
                .customerName(payment.getCustomer().getName())
                .customerShopName(payment.getCustomer().getShopName())
                .billId(payment.getBill() != null ? payment.getBill().getId() : null)
                .billNumber(payment.getBill() != null ? payment.getBill().getBillNumber() : null)
                .amount(payment.getAmount())
                .appliedAmount(payment.getAppliedAmount())
                .excessAmount(payment.getExcessAmount())
                .adjustmentType(payment.getAdjustmentType() != null
                        ? payment.getAdjustmentType().name() : null)
                .adjustmentNote(payment.getAdjustmentNote())
                .adjustedBillId(payment.getAdjustedBillId())
                .adjustedBillNumber(adjustedBillNumber)
                .paymentMode(payment.getPaymentMode())
                .paymentSource(payment.getPaymentSource())
                .notes(payment.getNotes())
                .paidAt(payment.getPaidAt())
                .collectedBy(payment.getCollectedBy() != null
                        ? payment.getCollectedBy().getName() : null)
                .customerPendingBalance(payment.getCustomer().getTotalPending())
                .billGrandTotal(payment.getBill() != null ? payment.getBill().getGrandTotal() : null)
                .billPendingAmount(payment.getBill() != null ? payment.getBill().getPendingAmount() : null)
                .build();
    }

    /**
     * Update a bill's paidAmount and pendingAmount, then derive its status:
     *   paidAmount == 0             → CONFIRMED  (all pending)
     *   0 < paidAmount < grandTotal → PARTIAL
     *   paidAmount >= grandTotal    → PAID
     */
    private void applyPaymentToBill(Bill bill, BigDecimal amountToApply) {
        bill.setPaidAmount(bill.getPaidAmount().add(amountToApply));
        BigDecimal newPending = bill.getGrandTotal().subtract(bill.getPaidAmount());
        bill.setPendingAmount(newPending.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO : newPending);
        bill.setStatus(deriveBillStatus(bill));
        billRepository.save(bill);
    }

    private BillStatus deriveBillStatus(Bill bill) {
        if (bill.getPaymentMode() == com.shop.modules.billing.PaymentMode.COD) {
            if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) == 0) {
                return BillStatus.COD_PENDING;
            } else if (bill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return BillStatus.COD_COLLECTED;
            } else {
                return BillStatus.PARTIAL;
            }
        } else {
            if (bill.getPaidAmount().compareTo(BigDecimal.ZERO) == 0) {
                return BillStatus.CONFIRMED;
            } else if (bill.getPendingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return BillStatus.PAID;
            } else {
                return BillStatus.PARTIAL;
            }
        }
    }

    private void recalculateCustomerPending(Customer customer) {
        BigDecimal totalGeneralPayments = paymentRepository.findByCustomerIdOrderByPaidAtDesc(customer.getId())
                .stream()
                .filter(p -> p.getBill() == null)
                .map(p -> p.getAppliedAmount() != null ? p.getAppliedAmount() : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unpaidOpeningBalance = customer.getOpeningBalance() != null
                ? customer.getOpeningBalance().subtract(totalGeneralPayments)
                : BigDecimal.ZERO;
        if (unpaidOpeningBalance.compareTo(BigDecimal.ZERO) < 0) {
            unpaidOpeningBalance = BigDecimal.ZERO;
        }

        BigDecimal totalBillPending = billRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .filter(b -> b.getStatus() != BillStatus.CANCELLED)
                .map(Bill::getPendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        customer.setTotalPending(unpaidOpeningBalance.add(totalBillPending));
        customerRepository.save(customer);
    }

    // ─────────────────────────────────────────────────────────────
    // Read endpoints
    // ─────────────────────────────────────────────────────────────

    public List<PaymentResponse> getCustomerPayments(UUID customerId) {
        return paymentRepository
                .findByCustomerIdOrderByPaidAtDesc(customerId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<PaymentResponse> getTodayCollections() {
        LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return paymentRepository.findBetween(start, end)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAllByOrderByPaidAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<PaymentResponse> getCollectedByPayments(UUID salesmanId) {
        return paymentRepository.findByCollectedByIdOrderByPaidAtDesc(salesmanId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<PaymentResponse> getTodayCollectedByPayments(UUID salesmanId) {
        LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return paymentRepository.findByCollectedByIdAndBetween(salesmanId, start, end)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // Preview overpayment — called BEFORE saving
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns an OverpaymentPreviewResponse when amount > bill.pendingAmount.
     * Returns null if amount is within the pending amount (normal payment).
     */
    public OverpaymentPreviewResponse previewOverpayment(
            UUID customerId, UUID billId, BigDecimal amount) {

        if (billId == null) return null; // No specific bill → existing FIFO handles it

        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        BigDecimal pending = bill.getPendingAmount();
        if (amount.compareTo(pending) <= 0) return null; // Normal, no overpayment

        BigDecimal excess = amount.subtract(pending);

        // Get other pending bills for FIFO preview
        List<Bill> otherBills = billRepository
                .findPendingBillsForCustomerExcluding(customerId, billId);

        List<OverpaymentPreviewResponse.BillSummary> summaries = otherBills.stream()
                .map(b -> OverpaymentPreviewResponse.BillSummary.builder()
                        .billId(b.getId())
                        .billNumber(b.getBillNumber())
                        .pendingAmount(b.getPendingAmount())
                        .grandTotal(b.getGrandTotal())
                        .createdAt(b.getCreatedAt() != null ? b.getCreatedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());

        // Compute FIFO auto-distribution preview
        List<OverpaymentPreviewResponse.AutoDistributionEntry> autoEntries = new ArrayList<>();
        BigDecimal remaining = excess;
        for (Bill ob : otherBills) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal apply = remaining.min(ob.getPendingAmount());
            autoEntries.add(OverpaymentPreviewResponse.AutoDistributionEntry.builder()
                    .billId(ob.getId())
                    .billNumber(ob.getBillNumber())
                    .pendingBefore(ob.getPendingAmount())
                    .amountApplied(apply)
                    .pendingAfter(ob.getPendingAmount().subtract(apply))
                    .willBeFullyPaid(ob.getPendingAmount().subtract(apply)
                            .compareTo(BigDecimal.ZERO) <= 0)
                    .build());
            remaining = remaining.subtract(apply);
        }

        return OverpaymentPreviewResponse.builder()
                .sourceBillId(bill.getId())
                .sourceBillNumber(bill.getBillNumber())
                .sourceBillPending(pending)
                .paymentAmount(amount)
                .excessAmount(excess)
                .otherPendingBills(summaries)
                .autoDistribution(autoEntries)
                .remainingAfterAuto(remaining) // > 0 means will block
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // Record Payment — main transactional method
    // ─────────────────────────────────────────────────────────────

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
                applyPaymentToBill(targetBill, waiveToApply);
                
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
                        
                response = toResponse(waiveSaved);
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

            recalculateCustomerPending(customer);

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
                    applyPaymentToBill(updatedBill, waiveToApply);
                    
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
                        messagingTemplate.convertAndSend("/topic/payments", toResponse(waiveSaved));
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
            recalculateCustomerPending(customer);
        }

        // Broadcast live payment activity over WebSocket
        try {
            messagingTemplate.convertAndSend("/topic/payments", response);
        } catch (Exception e) {
            System.err.println("Failed to broadcast live payment: " + e.getMessage());
        }

        return response;
    }

    // ── B: Normal payment ──
    private PaymentResponse recordNormalPayment(
            RecordPaymentRequest req, Customer customer,
            Bill bill, User collector, BigDecimal amount) {

        applyPaymentToBill(bill, amount);
        recalculateCustomerPending(customer);

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

        return toResponse(saved);
    }

    // ── C1: Manual adjust ──
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
        applyPaymentToBill(sourceBill, sourcePending);
        // Apply excess to target bill
        applyPaymentToBill(targetBill, applyToTarget);

        // Reduce customer pending by full payment amount
        recalculateCustomerPending(customer);

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

        return toResponse(sourceSaved);
    }

    // ── C2: Auto FIFO adjust ──
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
        applyPaymentToBill(sourceBill, sourcePending);

        // FIFO distribute excess — collect applied amounts per bill
        BigDecimal remaining = excess;
        List<String> adjustedBillNumbers = new ArrayList<>();
        List<Map.Entry<Bill, BigDecimal>> allocations = new ArrayList<>();

        for (Bill ob : otherBills) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal apply = remaining.min(ob.getPendingAmount());
            applyPaymentToBill(ob, apply);
            adjustedBillNumbers.add(String.format("%s(₹%.2f)", ob.getBillNumber(), apply));
            allocations.add(Map.entry(ob, apply));
            remaining = remaining.subtract(apply);
        }

        // Reduce customer pending by full amount
        recalculateCustomerPending(customer);

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

        return toResponse(sourceSaved);
    }

    // ── A: General payment (no bill linked) — existing FIFO logic preserved ──
    @Transactional
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
                applyPaymentToBill(pb, alloc);
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
        recalculateCustomerPending(customer);
        return toResponse(lastSaved);
    }

    // ─────────────────────────────────────────────────────────────
    // Delete Payment — reverses everything
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void deletePayment(UUID paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));

        Customer customer = payment.getCustomer();
        AdjustmentType type = payment.getAdjustmentType() != null
                ? payment.getAdjustmentType() : AdjustmentType.NORMAL;

        // Restore customer pending

        // Reverse source bill
        Bill bill = payment.getBill();
        if (bill != null) {
            BigDecimal appliedToSource = payment.getAppliedAmount() != null
                    ? payment.getAppliedAmount() : payment.getAmount();
            BigDecimal newPaid = bill.getPaidAmount().subtract(appliedToSource);
            bill.setPaidAmount(newPaid.compareTo(BigDecimal.ZERO) < 0
                    ? BigDecimal.ZERO : newPaid);
            bill.setPendingAmount(bill.getGrandTotal().subtract(bill.getPaidAmount()));
            bill.setStatus(deriveBillStatus(bill));
            billRepository.save(bill);
        }

        // Reverse adjusted bill (MANUAL or AUTO)
        if ((type == AdjustmentType.MANUAL_ADJUST || type == AdjustmentType.AUTO_ADJUST)
                && payment.getAdjustedBillId() != null) {
            billRepository.findById(payment.getAdjustedBillId()).ifPresent(adjBill -> {
                BigDecimal excess = payment.getExcessAmount() != null
                        ? payment.getExcessAmount() : BigDecimal.ZERO;
                BigDecimal newPaid = adjBill.getPaidAmount().subtract(excess);
                adjBill.setPaidAmount(newPaid.compareTo(BigDecimal.ZERO) < 0
                        ? BigDecimal.ZERO : newPaid);
                adjBill.setPendingAmount(adjBill.getGrandTotal().subtract(adjBill.getPaidAmount()));
                adjBill.setStatus(deriveBillStatus(adjBill));
                billRepository.save(adjBill);
            });
        }

        paymentRepository.delete(payment);
        recalculateCustomerPending(customer);
    }

    // ─────────────────────────────────────────────────────────────
    // Update payment details
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public PaymentResponse updatePayment(UUID id, String paymentMode, String notes) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));
        if (paymentMode != null) {
            if ("WAIVE_OFF".equalsIgnoreCase(paymentMode)) {
                if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new RuntimeException("Waive-off amount zero ya negative nahi ho sakta.");
                }
                if (payment.getAmount().compareTo(new BigDecimal("200")) > 0) {
                    throw new RuntimeException("Waive-off ₹200 se zyada nahi ho sakta. Entered: ₹" + String.format("%.2f", payment.getAmount()));
                }
            }
            payment.setPaymentMode(paymentMode);
        }
        if (notes != null) payment.setNotes(notes);
        return toResponse(paymentRepository.save(payment));
    }
}