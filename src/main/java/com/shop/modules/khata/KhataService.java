package com.shop.modules.khata;

import com.shop.modules.khata.dto.OverpaymentPreviewResponse;
import com.shop.modules.khata.dto.PaymentResponse;
import com.shop.modules.khata.dto.RecordPaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service orchestrator for Khata (Ledger) payments.
 * Delegates mutating actions to specialized domain services.
 */
@Service
@RequiredArgsConstructor
public class KhataService {

    private final PaymentRepository paymentRepository;
    private final KhataMapper khataMapper;
    private final PaymentRecordingService paymentRecordingService;
    private final PaymentPreviewService paymentPreviewService;
    private final PaymentReversalService paymentReversalService;

    // ─────────────────────────────────────────────────────────────
    // Read (Query) Operations
    // ─────────────────────────────────────────────────────────────

    public List<PaymentResponse> getCustomerPayments(UUID customerId) {
        return khataMapper.toResponses(paymentRepository.findByCustomerIdOrderByPaidAtDesc(customerId));
    }

    public List<PaymentResponse> getTodayCollections() {
        LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return khataMapper.toResponses(paymentRepository.findBetween(start, end));
    }

    public List<PaymentResponse> getAllPayments() {
        return khataMapper.toResponses(paymentRepository.findAllByOrderByPaidAtDesc());
    }

    public List<PaymentResponse> getCollectedByPayments(UUID salesmanId) {
        return khataMapper.toResponses(paymentRepository.findByCollectedByIdOrderByPaidAtDesc(salesmanId));
    }

    public List<PaymentResponse> getTodayCollectedByPayments(UUID salesmanId) {
        LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return khataMapper.toResponses(paymentRepository.findByCollectedByIdAndBetween(salesmanId, start, end));
    }

    // ─────────────────────────────────────────────────────────────
    // Write (Command) Operations
    // ─────────────────────────────────────────────────────────────

    /**
     * Preview overpayment distribution prior to recording.
     */
    public OverpaymentPreviewResponse previewOverpayment(
            UUID customerId, UUID billId, BigDecimal amount) {
        return paymentPreviewService.previewOverpayment(customerId, billId, amount);
    }

    /**
     * Record a new customer payment or waive-off adjustment.
     */
    public PaymentResponse recordPayment(
            RecordPaymentRequest req,
            String collectedByPhone) {
        return paymentRecordingService.recordPayment(req, collectedByPhone);
    }

    /**
     * Reverses a payment and all its allocations.
     */
    public void deletePayment(UUID paymentId) {
        paymentReversalService.deletePayment(paymentId);
    }

    /**
     * Updates payment information.
     */
    public PaymentResponse updatePayment(UUID id, String paymentMode, String notes) {
        return paymentReversalService.updatePayment(id, paymentMode, notes);
    }
}