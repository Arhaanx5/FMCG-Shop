package com.shop.common.ledger;

import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.billing.Bill;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.khata.PaymentRepository;
import com.shop.modules.khata.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/**
 * Shared service for cross-cutting customer ledger and balance recalculations.
 * Prevents code duplication and divergence between billing and payment domains.
 */
@Service
@RequiredArgsConstructor
public class CustomerLedgerService {

    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final CustomerRepository customerRepository;

    /**
     * Recalculates outstanding balances for a customer by summing up unpaid invoices
     * and deducting unallocated (general) payments against the opening balance.
     * Updates and saves the Customer entity.
     *
     * @param customer the customer whose pending balance needs recalculation
     */
    @Transactional
    public void recalculateCustomerPending(Customer customer) {
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
}
