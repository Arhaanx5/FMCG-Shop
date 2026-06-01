package com.shop.modules.khata;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.khata.dto.PaymentResponse;
import com.shop.modules.khata.dto.RecordPaymentRequest;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.persistence.EntityNotFoundException;

@Service
@RequiredArgsConstructor
public class KhataService {

    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final BillRepository billRepository;
    private final UserRepository userRepository;

    // Convert Payment entity to PaymentResponse DTO
    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .customerId(payment.getCustomer().getId())
                .customerName(payment.getCustomer().getName())
                .customerShopName(
                        payment.getCustomer().getShopName())
                .billId(payment.getBill() != null
                        ? payment.getBill().getId() : null)
                .billNumber(payment.getBill() != null
                        ? payment.getBill().getBillNumber() : null)
                .amount(payment.getAmount())
                .paymentMode(payment.getPaymentMode())
                .notes(payment.getNotes())
                .paidAt(payment.getPaidAt())
                .collectedBy(payment.getCollectedBy() != null
                        ? payment.getCollectedBy().getName() : null)
                .customerPendingBalance(payment.getCustomer().getTotalPending())
                .build();
    }

    public List<PaymentResponse> getCustomerPayments(
            UUID customerId) {
        return paymentRepository
                .findByCustomerIdOrderByPaidAtDesc(customerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<PaymentResponse> getTodayCollections() {
        LocalDateTime start =
                LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return paymentRepository.findBetween(start, end)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<PaymentResponse> getCollectedByPayments(UUID salesmanId) {
        return paymentRepository.findByCollectedByIdOrderByPaidAtDesc(salesmanId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<PaymentResponse> getTodayCollectedByPayments(UUID salesmanId) {
        LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return paymentRepository.findByCollectedByIdAndBetween(salesmanId, start, end)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PaymentResponse recordPayment(
            RecordPaymentRequest req,
            String collectedByPhone) {

        Customer customer = customerRepository
                .findById(req.getCustomerId())
                .orElseThrow(() ->
                        new RuntimeException("Customer not found"));

        User collector = userRepository
                .findByPhone(collectedByPhone)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        Bill bill = null;
        if (req.getBillId() != null) {
            bill = billRepository
                    .findById(req.getBillId())
                    .orElse(null);
        }

        Payment payment = Payment.builder()
                .customer(customer)
                .bill(bill)
                .amount(req.getAmount())
                .paymentMode(req.getPaymentMode())
                .notes(req.getNotes())
                .collectedBy(collector)
                .build();

        // Reduce customer pending balance
        BigDecimal newPending = customer
                .getTotalPending()
                .subtract(req.getAmount());

        customer.setTotalPending(
                newPending.compareTo(BigDecimal.ZERO) < 0
                        ? BigDecimal.ZERO : newPending);
        customerRepository.save(customer);

        // Update bill paid amount if linked
        if (bill != null) {
            bill.setPaidAmount(
                    bill.getPaidAmount().add(req.getAmount()));
            bill.setPendingAmount(
                    bill.getGrandTotal()
                            .subtract(bill.getPaidAmount()));
            billRepository.save(bill);
        }

        Payment saved = paymentRepository.save(payment);
        return toResponse(saved);
    }

    // ── Delete payment (ADMIN only) ──
    // Reverses customer balance and linked bill amounts,
    // then permanently removes the payment record.
    @Transactional
    public void deletePayment(UUID paymentId) {

        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Payment not found: "
                                        + paymentId));

        // Restore customer pending balance
        Customer customer = payment.getCustomer();
        customer.setTotalPending(
                customer.getTotalPending()
                        .add(payment.getAmount()));
        customerRepository.save(customer);

        // If linked to a bill, reverse bill paid/pending amounts
        Bill bill = payment.getBill();
        if (bill != null) {
            BigDecimal newPaid = bill.getPaidAmount()
                    .subtract(payment.getAmount());
            bill.setPaidAmount(
                    newPaid.compareTo(BigDecimal.ZERO) < 0
                            ? BigDecimal.ZERO : newPaid);
            bill.setPendingAmount(
                    bill.getGrandTotal()
                            .subtract(bill.getPaidAmount()));
            billRepository.save(bill);
        }

        paymentRepository.delete(payment);
    }

    // ── Update payment details (ADMIN/MANAGER only) ──
    @Transactional
    public PaymentResponse updatePayment(UUID id, String paymentMode, String notes) {
        Payment payment = paymentRepository
                .findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Payment not found: " + id));

        if (paymentMode != null) {
            payment.setPaymentMode(paymentMode);
        }
        if (notes != null) {
            payment.setNotes(notes);
        }

        return toResponse(paymentRepository.save(payment));
    }
}