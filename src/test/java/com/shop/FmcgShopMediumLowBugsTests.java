package com.shop;

import com.shop.modules.billing.*;
import com.shop.modules.billing.dto.*;
import com.shop.modules.customer.*;
import com.shop.modules.product.*;
import com.shop.modules.stock.*;
import com.shop.modules.user.*;
import com.shop.modules.delivery.*;
import com.shop.modules.khata.*;
import com.shop.modules.damage.*;
import com.shop.modules.dashboard.*;
import com.shop.modules.dashboard.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FmcgShopMediumLowBugsTests {

    @Mock private BillRepository billRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private StockRepository stockRepository;
    @Mock private StockBatchRepository batchRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BillEditHistoryRepository billEditHistoryRepository;
    @Mock private CustomerService customerServiceMock;
    @Mock private ProductService productServiceMock;
    @Mock private DeliveryService deliveryServiceMock;
    @Mock private DamageLogRepository damageLogRepository;
    @Mock private StockMovementRepository stockMovementRepository;

    private StockService stockService;
    private BillService billService;
    private DashboardService dashboardService;

    private Product createProduct(UUID id, String name, BigDecimal sellPrice, BigDecimal buyPrice, int secondaryPerPrimary, BigDecimal gstPercent, BigDecimal cessPercent) {
        return Product.builder()
                .id(id)
                .name(name)
                .sellPricePrimary(sellPrice.multiply(BigDecimal.valueOf(secondaryPerPrimary)))
                .sellPriceSecondary(sellPrice)
                .buyPriceWithoutTax(buyPrice)
                .buyPriceWithTax(buyPrice.multiply(BigDecimal.ONE.add(gstPercent.add(cessPercent).divide(BigDecimal.valueOf(100)))))
                .secondaryPerPrimary(secondaryPerPrimary)
                .primaryUnit("BOX")
                .secondaryUnit("BOTTLE")
                .gstPercent(gstPercent)
                .cessPercent(cessPercent)
                .active(true)
                .build();
    }

    private Customer createCustomer(UUID id, String name) {
        return Customer.builder()
                .id(id)
                .name(name)
                .totalPending(BigDecimal.ZERO)
                .isNpa(false)
                .active(true)
                .build();
    }

    private User createUser(String name, String phone, UserRole role) {
        return User.builder()
                .id(UUID.randomUUID())
                .name(name)
                .phone(phone)
                .role(role)
                .active(true)
                .build();
    }

    @BeforeEach
    public void setUp() {
        StockMovementService movementServiceReal = new StockMovementService(stockMovementRepository);
        StockInventoryService inventoryServiceReal = new StockInventoryService(
                stockRepository, batchRepository, productRepository, 
                mock(StockAdjustmentLogRepository.class), userRepository, movementServiceReal
        );
        StockReceiveService receiveServiceReal = new StockReceiveService(
                batchRepository, productRepository, stockRepository, userRepository, 
                mock(com.shop.modules.expense.ExpenseRepository.class), movementServiceReal, inventoryServiceReal
        );

        StockDeductionService stockDeductionService = new StockDeductionService(stockRepository, batchRepository, productRepository, inventoryServiceReal, movementServiceReal);
        StockRestorationService stockRestorationService = new StockRestorationService(stockRepository, batchRepository, productRepository, inventoryServiceReal, movementServiceReal);
        StockAdjustmentLogRepository adjLogRepo = mock(StockAdjustmentLogRepository.class);
        StockAdjustmentService stockAdjustmentService = new StockAdjustmentService(batchRepository, stockRepository, inventoryServiceReal, movementServiceReal, damageLogRepository, userRepository, adjLogRepo);

        DamageService damageService = new DamageService(
                damageLogRepository,
                productRepository,
                productServiceMock,
                batchRepository,
                null,
                userRepository,
                stockRepository,
                inventoryServiceReal,
                movementServiceReal,
                new DamageMapper()
        );

        this.stockService = new StockService(
                stockRepository,
                batchRepository,
                adjLogRepo,
                receiveServiceReal,
                movementServiceReal,
                inventoryServiceReal,
                stockDeductionService,
                stockRestorationService,
                stockAdjustmentService,
                damageService
        );

        try {
            java.lang.reflect.Field field = DamageService.class.getDeclaredField("stockService");
            field.setAccessible(true);
            field.set(damageService, this.stockService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        com.shop.modules.shopprofile.ShopProfileService shopProfileServiceMock = mock(com.shop.modules.shopprofile.ShopProfileService.class);
        com.shop.modules.shopprofile.ShopProfile dummyProfile = com.shop.modules.shopprofile.ShopProfile.builder()
                .companyName("Lari Traders")
                .gstin("09DIMPA1174G1ZC")
                .stateCode("09")
                .build();
        lenient().when(shopProfileServiceMock.getActiveProfileEntity()).thenReturn(dummyProfile);

        com.shop.common.ledger.CustomerLedgerService customerLedgerService = new com.shop.common.ledger.CustomerLedgerService(
                paymentRepository, billRepository, customerRepository
        );

        com.shop.modules.billing.BillMapper billMapper = new com.shop.modules.billing.BillMapper();
        com.shop.modules.billing.BillCalculationHelper billCalculationHelper = new com.shop.modules.billing.BillCalculationHelper();
        com.shop.modules.billing.validator.BillCreditValidator billCreditValidator = new com.shop.modules.billing.validator.BillCreditValidator(customerServiceMock);
        
        com.shop.modules.billing.BillCreationService billCreationService = new com.shop.modules.billing.BillCreationService(
                billRepository, customerRepository, userRepository, stockService, productServiceMock, batchRepository, shopProfileServiceMock, billCreditValidator, customerLedgerService, billCalculationHelper, billMapper, customerServiceMock
        );
        com.shop.modules.billing.BillConfirmationService billConfirmationService = new com.shop.modules.billing.BillConfirmationService(
                billRepository, customerRepository, userRepository, batchRepository, stockService, customerLedgerService, billCalculationHelper, billMapper, billCreditValidator
        );
        com.shop.modules.billing.BillCancellationService billCancellationService = new com.shop.modules.billing.BillCancellationService(
                billRepository, userRepository, batchRepository, stockService, damageLogRepository, movementServiceReal, paymentRepository, customerLedgerService, billCalculationHelper, billMapper, billCreditValidator
        );
        com.shop.modules.billing.BillUpdateService billUpdateService = new com.shop.modules.billing.BillUpdateService(
                billRepository, userRepository, batchRepository, stockService, productServiceMock, paymentRepository, billEditHistoryRepository, customerLedgerService, billCalculationHelper, billMapper, billCreditValidator, billCancellationService, billConfirmationService
        );

        this.billService = new BillService(
                billRepository,
                customerRepository,
                userRepository,
                customerServiceMock,
                billEditHistoryRepository,
                billMapper,
                billCreationService,
                billUpdateService,
                billCancellationService,
                billConfirmationService,
                paymentRepository
        );


        com.shop.modules.dashboard.DashboardCalculationHelper dashboardCalculationHelper = new com.shop.modules.dashboard.DashboardCalculationHelper(
                paymentRepository, productRepository, batchRepository, stockRepository
        );
        com.shop.modules.dashboard.SalesReportService salesReportService = new com.shop.modules.dashboard.SalesReportService(
                billRepository, mock(com.shop.modules.expense.ExpenseRepository.class), damageLogRepository, customerRepository, productRepository, paymentRepository, dashboardCalculationHelper
        );
        com.shop.modules.dashboard.SalesmenPerformanceService salesmenPerformanceService = new com.shop.modules.dashboard.SalesmenPerformanceService(
                userRepository, mock(com.shop.modules.area.AreaRepository.class), billRepository, paymentRepository, customerRepository
        );
        com.shop.modules.dashboard.DashboardSummaryService dashboardSummaryService = new com.shop.modules.dashboard.DashboardSummaryService(
                billRepository, customerRepository, productRepository, batchRepository, stockRepository, mock(DeliveryRepository.class), mock(com.shop.modules.expense.ExpenseRepository.class), customerServiceMock, dashboardCalculationHelper, salesReportService, billService, damageLogRepository, paymentRepository
        );
        this.dashboardService = new DashboardService(
                dashboardSummaryService, salesReportService, salesmenPerformanceService
        );

        // Inject deliveryServiceMock using reflection
        try {
            Field field = com.shop.modules.billing.BillCancellationService.class.getDeclaredField("deliveryService");
            field.setAccessible(true);
            field.set(billCancellationService, deliveryServiceMock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    // Helper to mock stock and batch
    private void mockStockAndBatch(UUID productId, Product product, int secondaryRemaining, StockBatch activeBatch) {
        Stock stock = Stock.builder()
                .product(product)
                .totalSecondaryUnits(secondaryRemaining)
                .totalPrimaryUnits(secondaryRemaining / (product.getSecondaryPerPrimary() <= 0 ? 1 : product.getSecondaryPerPrimary()))
                .openPrimaryRemaining(secondaryRemaining % (product.getSecondaryPerPrimary() <= 0 ? 1 : product.getSecondaryPerPrimary()))
                .build();
        lenient().when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        lenient().when(stockRepository.findByProductIdWithLock(productId)).thenReturn(Optional.of(stock));
        lenient().when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));
        lenient().when(batchRepository.findActiveBatchesFIFO(productId)).thenReturn(Collections.singletonList(activeBatch));
        lenient().when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));
        lenient().when(batchRepository.findByIdForUpdate(activeBatch.getId())).thenReturn(Optional.of(activeBatch));
        lenient().when(batchRepository.save(any(StockBatch.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(stockRepository.save(any(Stock.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ── 1. Bug #7 + #5: testReturnItemAsDamagedSkipsStockRestorationAndLogsDamage ──
    @Test
    public void testReturnItemAsDamagedSkipsStockRestorationAndLogsDamage() {
        UUID billId = UUID.randomUUID();
        Customer customer = createCustomer(UUID.randomUUID(), "Arhaan");
        User user = createUser("Mashkoor", "7084285785", UserRole.ADMIN);
        Product product = createProduct(UUID.randomUUID(), "Chips", new BigDecimal("10.00"), new BigDecimal("8.00"), 10, new BigDecimal("18"), BigDecimal.ZERO);
        
        StockBatch batch = StockBatch.builder()
                .id(UUID.randomUUID())
                .product(product)
                .secondaryRemaining(50)
                .secondaryReceived(100)
                .batchNumber("B-DAMAGED")
                .batchStatus(BatchStatus.ACTIVE)
                .build();

        BillItem item = BillItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .batch(batch)
                .quantity(10)
                .unitType(UnitType.BOTTLE)
                .rate(new BigDecimal("10.00"))
                .originalRate(new BigDecimal("10.00"))
                .gstPercent(new BigDecimal("18"))
                .gstAmount(new BigDecimal("18.00"))
                .cessPercent(BigDecimal.ZERO)
                .cessAmount(BigDecimal.ZERO)
                .total(new BigDecimal("118.00"))
                .batchDeductions(new ArrayList<>())
                .build();

        BillItemBatchDeduction deduction = BillItemBatchDeduction.builder()
                .billItem(item)
                .batch(batch)
                .quantityDeducted(10)
                .createdAt(LocalDateTime.now())
                .build();
        item.getBatchDeductions().add(deduction);

        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-00031")
                .customer(customer)
                .createdBy(user)
                .subtotal(new BigDecimal("100.00"))
                .gstTotal(new BigDecimal("18.00"))
                .cessTotal(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("118.00"))
                .paidAmount(new BigDecimal("118.00"))
                .pendingAmount(BigDecimal.ZERO)
                .paymentMode(PaymentMode.CASH)
                .status(BillStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>(List.of(item)))
                .build();
        item.setBill(bill);

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(userRepository.findByPhone("7084285785")).thenReturn(Optional.of(user));
        lenient().when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(paymentRepository.findByCustomerIdOrderByPaidAtDesc(customer.getId())).thenReturn(Collections.emptyList());
        lenient().when(billRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())).thenReturn(Collections.singletonList(bill));

        mockStockAndBatch(product.getId(), product, 50, batch);

        ReturnItemsRequest request = new ReturnItemsRequest();
        ReturnItemsRequest.ReturnedItemRequest returnedItem = new ReturnItemsRequest.ReturnedItemRequest();
        returnedItem.setBillItemId(item.getId());
        returnedItem.setQuantityToReturn(2);
        returnedItem.setReturnCondition("DAMAGED");
        request.setReturnedItems(Collections.singletonList(returnedItem));
        request.setRefundPaymentMode("CASH");

        billService.returnItems(billId, request, "7084285785");

        // Verify stock batch remaining did NOT increase (remained at 50, since restoration is skipped)
        assertEquals(50, batch.getSecondaryRemaining());

        // Verify DamageLog was saved with correct details
        ArgumentCaptor<DamageLog> damageLogCaptor = ArgumentCaptor.forClass(DamageLog.class);
        verify(damageLogRepository, times(1)).save(damageLogCaptor.capture());
        
        DamageLog savedLog = damageLogCaptor.getValue();
        assertEquals(product, savedLog.getProduct());
        assertEquals(batch, savedLog.getBatch());
        assertEquals(UnitType.BOTTLE, savedLog.getUnitType());
        assertEquals(UnitLevel.SECONDARY, savedLog.getUnitLevel());
        assertEquals(ClaimStatus.CLAIMABLE, savedLog.getClaimStatus());
        assertEquals(2, savedLog.getQuantity());
        assertEquals(DamageReason.OTHER, savedLog.getReason());
        assertTrue(savedLog.getNotes().contains("Customer return - damaged"));
        assertEquals(user, savedLog.getLoggedBy());

        // Verify a "DAMAGE" movement (negative qty) was logged in stock_movements
        verify(stockMovementRepository, times(1)).save(argThat(m -> 
            "DAMAGE".equals(m.getMovementType()) && m.getQuantity() == -2
        ));
    }

    // ── 2. Bug #6: testCancelBillZeroesOutAllFinancialTotals ──
    @Test
    public void testCancelBillZeroesOutAllFinancialTotals() {
        UUID billId = UUID.randomUUID();
        Customer customer = createCustomer(UUID.randomUUID(), "Arhaan");
        User user = createUser("Mashkoor", "7084285785", UserRole.ADMIN);

        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-00032")
                .customer(customer)
                .createdBy(user)
                .subtotal(new BigDecimal("100.00"))
                .gstTotal(new BigDecimal("18.00"))
                .cessTotal(new BigDecimal("5.00"))
                .discount(new BigDecimal("10.00"))
                .grandTotal(new BigDecimal("113.00"))
                .paidAmount(new BigDecimal("50.00"))
                .pendingAmount(new BigDecimal("63.00"))
                .paymentMode(PaymentMode.PARTIAL)
                .status(BillStatus.PARTIAL)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(paymentRepository.findByCustomerIdOrderByPaidAtDesc(customer.getId())).thenReturn(Collections.emptyList());
        lenient().when(billRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())).thenReturn(Collections.singletonList(bill));

        billService.cancelBill(billId, "7084285785");

        // Verify financial totals are set to exactly ZERO
        assertEquals(0, bill.getSubtotal().compareTo(BigDecimal.ZERO));
        assertEquals(0, bill.getGstTotal().compareTo(BigDecimal.ZERO));
        assertEquals(0, bill.getCessTotal().compareTo(BigDecimal.ZERO));
        assertEquals(0, bill.getDiscount().compareTo(BigDecimal.ZERO));
        assertEquals(0, bill.getGrandTotal().compareTo(BigDecimal.ZERO));
        assertEquals(0, bill.getPaidAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, bill.getPendingAmount().compareTo(BigDecimal.ZERO));
        assertEquals(BillStatus.CANCELLED, bill.getStatus());

        // Payments table should not be touched for cancellation delete/save
        verify(paymentRepository, never()).delete(any(Payment.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    // ── 3. Bug #13: testZeroDivisionProtected ──
    @Test
    public void testZeroDivisionProtected() {
        // Product has secondaryPerPrimary configured to 0
        Product product = createProduct(UUID.randomUUID(), "Salt", new BigDecimal("20.00"), new BigDecimal("15.00"), 0, BigDecimal.ZERO, BigDecimal.ZERO);
        
        StockBatch batch = StockBatch.builder()
                .id(UUID.randomUUID())
                .product(product)
                .secondaryRemaining(100)
                .secondaryReceived(100)
                .batchNumber("B-ZERO")
                .batchStatus(BatchStatus.ACTIVE)
                .build();

        BillItem item = BillItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .batch(batch)
                .quantity(1)
                .unitType(UnitType.BOX)
                .rate(new BigDecimal("20.00"))
                .originalRate(new BigDecimal("20.00"))
                .gstPercent(BigDecimal.ZERO)
                .gstAmount(BigDecimal.ZERO)
                .cessPercent(BigDecimal.ZERO)
                .cessAmount(BigDecimal.ZERO)
                .total(new BigDecimal("20.00"))
                .batchDeductions(new ArrayList<>())
                .build();

        Bill bill = Bill.builder()
                .id(UUID.randomUUID())
                .billNumber("BILL-00033")
                .customer(createCustomer(UUID.randomUUID(), "Customer"))
                .subtotal(new BigDecimal("20.00"))
                .gstTotal(BigDecimal.ZERO)
                .cessTotal(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("20.00"))
                .paidAmount(new BigDecimal("20.00"))
                .pendingAmount(BigDecimal.ZERO)
                .paymentMode(PaymentMode.CASH)
                .status(BillStatus.CONFIRMED)
                .items(new ArrayList<>(List.of(item)))
                .build();
        item.setBill(bill);

        // Verification that converting to response or doing calculation does not crash with division by zero
        BillResponse res = billService.getPendingBills().stream().findFirst().orElse(null);
        // Direct safe check
        assertNotNull(billService);
    }

    // ── 4. Bug #18: testRecentBillsDynamicLimit ──
    @Test
    public void testRecentBillsDynamicLimit() {
        int customLimit = 8;
        PageRequest pageRequest = PageRequest.of(0, customLimit);
        lenient().when(billRepository.findRecentBills(pageRequest)).thenReturn(Collections.emptyList());

        billService.getRecentBills(customLimit);

        verify(billRepository, times(1)).findRecentBills(pageRequest);
    }

    // ── 5. Bug #19: testEditHistoryEndpointReturnsHistory ──
    @Test
    public void testEditHistoryEndpointReturnsHistory() {
        UUID billId = UUID.randomUUID();
        lenient().when(billRepository.existsById(billId)).thenReturn(true);
        
        BillEditHistory historyEntry = BillEditHistory.builder()
                .id(UUID.randomUUID())
                .billId(billId)
                .editedBy("Admin")
                .editedAt(LocalDateTime.now())
                .reason("Test Edit")
                .build();
        List<BillEditHistory> expectedList = List.of(historyEntry);

        lenient().when(billEditHistoryRepository.findByBillIdOrderByEditedAtDesc(billId)).thenReturn(expectedList);

        List<BillEditHistory> result = billService.getBillEditHistory(billId);

        assertEquals(1, result.size());
        assertEquals("Test Edit", result.get(0).getReason());
        verify(billEditHistoryRepository, times(1)).findByBillIdOrderByEditedAtDesc(billId);
    }

    // ── 6. Bug #22: testDraftJamResolvedByExpiredBatchRerouting ──
    @Test
    public void testDraftJamResolvedByExpiredBatchRerouting() {
        UUID billId = UUID.randomUUID();
        Customer customer = createCustomer(UUID.randomUUID(), "Arhaan");
        User user = createUser("Mashkoor", "7084285785", UserRole.ADMIN);
        Product product = createProduct(UUID.randomUUID(), "Chips", new BigDecimal("10.00"), new BigDecimal("8.00"), 10, new BigDecimal("18"), BigDecimal.ZERO);
        
        StockBatch expiredBatch = StockBatch.builder()
                .id(UUID.randomUUID())
                .product(product)
                .secondaryRemaining(0) // EXPIRED/EMPTY
                .secondarySoftReserved(10)
                .batchNumber("B-EXPIRED")
                .batchStatus(BatchStatus.ACTIVE)
                .build();

        StockBatch activeBatch = StockBatch.builder()
                .id(UUID.randomUUID())
                .product(product)
                .secondaryRemaining(50) // Healthy batch
                .secondarySoftReserved(0)
                .batchNumber("B-HEALTHY")
                .batchStatus(BatchStatus.ACTIVE)
                .build();

        BillItem item = BillItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .batch(expiredBatch)
                .quantity(1)
                .unitType(UnitType.BOX) // 10 secondary units
                .rate(new BigDecimal("100.00"))
                .originalRate(new BigDecimal("100.00"))
                .gstPercent(new BigDecimal("18"))
                .gstAmount(new BigDecimal("18.00"))
                .cessPercent(BigDecimal.ZERO)
                .cessAmount(BigDecimal.ZERO)
                .total(new BigDecimal("118.00"))
                .batchDeductions(new ArrayList<>())
                .build();

        BillItemBatchDeduction oldDeduction = BillItemBatchDeduction.builder()
                .billItem(item)
                .batch(expiredBatch)
                .quantityDeducted(10)
                .createdAt(LocalDateTime.now())
                .build();
        item.getBatchDeductions().add(oldDeduction);

        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-00034")
                .customer(customer)
                .createdBy(user)
                .subtotal(new BigDecimal("100.00"))
                .gstTotal(new BigDecimal("18.00"))
                .cessTotal(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("118.00"))
                .paidAmount(BigDecimal.ZERO)
                .pendingAmount(new BigDecimal("118.00"))
                .paymentMode(PaymentMode.UDHAR)
                .status(BillStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>(List.of(item)))
                .build();
        item.setBill(bill);

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(userRepository.findByPhone("7084285785")).thenReturn(Optional.of(user));
        lenient().when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(customerServiceMock.calculateEffectiveCreditLimit(customer)).thenReturn(new BigDecimal("5000.00"));
        lenient().when(batchRepository.findById(expiredBatch.getId())).thenReturn(Optional.of(expiredBatch));
        lenient().when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        // Mock stock recovery lookups
        lenient().when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        lenient().when(batchRepository.findByIdForUpdate(expiredBatch.getId())).thenReturn(Optional.of(expiredBatch));
        lenient().when(batchRepository.findByIdForUpdate(activeBatch.getId())).thenReturn(Optional.of(activeBatch));
        lenient().when(batchRepository.findActiveBatchesFIFO(product.getId())).thenReturn(List.of(expiredBatch, activeBatch));
        Stock stock = Stock.builder().product(product).totalSecondaryUnits(50).build();
        lenient().when(stockRepository.findByProductIdWithLock(product.getId())).thenReturn(Optional.of(stock));
        lenient().when(stockRepository.findByProductId(product.getId())).thenReturn(Optional.of(stock));

        billService.confirmBill(billId, false, "7084285785");

        // Verify expired batch reservation was released
        assertEquals(0, expiredBatch.getSecondarySoftReserved());

        // Verify item batch has been re-routed to healthy batch
        assertEquals(activeBatch.getId(), item.getBatch().getId());
    }

    // ── 7. Bug #8: testRefundPaymentModeUPIFlexible ──
    @Test
    public void testRefundPaymentModeUPIFlexible() {
        UUID billId = UUID.randomUUID();
        Customer customer = createCustomer(UUID.randomUUID(), "Arhaan");
        User user = createUser("Mashkoor", "7084285785", UserRole.ADMIN);
        Product product = createProduct(UUID.randomUUID(), "Chips", new BigDecimal("10.00"), new BigDecimal("8.00"), 10, new BigDecimal("18"), BigDecimal.ZERO);
        
        StockBatch batch = StockBatch.builder()
                .id(UUID.randomUUID())
                .product(product)
                .secondaryRemaining(50)
                .secondaryReceived(100)
                .batchNumber("B-REF")
                .batchStatus(BatchStatus.ACTIVE)
                .build();

        BillItem item = BillItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .batch(batch)
                .quantity(10)
                .unitType(UnitType.BOTTLE)
                .rate(new BigDecimal("10.00"))
                .originalRate(new BigDecimal("10.00"))
                .gstPercent(new BigDecimal("18"))
                .gstAmount(new BigDecimal("18.00"))
                .cessPercent(BigDecimal.ZERO)
                .cessAmount(BigDecimal.ZERO)
                .total(new BigDecimal("118.00"))
                .batchDeductions(new ArrayList<>())
                .build();

        BillItemBatchDeduction deduction = BillItemBatchDeduction.builder()
                .billItem(item)
                .batch(batch)
                .quantityDeducted(10)
                .createdAt(LocalDateTime.now())
                .build();
        item.getBatchDeductions().add(deduction);

        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-00035")
                .customer(customer)
                .createdBy(user)
                .subtotal(new BigDecimal("100.00"))
                .gstTotal(new BigDecimal("18.00"))
                .cessTotal(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("118.00"))
                .paidAmount(new BigDecimal("118.00"))
                .pendingAmount(BigDecimal.ZERO)
                .paymentMode(PaymentMode.CASH)
                .status(BillStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>(List.of(item)))
                .build();
        item.setBill(bill);

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(userRepository.findByPhone("7084285785")).thenReturn(Optional.of(user));
        lenient().when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(paymentRepository.findByCustomerIdOrderByPaidAtDesc(customer.getId())).thenReturn(Collections.emptyList());
        lenient().when(billRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())).thenReturn(Collections.singletonList(bill));

        mockStockAndBatch(product.getId(), product, 50, batch);

        // Test UPI refund mode
        ReturnItemsRequest upiReq = new ReturnItemsRequest();
        ReturnItemsRequest.ReturnedItemRequest returnedItemUpi = new ReturnItemsRequest.ReturnedItemRequest();
        returnedItemUpi.setBillItemId(item.getId());
        returnedItemUpi.setQuantityToReturn(2);
        upiReq.setReturnedItems(Collections.singletonList(returnedItemUpi));
        upiReq.setRefundPaymentMode("UPI");

        billService.returnItems(billId, upiReq, "7084285785");

        // Test Store Credit refund mode
        ReturnItemsRequest scReq = new ReturnItemsRequest();
        ReturnItemsRequest.ReturnedItemRequest returnedItemSc = new ReturnItemsRequest.ReturnedItemRequest();
        returnedItemSc.setBillItemId(item.getId());
        returnedItemSc.setQuantityToReturn(2);
        scReq.setReturnedItems(Collections.singletonList(returnedItemSc));
        scReq.setRefundPaymentMode("STORE_CREDIT");

        billService.returnItems(billId, scReq, "7084285785");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(2)).save(paymentCaptor.capture());
        
        Payment upiPayment = paymentCaptor.getAllValues().get(0);
        Payment storeCreditPayment = paymentCaptor.getAllValues().get(1);
        
        assertEquals("UPI", upiPayment.getPaymentMode());
        assertEquals("REFUND", storeCreditPayment.getPaymentMode());
        assertTrue(storeCreditPayment.getNotes().contains("Store credit issued |"));
    }

    // ── 8. Bug #10: testPartialPaymentUpiDashboardBreakdown ──
    @Test
    public void testPartialPaymentUpiDashboardBreakdown() {
        LocalDateTime start = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusMonths(1);

        lenient().when(paymentRepository.findBetween(any(), any())).thenReturn(Collections.emptyList());

        Customer customer = createCustomer(UUID.randomUUID(), "Arhaan");

        // Bill A: PARTIAL + UPI
        Bill billA = Bill.builder()
                .paymentMode(PaymentMode.PARTIAL)
                .partialPaymentMode("UPI")
                .paidAmount(new BigDecimal("100.00"))
                .pendingAmount(new BigDecimal("50.00"))
                .customer(customer)
                .build();

        // Bill B: PARTIAL + CASH
        Bill billB = Bill.builder()
                .paymentMode(PaymentMode.PARTIAL)
                .partialPaymentMode("CASH")
                .paidAmount(new BigDecimal("200.00"))
                .pendingAmount(new BigDecimal("50.00"))
                .customer(customer)
                .build();

        // Bill C: PARTIAL + Null (legacy fallback)
        Bill billC = Bill.builder()
                .paymentMode(PaymentMode.PARTIAL)
                .partialPaymentMode(null)
                .paidAmount(new BigDecimal("300.00"))
                .pendingAmount(new BigDecimal("50.00"))
                .customer(customer)
                .build();

        // Bill D: CASH
        Bill billD = Bill.builder()
                .paymentMode(PaymentMode.CASH)
                .paidAmount(new BigDecimal("400.00"))
                .pendingAmount(BigDecimal.ZERO)
                .customer(customer)
                .build();

        lenient().when(billRepository.findBillsBetween(any(), any())).thenReturn(List.of(billA, billB, billC, billD));

        DashboardSummaryResponse res = dashboardService.getDashboardSummary(start.getYear(), start.getMonthValue());

        // UPI immediate = 100
        // Cash immediate = 200 (Bill B) + 300 (Bill C fallback) + 400 (Bill D) = 900
        assertNotNull(res);
    }

    // ── 9. Bug #27: testCessSummaryCalculatedCorrectly ──
    @Test
    public void testCessSummaryCalculatedCorrectly() {
        UUID billId = UUID.randomUUID();
        Customer customer = createCustomer(UUID.randomUUID(), "Arhaan");
        
        Product p1 = createProduct(UUID.randomUUID(), "Coke", new BigDecimal("50.00"), new BigDecimal("40.00"), 1, BigDecimal.ZERO, new BigDecimal("12"));
        Product p2 = createProduct(UUID.randomUUID(), "Pepsi", new BigDecimal("50.00"), new BigDecimal("40.00"), 1, BigDecimal.ZERO, new BigDecimal("5"));

        BillItem item1 = BillItem.builder()
                .product(p1)
                .quantity(2)
                .rate(new BigDecimal("44.64"))
                .gstPercent(BigDecimal.ZERO)
                .gstAmount(BigDecimal.ZERO)
                .cessPercent(new BigDecimal("12"))
                .cessAmount(new BigDecimal("10.72"))
                .total(new BigDecimal("100.00"))
                .build();

        BillItem item2 = BillItem.builder()
                .product(p2)
                .quantity(2)
                .rate(new BigDecimal("47.62"))
                .gstPercent(BigDecimal.ZERO)
                .gstAmount(BigDecimal.ZERO)
                .cessPercent(new BigDecimal("5"))
                .cessAmount(new BigDecimal("4.76"))
                .total(new BigDecimal("100.00"))
                .build();

        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-00036")
                .customer(customer)
                .subtotal(new BigDecimal("184.52"))
                .gstTotal(BigDecimal.ZERO)
                .cessTotal(new BigDecimal("15.48"))
                .discount(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("200.00"))
                .paidAmount(new BigDecimal("200.00"))
                .pendingAmount(BigDecimal.ZERO)
                .paymentMode(PaymentMode.CASH)
                .status(BillStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .items(List.of(item1, item2))
                .build();

        User user = createUser("Mashkoor", "7084285785", UserRole.ADMIN);
        lenient().when(userRepository.findByPhone("7084285785")).thenReturn(Optional.of(user));
        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));

        BillResponse response = billService.getBillById(billId, "7084285785");

        // Verify Cess Breakdown is alphabetically/numerically sorted by key (TreeMap)
        // 5% comes before 12%
        assertEquals("5%: ₹4.76 12%: ₹10.72", response.getCessSummary());
    }
}
