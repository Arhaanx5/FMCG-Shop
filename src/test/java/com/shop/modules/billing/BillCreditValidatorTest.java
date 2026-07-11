package com.shop.modules.billing;

import com.shop.modules.billing.validator.BillCreditValidator;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BillCreditValidator}.
 *
 * <p>Covers all three validation methods:
 * <ul>
 *   <li>{@code validateNpaBlock} — NPA-block only</li>
 *   <li>{@code validateCreditLimit} — full-amount credit-limit check</li>
 *   <li>{@code validateForUpdate} — delta-based update check</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BillCreditValidatorTest {

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private BillCreditValidator validator;

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Customer npaCustomer(String name, BigDecimal totalPending) {
        Customer c = new Customer();
        c.setName(name);
        c.setIsNpa(true);
        c.setTotalPending(totalPending);
        return c;
    }

    private Customer normalCustomer(String name, BigDecimal totalPending) {
        Customer c = new Customer();
        c.setName(name);
        c.setIsNpa(false);
        c.setTotalPending(totalPending);
        return c;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateNpaBlock()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateNpaBlock")
    class ValidateNpaBlock {

        @Test
        @DisplayName("NPA customer with UDHAR mode → RuntimeException thrown")
        void npa_udhar_is_blocked() {
            Customer npa = npaCustomer("Ramesh Stores", BigDecimal.ZERO);
            assertThatThrownBy(() -> validator.validateNpaBlock(npa, PaymentMode.UDHAR))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Credit sales are blocked for NPA customer: Ramesh Stores");
        }

        @Test
        @DisplayName("NPA customer with PARTIAL mode → RuntimeException thrown")
        void npa_partial_is_blocked() {
            Customer npa = npaCustomer("Ramesh Stores", BigDecimal.ZERO);
            assertThatThrownBy(() -> validator.validateNpaBlock(npa, PaymentMode.PARTIAL))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Credit sales are blocked for NPA customer");
        }

        @Test
        @DisplayName("NPA customer with CASH mode → no exception (cash is allowed)")
        void npa_cash_is_allowed() {
            Customer npa = npaCustomer("Ramesh Stores", BigDecimal.ZERO);
            assertThatNoException().isThrownBy(() -> validator.validateNpaBlock(npa, PaymentMode.CASH));
        }

        @Test
        @DisplayName("Non-NPA customer with UDHAR → no exception")
        void normal_customer_udhar_passes_npa_check() {
            Customer normal = normalCustomer("Suresh Traders", BigDecimal.ZERO);
            assertThatNoException().isThrownBy(() -> validator.validateNpaBlock(normal, PaymentMode.UDHAR));
        }

        @Test
        @DisplayName("Customer with null isNpa field → no exception (treated as non-NPA)")
        void null_isNpa_treated_as_non_npa() {
            Customer c = new Customer();
            c.setName("Unknown");
            c.setIsNpa(null);
            assertThatNoException().isThrownBy(() -> validator.validateNpaBlock(c, PaymentMode.UDHAR));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateCreditLimit()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateCreditLimit")
    class ValidateCreditLimit {

        private final BigDecimal LIMIT = new BigDecimal("50000");

        @BeforeEach
        void stubLimit() {
            // stub for any customer arg (lenient because CASH/COD skip this check)
            lenient().when(customerService.calculateEffectiveCreditLimit(any())).thenReturn(LIMIT);
        }

        @Test
        @DisplayName("UDHAR + projected pending exceeds limit → RuntimeException")
        void credit_limit_exceeded_throws() {
            Customer c = normalCustomer("Vijay Kiryana", new BigDecimal("48000"));
            // adding ₹5000 → projected = 53000 > 50000
            assertThatThrownBy(() -> validator.validateCreditLimit(
                            c, PaymentMode.UDHAR, new BigDecimal("5000"), "Requested Credit"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Credit limit exceeded for customer: Vijay Kiryana")
                    .hasMessageContaining("Requested Credit: ₹5000");
        }

        @Test
        @DisplayName("UDHAR + projected pending exactly at limit → no exception")
        void credit_limit_exact_boundary_passes() {
            Customer c = normalCustomer("Vijay Kiryana", new BigDecimal("45000"));
            // adding ₹5000 → projected = 50000 == limit → should pass
            assertThatNoException().isThrownBy(() -> validator.validateCreditLimit(
                    c, PaymentMode.UDHAR, new BigDecimal("5000"), "Requested Credit"));
        }

        @Test
        @DisplayName("PARTIAL + projected pending exceeds limit → RuntimeException")
        void partial_credit_limit_exceeded() {
            Customer c = normalCustomer("Vijay Kiryana", new BigDecimal("49000"));
            assertThatThrownBy(() -> validator.validateCreditLimit(
                            c, PaymentMode.PARTIAL, new BigDecimal("2000"), "Requested Credit"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Credit limit exceeded");
        }

        @Test
        @DisplayName("CASH payment → credit check skipped entirely (no customerService call)")
        void cash_payment_skips_credit_check() {
            Customer c = normalCustomer("Cash Buyer", new BigDecimal("99999"));
            assertThatNoException().isThrownBy(() -> validator.validateCreditLimit(
                    c, PaymentMode.CASH, new BigDecimal("99999"), "Requested Credit"));
            verifyNoInteractions(customerService);
        }

        @Test
        @DisplayName("COD payment → credit check skipped (no customerService call)")
        void cod_payment_skips_credit_check() {
            Customer c = normalCustomer("COD Buyer", new BigDecimal("99999"));
            assertThatNoException().isThrownBy(() -> validator.validateCreditLimit(
                    c, PaymentMode.COD, new BigDecimal("5000"), "Requested Credit"));
            verifyNoInteractions(customerService);
        }

        @Test
        @DisplayName("Error message contains correct label (Restoring Bill Pending)")
        void error_message_uses_provided_label() {
            Customer c = normalCustomer("Restore Customer", new BigDecimal("48000"));
            assertThatThrownBy(() -> validator.validateCreditLimit(
                            c, PaymentMode.UDHAR, new BigDecimal("5000"), "Restoring Bill Pending"))
                    .hasMessageContaining("Restoring Bill Pending: ₹5000");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateForUpdate()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateForUpdate")
    class ValidateForUpdate {

        private final BigDecimal LIMIT = new BigDecimal("50000");

        @Test
        @DisplayName("diff <= 0 → no checks at all (no customerService call)")
        void no_increase_skips_all_checks() {
            Customer c = npaCustomer("NPA Guy", new BigDecimal("60000")); // would fail if checked
            assertThatNoException().isThrownBy(() ->
                    validator.validateForUpdate(c, BigDecimal.ZERO));
            assertThatNoException().isThrownBy(() ->
                    validator.validateForUpdate(c, new BigDecimal("-100")));
            verifyNoInteractions(customerService);
        }

        @Test
        @DisplayName("diff > 0 on NPA customer → RuntimeException (no PaymentMode guard)")
        void increase_on_npa_customer_blocked() {
            Customer npa = npaCustomer("NPA Update", BigDecimal.ZERO);
            assertThatThrownBy(() -> validator.validateForUpdate(npa, new BigDecimal("100")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Credit sales are blocked for NPA customer: NPA Update");
        }

        @Test
        @DisplayName("diff > 0 + exceeds credit limit → RuntimeException with Additional Credit label")
        void increase_exceeds_limit_throws() {
            Customer c = normalCustomer("Limit Breaker", new BigDecimal("48000"));
            when(customerService.calculateEffectiveCreditLimit(c)).thenReturn(LIMIT);

            assertThatThrownBy(() -> validator.validateForUpdate(c, new BigDecimal("5000")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Credit limit exceeded for customer: Limit Breaker")
                    .hasMessageContaining("Additional Credit: ₹5000");
        }

        @Test
        @DisplayName("diff > 0 + within credit limit → no exception")
        void increase_within_limit_passes() {
            Customer c = normalCustomer("Safe Customer", new BigDecimal("40000"));
            when(customerService.calculateEffectiveCreditLimit(c)).thenReturn(LIMIT);

            assertThatNoException().isThrownBy(() ->
                    validator.validateForUpdate(c, new BigDecimal("5000")));
        }

        @Test
        @DisplayName("diff exactly reaches limit boundary → no exception")
        void increase_exactly_at_limit_passes() {
            Customer c = normalCustomer("Boundary Customer", new BigDecimal("45000"));
            when(customerService.calculateEffectiveCreditLimit(c)).thenReturn(LIMIT);

            // 45000 + 5000 = 50000 = limit → should pass (not > limit)
            assertThatNoException().isThrownBy(() ->
                    validator.validateForUpdate(c, new BigDecimal("5000")));
        }
    }
}
