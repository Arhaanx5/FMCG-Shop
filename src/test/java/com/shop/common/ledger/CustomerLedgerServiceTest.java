package com.shop.common.ledger;

import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.khata.Payment;
import com.shop.modules.khata.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Focused unit tests for CustomerLedgerService.
 */
@ExtendWith(MockitoExtension.class)
class CustomerLedgerServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BillRepository billRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerLedgerService customerLedgerService;

    private Customer createCustomer(UUID id, BigDecimal openingBalance) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName("Test Customer");
        customer.setOpeningBalance(openingBalance);
        customer.setTotalPending(BigDecimal.ZERO);
        return customer;
    }

    private Bill createBill(BigDecimal pendingAmount, BillStatus status) {
        Bill bill = new Bill();
        bill.setPendingAmount(pendingAmount);
        bill.setStatus(status);
        return bill;
    }

    private Payment createGeneralPayment(BigDecimal amount, BigDecimal appliedAmount) {
        Payment payment = new Payment();
        payment.setBill(null); // General payment (not applied to a specific bill)
        payment.setAmount(amount);
        payment.setAppliedAmount(appliedAmount);
        return payment;
    }

    @Test
    @DisplayName("Opening balance without general payments → total pending equals opening balance")
    void recalculate_opening_balance_with_no_general_payments() {
        UUID customerId = UUID.randomUUID();
        Customer customer = createCustomer(customerId, new BigDecimal("10000.00"));

        when(paymentRepository.findByCustomerIdOrderByPaidAtDesc(customerId))
                .thenReturn(Collections.emptyList());
        when(billRepository.findByCustomerIdOrderByCreatedAtDesc(customerId))
                .thenReturn(Collections.emptyList());

        customerLedgerService.recalculateCustomerPending(customer);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getTotalPending()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("Opening balance partially paid by general payments → total pending is balance - payments")
    void recalculate_opening_balance_partially_paid() {
        UUID customerId = UUID.randomUUID();
        Customer customer = createCustomer(customerId, new BigDecimal("10000.00"));

        Payment payment1 = createGeneralPayment(new BigDecimal("3000.00"), new BigDecimal("3000.00"));
        Payment payment2 = createGeneralPayment(new BigDecimal("2000.00"), null); // fallback to getAmount()

        when(paymentRepository.findByCustomerIdOrderByPaidAtDesc(customerId))
                .thenReturn(Arrays.asList(payment1, payment2));
        when(billRepository.findByCustomerIdOrderByCreatedAtDesc(customerId))
                .thenReturn(Collections.emptyList());

        customerLedgerService.recalculateCustomerPending(customer);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        // 10000 - (3000 + 2000) = 5000
        assertThat(customerCaptor.getValue().getTotalPending()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("Opening balance fully paid/overpaid by general payments → unpaid opening balance clamped to zero")
    void recalculate_negative_opening_balance_clamped_to_zero() {
        UUID customerId = UUID.randomUUID();
        Customer customer = createCustomer(customerId, new BigDecimal("10000.00"));

        Payment payment = createGeneralPayment(new BigDecimal("15000.00"), new BigDecimal("15000.00"));

        when(paymentRepository.findByCustomerIdOrderByPaidAtDesc(customerId))
                .thenReturn(Collections.singletonList(payment));
        when(billRepository.findByCustomerIdOrderByCreatedAtDesc(customerId))
                .thenReturn(Collections.emptyList());

        customerLedgerService.recalculateCustomerPending(customer);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        // 10000 - 15000 = -5000 -> clamped to 0
        assertThat(customerCaptor.getValue().getTotalPending()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Pending invoices included, cancelled invoices ignored → total pending sums correct bills")
    void recalculate_sums_all_non_cancelled_invoices_and_ignores_cancelled() {
        UUID customerId = UUID.randomUUID();
        Customer customer = createCustomer(customerId, BigDecimal.ZERO);

        Bill bill1 = createBill(new BigDecimal("1500.00"), BillStatus.CONFIRMED);
        Bill bill2 = createBill(new BigDecimal("2500.00"), BillStatus.PARTIAL);
        Bill bill3 = createBill(new BigDecimal("1000.00"), BillStatus.CANCELLED);
        Bill bill4 = createBill(new BigDecimal("500.00"), BillStatus.DRAFT);

        when(paymentRepository.findByCustomerIdOrderByPaidAtDesc(customerId))
                .thenReturn(Collections.emptyList());
        when(billRepository.findByCustomerIdOrderByCreatedAtDesc(customerId))
                .thenReturn(Arrays.asList(bill1, bill2, bill3, bill4));

        customerLedgerService.recalculateCustomerPending(customer);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        // 1500 + 2500 + 500 = 4500 (ignores L1000 cancelled)
        assertThat(customerCaptor.getValue().getTotalPending()).isEqualByComparingTo("4500.00");
    }

    @Test
    @DisplayName("Mixed scenario (Opening balance + general payments + active bills) → calculates correct total")
    void recalculate_mixed_scenario() {
        UUID customerId = UUID.randomUUID();
        Customer customer = createCustomer(customerId, new BigDecimal("10000.00"));

        Payment payment = createGeneralPayment(new BigDecimal("4000.00"), new BigDecimal("4000.00"));
        Bill bill1 = createBill(new BigDecimal("3000.00"), BillStatus.CONFIRMED);
        Bill bill2 = createBill(new BigDecimal("2000.00"), BillStatus.CANCELLED); // ignored

        when(paymentRepository.findByCustomerIdOrderByPaidAtDesc(customerId))
                .thenReturn(Collections.singletonList(payment));
        when(billRepository.findByCustomerIdOrderByCreatedAtDesc(customerId))
                .thenReturn(Arrays.asList(bill1, bill2));

        customerLedgerService.recalculateCustomerPending(customer);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        // Unpaid opening: 10000 - 4000 = 6000
        // Bill pending: 3000
        // Total pending: 6000 + 3000 = 9000
        assertThat(customerCaptor.getValue().getTotalPending()).isEqualByComparingTo("9000.00");
    }
}
