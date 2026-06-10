package com.shop;

import com.shop.modules.billing.*;
import com.shop.modules.billing.dto.CreateBillRequest;
import com.shop.modules.billing.dto.ReturnItemsRequest;
import com.shop.modules.customer.*;
import com.shop.modules.customer.dto.*;
import com.shop.modules.dashboard.DashboardService;
import com.shop.modules.dashboard.dto.DashboardResponse;
import com.shop.modules.delivery.*;
import com.shop.modules.delivery.RouteOptimizationService.RouteResult;
import com.shop.modules.delivery.RouteOptimizationService.RouteAreaGroup;
import com.shop.modules.delivery.RouteOptimizationService.RouteStop;
import com.shop.modules.expense.Expense;
import com.shop.modules.expense.ExpenseRepository;
import com.shop.modules.damage.DamageLog;
import com.shop.modules.damage.DamageLogRepository;
import com.shop.modules.damage.DamageService;
import com.shop.modules.damage.DamageReason;
import com.shop.modules.damage.dto.LogDamageRequest;
import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.product.ProductService;
import com.shop.modules.product.UnitType;
import com.shop.modules.stock.*;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.modules.area.Area;
import com.shop.modules.area.AreaRepository;
import com.shop.modules.khata.Payment;
import com.shop.modules.khata.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FmcgShopBusinessTests {

    @Mock private StockRepository stockRepository;
    @Mock private StockBatchRepository batchRepository;
    @Mock private ProductRepository productRepository;
    @Mock private BillRepository billRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private DamageLogRepository damageLogRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private AiReminderGenerator aiReminderGenerator;
    @Mock private AreaRepository areaRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private StockAdjustmentLogRepository stockAdjustmentLogRepository;

    // Mocks injected into BillService and DamageService
    @Mock private CustomerService customerServiceMock;
    @Mock private ProductService productServiceMock;
    @Mock private BillService billServiceMock;

    @InjectMocks private StockService stockService;
    @InjectMocks private BillService billService;
    @InjectMocks private DamageService damageService;
    @InjectMocks private DashboardService dashboardService;
    @InjectMocks private CustomerService customerService;
    @InjectMocks private AiReminderService aiReminderService;
    @InjectMocks private RouteOptimizationService routeOptimizationService;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        this.stockService = new StockService(stockRepository, batchRepository, productRepository, stockAdjustmentLogRepository, userRepository, damageLogRepository);
        
        this.damageService = new DamageService(
                damageLogRepository,
                productRepository,
                productServiceMock,
                batchRepository,
                stockService,
                userRepository
        );

        this.billService = new BillService(
                billRepository,
                customerRepository,
                productRepository,
                userRepository,
                stockService,
                stockRepository,
                customerServiceMock,
                productServiceMock,
                batchRepository,
                paymentRepository
        );

        this.customerService = new CustomerService(
                customerRepository,
                null,
                billRepository
        );

        this.aiReminderService = new AiReminderService(
                customerServiceMock,
                aiReminderGenerator
        );

        this.dashboardService = new DashboardService(
                billRepository,
                customerRepository,
                productRepository,
                batchRepository,
                stockRepository,
                deliveryRepository,
                expenseRepository,
                userRepository,
                areaRepository,
                paymentRepository,
                damageLogRepository,
                billServiceMock
        );

        this.routeOptimizationService = new RouteOptimizationService(deliveryRepository, userRepository);
    }

    // Helper to create a dummy Product
    private Product createDummyProduct(UUID id, int secondaryPerPrimary) {
        return Product.builder()
                .id(id)
                .name("Cold Drink")
                .primaryUnit("BOX")
                .secondaryUnit("BOTTLE")
                .secondaryPerPrimary(secondaryPerPrimary)
                .sellPricePrimary(BigDecimal.valueOf(1200))
                .sellPriceSecondary(BigDecimal.valueOf(100))
                .buyPriceWithoutTax(BigDecimal.valueOf(900))
                .gstPercent(BigDecimal.valueOf(18))
                .buyPriceWithTax(BigDecimal.valueOf(1062))
                .lowStockAlert(10)
                .lowStockUnit("SECONDARY")
                .active(true)
                .build();
    }

    // Helper to create a dummy Customer
    private Customer createDummyCustomer(UUID id) {
        return Customer.builder()
                .id(id)
                .name("Super Mart")
                .totalPending(BigDecimal.ZERO)
                .active(true)
                .build();
    }

    // Helper to create a dummy User
    private User createDummyUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .name("Arhaan")
                .phone("9450821033")
                .active(true)
                .build();
    }

    // ── 1. Stock Normalization Tests ──
    @Test
    public void testStockNormalizationOnDeductAndAddBack() {
        UUID productId = UUID.randomUUID();
        Product product = createDummyProduct(productId, 12); // 12 bottles per BOX
        Stock stock = Stock.builder()
                .product(product)
                .totalPrimaryUnits(10)
                .totalSecondaryUnits(120) // 10 boxes initially
                .openPrimaryRemaining(0)
                .hasOpenPrimary(false)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));

        // Deduct 2 secondary units (loose bottles)
        stockService.deductBySecondary(productId, 2);

        // Verify total secondary decreased to 118
        assertEquals(118, stock.getTotalSecondaryUnits());
        // Verify loose units normalized: 118 % 12 = 10 loose bottles
        assertEquals(10, stock.getOpenPrimaryRemaining());
        // Verify sealed boxes normalized: 118 / 12 = 9 sealed boxes
        assertEquals(9, stock.getTotalPrimaryUnits());
        assertTrue(stock.getHasOpenPrimary());

        // Now add back 14 secondary units (e.g. return/cancellation)
        // 118 + 14 = 132 units
        stockService.addBackStock(productId, 0, 14);

        assertEquals(132, stock.getTotalSecondaryUnits());
        // 132 / 12 = 11 sealed boxes, 0 loose units
        assertEquals(11, stock.getTotalPrimaryUnits());
        assertEquals(0, stock.getOpenPrimaryRemaining());
        assertFalse(stock.getHasOpenPrimary());
    }

    // ── 2. Free Quantity Stock Deduction Tests ──
    @Test
    public void testBillingDeductsTotalQuantityIncludingFreeQuantity() {
        // We set up a manual test of the business logic flow
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Product product = createDummyProduct(productId, 12);
        Customer customer = createDummyCustomer(customerId);
        User user = createDummyUser();

        Stock stock = Stock.builder()
                .product(product)
                .totalPrimaryUnits(10)
                .totalSecondaryUnits(120)
                .build();

        // Create bill request: 2 boxes + 1 box free = total 3 boxes to deduct
        CreateBillRequest.BillItemRequest itemReq = new CreateBillRequest.BillItemRequest();
        itemReq.setProductId(productId.toString());
        itemReq.setUnitType(UnitType.BOX);
        itemReq.setQuantity(2);
        itemReq.setFreeQuantity(1);

        CreateBillRequest request = new CreateBillRequest();
        request.setCustomerId(customerId.toString());
        request.setPaymentMode(PaymentMode.CASH);
        request.setItems(Collections.singletonList(itemReq));
        request.setDiscount(BigDecimal.ZERO);

        StockBatch batch = StockBatch.builder()
                .product(product)
                .batchNumber("B-901")
                .secondaryReceived(120)
                .secondaryRemaining(120)
                .buyPriceWithoutTax(BigDecimal.valueOf(900))
                .build();

        // Inject mocks for BillService (using service-level mocks since BillService now calls services not repos)
        when(customerServiceMock.findCustomerByIdentifier(customerId.toString())).thenReturn(customer);
        when(userRepository.findByPhone("9450821033")).thenReturn(Optional.of(user));
        when(productServiceMock.findProductByIdentifier(productId.toString())).thenReturn(product);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));
        when(batchRepository.findActiveBatchesFIFO(productId)).thenReturn(Collections.singletonList(batch));
        when(batchRepository.findByProductId(productId)).thenReturn(Collections.singletonList(batch));
        when(billRepository.findMaxBillSequence()).thenReturn(0);
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Create the bill
        billService.createBill(request, "9450821033");

        // Verify that total stock decreased by 3 boxes (36 secondary units)
        // 120 - 36 = 84
        assertEquals(84, stock.getTotalSecondaryUnits());
        assertEquals(7, stock.getTotalPrimaryUnits());
    }

    // ── 3. LIFO Batch Restoration Tests ──
    @Test
    public void testLIFOBatchRestorationOnCancel() {
        UUID productId = UUID.randomUUID();
        Product product = createDummyProduct(productId, 12);

        // 2 batches received in order
        StockBatch batch1 = StockBatch.builder()
                .product(product)
                .secondaryReceived(12)
                .secondaryRemaining(0) // completely sold
                .receivedAt(java.time.LocalDateTime.now().minusDays(2))
                .exhausted(true)
                .build();

        StockBatch batch2 = StockBatch.builder()
                .product(product)
                .secondaryReceived(12)
                .secondaryRemaining(4) // partially sold (8 sold)
                .receivedAt(java.time.LocalDateTime.now().minusDays(1))
                .exhausted(false)
                .build();

        when(batchRepository.findByProductIdOrderByReceivedAtDesc(productId))
                .thenReturn(Arrays.asList(batch2, batch1));

        // Restore 10 secondary units
        stockService.restoreStockToBatches(productId, 10);

        // LIFO order: batch2 is checked first (capacity to restore = 12 - 4 = 8)
        // It should restore 8 units to batch2, making it full (12 remaining)
        assertEquals(12, batch2.getSecondaryRemaining());
        assertFalse(batch2.getExhausted());

        // Remaining 2 units restored to batch1 (capacity = 12 - 0 = 12)
        // It should restore 2 units to batch1, leaving 2 remaining
        assertEquals(2, batch1.getSecondaryRemaining());
        assertFalse(batch1.getExhausted());
    }

    // ── 4. Damage Logging Value Loss & Stock Deductions ──
    @Test
    public void testDamageLoggingForSecondaryUnits() {
        UUID productId = UUID.randomUUID();
        Product product = createDummyProduct(productId, 12); // ₹1062 buy price with tax
        User user = createDummyUser();

        // Requesting 3 BOTTLEs (secondary unit) of damage
        LogDamageRequest req = new LogDamageRequest();
        req.setProductId(productId.toString());
        req.setUnitType(UnitType.BOTTLE);
        req.setQuantity(3);
        req.setReason(DamageReason.EXPIRE);

        Stock stock = Stock.builder()
                .product(product)
                .totalSecondaryUnits(100)
                .build();
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));
        when(productServiceMock.findProductByIdentifier(productId.toString())).thenReturn(product);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(userRepository.findByPhone("9450821033")).thenReturn(Optional.of(user));
        when(damageLogRepository.save(any(DamageLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Let's capture the saved DamageLog
        ArgumentCaptor<DamageLog> logCaptor = ArgumentCaptor.forClass(DamageLog.class);

        damageService.logDamage(req, "9450821033");

        verify(damageLogRepository).save(logCaptor.capture());
        DamageLog savedLog = logCaptor.getValue();

        // 1 primary unit buy price (without tax) = ₹900
        // Price per secondary unit = 900 / 12 = 75
        // Value loss for 3 secondary units = 75 * 3 = 225
        assertEquals(new BigDecimal("225.00"), savedLog.getValueLoss());
    }

    @Test
    public void testDamageLoggingForSingleUnitsAndClaims() {
        UUID productId = UUID.randomUUID();
        Product product = createDummyProduct(productId, 12); // ₹1062 buy price with tax
        product.setSecondaryUnit("LADI"); // testing divisor 10
        User user = createDummyUser();

        // Requesting 3 packets/single units of chips
        LogDamageRequest req = new LogDamageRequest();
        req.setProductId(productId.toString());
        req.setUnitLevel(com.shop.modules.damage.UnitLevel.SINGLE);
        req.setClaimStatus(com.shop.modules.damage.ClaimStatus.CLAIMABLE);
        req.setQuantity(3);
        req.setReason(DamageReason.CRUSH);

        Stock stock = Stock.builder()
                .product(product)
                .totalSecondaryUnits(100)
                .build();
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));
        when(productServiceMock.findProductByIdentifier(productId.toString())).thenReturn(product);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(userRepository.findByPhone("9450821033")).thenReturn(Optional.of(user));
        when(damageLogRepository.save(any(DamageLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<DamageLog> logCaptor = ArgumentCaptor.forClass(DamageLog.class);

        damageService.logDamage(req, "9450821033");

        verify(damageLogRepository).save(logCaptor.capture());
        DamageLog savedLog = logCaptor.getValue();

        // 1 primary unit buy price (without tax) = 900
        // Price per secondary unit (LADI) = 900 / 12 = 75
        // Price per single unit (packet) = 75 / 10 = 7.5
        // Value loss for 3 packets = 7.5 * 3 = 22.5
        assertEquals(0, new BigDecimal("22.50").compareTo(savedLog.getValueLoss()));
        assertEquals(com.shop.modules.damage.UnitLevel.SINGLE, savedLog.getUnitLevel());
        assertEquals(com.shop.modules.damage.ClaimStatus.CLAIMABLE, savedLog.getClaimStatus());
    }

    // ── 5. Specific Batch Selection Test ──
    @Test
    public void testCreateBillWithSpecificBatch() {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Product product = createDummyProduct(productId, 12);
        Customer customer = createDummyCustomer(customerId);
        User user = createDummyUser();

        StockBatch batch = StockBatch.builder()
                .id(batchId)
                .product(product)
                .batchNumber("B-902")
                .secondaryReceived(24)
                .secondaryRemaining(24)
                .buyPriceWithoutTax(BigDecimal.valueOf(900))
                .build();

        Stock stock = Stock.builder()
                .product(product)
                .totalPrimaryUnits(2)
                .totalSecondaryUnits(24)
                .build();

        CreateBillRequest.BillItemRequest itemReq = new CreateBillRequest.BillItemRequest();
        itemReq.setProductId(productId.toString());
        itemReq.setBatchId(batchId);
        itemReq.setUnitType(UnitType.BOTTLE);
        itemReq.setQuantity(5);
        itemReq.setFreeQuantity(0);

        CreateBillRequest request = new CreateBillRequest();
        request.setCustomerId(customerId.toString());
        request.setPaymentMode(PaymentMode.CASH);
        request.setItems(Collections.singletonList(itemReq));

        when(customerServiceMock.findCustomerByIdentifier(customerId.toString())).thenReturn(customer);
        when(userRepository.findByPhone("9450821033")).thenReturn(Optional.of(user));
        when(productServiceMock.findProductByIdentifier(productId.toString())).thenReturn(product);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));
        when(stockRepository.save(any(Stock.class))).thenReturn(stock);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(batchRepository.findByProductId(productId)).thenReturn(Collections.singletonList(batch));
        when(billRepository.findMaxBillSequence()).thenReturn(0);
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        billService.createBill(request, "9450821033");

        // Verify stock deducted from specific batch (24 - 5 = 19)
        assertEquals(19, batch.getSecondaryRemaining());
        // Verify total secondary stock deducted
        assertEquals(19, stock.getTotalSecondaryUnits());
    }

    // ── 6. Partial Returns Test ──
    @Test
    public void testPartialReturnsAndCreditNotes() {
        UUID billId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID billItemId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        Product product = createDummyProduct(productId, 12);
        Customer customer = createDummyCustomer(customerId);
        customer.setTotalPending(BigDecimal.valueOf(1180));

        StockBatch batch = StockBatch.builder()
                .id(batchId)
                .product(product)
                .secondaryReceived(12)
                .secondaryRemaining(2) // 10 sold
                .buyPriceWithoutTax(BigDecimal.valueOf(900))
                .build();

        Stock stock = Stock.builder()
                .product(product)
                .totalPrimaryUnits(0)
                .totalSecondaryUnits(2)
                .build();

        BillItem item = BillItem.builder()
                .id(billItemId)
                .product(product)
                .batch(batch)
                .unitType(UnitType.BOTTLE)
                .quantity(10)
                .freeQuantity(0)
                .rate(BigDecimal.valueOf(100))
                .gstPercent(BigDecimal.valueOf(18))
                .gstAmount(BigDecimal.valueOf(180))
                .total(BigDecimal.valueOf(1180))
                .build();

        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-0001")
                .customer(customer)
                .subtotal(BigDecimal.valueOf(1000))
                .gstTotal(BigDecimal.valueOf(180))
                .grandTotal(BigDecimal.valueOf(1180))
                .paidAmount(BigDecimal.ZERO)
                .pendingAmount(BigDecimal.valueOf(1180))
                .paymentMode(PaymentMode.UDHAR)
                .status(BillStatus.CONFIRMED)
                .items(new ArrayList<>(Collections.singletonList(item)))
                .build();

        item.setBill(bill);

        ReturnItemsRequest.ReturnedItemRequest returnItem = new ReturnItemsRequest.ReturnedItemRequest();
        returnItem.setBillItemId(billItemId);
        returnItem.setQuantityToReturn(3);

        ReturnItemsRequest request = new ReturnItemsRequest();
        request.setReturnedItems(Collections.singletonList(returnItem));

        when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        when(billRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)).thenReturn(Collections.singletonList(bill));
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(billRepository.save(any(Bill.class))).thenReturn(bill);

        billService.returnItems(billId, request);

        // Refund proportional: 1180 / 10 = 118 per unit. 118 * 3 = 354 refund.
        // New grand total = 1180 - 354 = 826
        assertEquals(new BigDecimal("826.00"), bill.getGrandTotal());
        assertEquals(7, item.getQuantity());

        // Stock restored: 2 + 3 = 5
        assertEquals(5, stock.getTotalSecondaryUnits());
        // Batch stock restored: 2 + 3 = 5
        assertEquals(5, batch.getSecondaryRemaining());

        // Customer debt reduced: 1180 - 354 = 826
        assertEquals(new BigDecimal("826.00"), customer.getTotalPending());
    }

    // ── 7. True Profit-Loss COGS Test ──
    @Test
    public void testCogsCalculationInDashboardSummary() {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        Product product = createDummyProduct(productId, 12);
        Customer customer = createDummyCustomer(customerId);

        StockBatch batch = StockBatch.builder()
                .id(batchId)
                .product(product)
                .secondaryReceived(12)
                .secondaryRemaining(2)
                .buyPriceWithoutTax(BigDecimal.valueOf(900)) // buy price per sec = 900 / 12 = 75
                .build();

        BillItem item = BillItem.builder()
                .product(product)
                .batch(batch)
                .unitType(UnitType.BOTTLE)
                .quantity(10)
                .freeQuantity(0)
                .rate(BigDecimal.valueOf(100))
                .total(BigDecimal.valueOf(1180))
                .build();

        Bill bill = Bill.builder()
                .customer(customer)
                .grandTotal(BigDecimal.valueOf(1180))
                .status(BillStatus.CONFIRMED)
                .items(Collections.singletonList(item))
                .build();

        Expense expense = Expense.builder()
                .amount(BigDecimal.valueOf(100))
                .category(com.shop.modules.expense.ExpenseCategory.RENT)
                .build();

        // Mocks for DashboardService
        when(billRepository.findBillsBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(bill));
        when(expenseRepository.findBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(expense));
        
        when(productRepository.findLowStockProducts()).thenReturn(Collections.emptyList());
        when(batchRepository.findExpiringBefore(any(LocalDate.class))).thenReturn(Collections.emptyList());
        when(customerRepository.findInactiveCustomers(any(LocalDateTime.class))).thenReturn(Collections.emptyList());
        when(deliveryRepository.findByStatus(any())).thenReturn(Collections.emptyList());

        DashboardResponse response = dashboardService.getTodaySummary();

        // True net profit: monthRevenue (1180) - cogs (10 * 75 = 750) - expenses (100) = 330
        assertEquals(0, BigDecimal.valueOf(330).compareTo(response.getMonthNetProfit()));
    }

    // ── 8. NPA Credit Lock Tests ──
    @Test
    public void testNpaCreditLockBlocksCreditBilling() {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Product product = createDummyProduct(productId, 12);
        Customer customer = createDummyCustomer(customerId);
        customer.setIsNpa(true); // Flag customer as NPA defaulter
        User user = createDummyUser();

        CreateBillRequest.BillItemRequest itemReq = new CreateBillRequest.BillItemRequest();
        itemReq.setProductId(productId.toString());
        itemReq.setUnitType(UnitType.BOX);
        itemReq.setQuantity(1);
        itemReq.setFreeQuantity(0);

        // Try creating bill in UDHAR mode
        CreateBillRequest udharRequest = new CreateBillRequest();
        udharRequest.setCustomerId(customerId.toString());
        udharRequest.setPaymentMode(PaymentMode.UDHAR);
        udharRequest.setItems(Collections.singletonList(itemReq));

        when(customerServiceMock.findCustomerByIdentifier(customerId.toString())).thenReturn(customer);
        when(userRepository.findByPhone("9450821033")).thenReturn(Optional.of(user));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            billService.createBill(udharRequest, "9450821033");
        });
        assertTrue(exception.getMessage().contains("Credit sales are blocked for NPA customer"));
    }

    // ── 9. Auto-NPA Scanner Tests ──
    @Test
    public void testAutoNpaScannerTagsOverdueBalances() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Customer customerA = Customer.builder().id(id1).name("Defaulter Shop").isNpa(false).active(true).build();
        Customer customerB = Customer.builder().id(id2).name("Good Shop").isNpa(true).active(true).build();

        List<Customer> allCustomers = Arrays.asList(customerA, customerB);
        List<Customer> overdueCustomers = Collections.singletonList(customerA);

        when(customerRepository.findByActiveTrue()).thenReturn(allCustomers);
        when(billRepository.findCustomersWithOverdueBills(any(java.time.LocalDateTime.class)))
                .thenReturn(overdueCustomers);

        customerService.scanAndMarkNpaCustomers();

        // Customer A has overdue bills -> should be marked as NPA = true
        assertTrue(customerA.getIsNpa());
        // Customer B has no overdue bills -> should be unflagged to NPA = false
        assertFalse(customerB.getIsNpa());

        // Verify that updates were saved
        verify(customerRepository, atLeastOnce()).save(customerA);
        verify(customerRepository, atLeastOnce()).save(customerB);
    }

    // ── 10. AI Hinglish Reminder Fallback & Formatting ──
    @Test
    public void testAiReminderFallbackAndFormatting() {
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(customerId)
                .name("Ramesh ji")
                .shopName("Ramesh Kirana Store")
                .phone("9876543210")
                .totalPending(BigDecimal.valueOf(2500))
                .active(true)
                .build();

        when(customerServiceMock.findCustomerByIdentifier(customerId.toString())).thenReturn(customer);
        
        // Mock the cached generator to return our Hinglish message
        String expectedMessage = "Pranam Ramesh ji (Ramesh Kirana Store), outstanding balance is Rs. 2500.";
        when(aiReminderGenerator.generateReminderMessage(
                eq(customerId),
                eq(BigDecimal.valueOf(2500)),
                anyString(),
                eq("Ramesh ji"),
                eq("Ramesh Kirana Store")
        )).thenReturn(expectedMessage);

        AiReminderResponse response = aiReminderService.generateCustomerReminder(customerId.toString());

        assertEquals(expectedMessage, response.getMessage());
        // Verify WhatsApp link matches the phone and contains the urlencoded message
        assertTrue(response.getWhatsappLink().contains("919876543210"));
        assertTrue(response.getWhatsappLink().contains("text="));
    }

    // ── 11. Credit Limit Validation & Transaction Rollback Test ──
    @Test
    public void testCreditLimitValidationBlocksOverLimitCreditBilling() {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Product product = createDummyProduct(productId, 12);
        Customer customer = createDummyCustomer(customerId);
        customer.setTotalPending(BigDecimal.valueOf(4000));
        
        User user = createDummyUser();

        CreateBillRequest.BillItemRequest itemReq = new CreateBillRequest.BillItemRequest();
        itemReq.setProductId(productId.toString());
        itemReq.setUnitType(UnitType.BOX);
        itemReq.setQuantity(1);
        itemReq.setFreeQuantity(0);

        CreateBillRequest request = new CreateBillRequest();
        request.setCustomerId(customerId.toString());
        request.setPaymentMode(PaymentMode.UDHAR);
        request.setItems(Collections.singletonList(itemReq));

        Stock stock = Stock.builder()
                .product(product)
                .totalPrimaryUnits(10)
                .totalSecondaryUnits(120)
                .build();

        StockBatch batch = StockBatch.builder()
                .product(product)
                .batchNumber("B-901")
                .secondaryReceived(120)
                .secondaryRemaining(120)
                .buyPriceWithoutTax(BigDecimal.valueOf(900))
                .build();

        when(customerServiceMock.findCustomerByIdentifier(customerId.toString())).thenReturn(customer);
        when(userRepository.findByPhone("9450821033")).thenReturn(Optional.of(user));
        when(productServiceMock.findProductByIdentifier(productId.toString())).thenReturn(product);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));
        when(batchRepository.findActiveBatchesFIFO(productId)).thenReturn(Collections.singletonList(batch));
        when(batchRepository.findByProductId(productId)).thenReturn(Collections.singletonList(batch));
        
        // Mock dynamic effective limit to ₹5000
        when(customerServiceMock.calculateEffectiveCreditLimit(customer)).thenReturn(BigDecimal.valueOf(5000));

        // Attempting to create the bill should throw a credit limit exceeded exception
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            billService.createBill(request, "9450821033");
        });
        assertTrue(exception.getMessage().contains("Credit limit exceeded for customer"));
        
        // Assert that the customer's totalPending is STILL ₹4000 (no changes saved)
        assertEquals(BigDecimal.valueOf(4000), customer.getTotalPending());
    }

    // ── 12. Credit Scoring Logic Tests ──
    @Test
    public void testCalculateEffectiveCreditLimit_NewCustomer_DefaultZero() {
        Customer customer = Customer.builder()
                .id(UUID.randomUUID())
                .name("New Shop")
                .createdAt(LocalDateTime.now()) // brand new account
                .creditLimit(null) // follow auto rules
                .build();
        
        when(billRepository.sumPaidAmountByCustomerId(customer.getId())).thenReturn(BigDecimal.ZERO);
        
        BigDecimal limit = customerService.calculateEffectiveCreditLimit(customer);
        assertEquals(0, BigDecimal.ZERO.compareTo(limit));
    }

    @Test
    public void testCalculateEffectiveCreditLimit_LoyalCustomer_AutoUnlocked() {
        Customer customer = Customer.builder()
                .id(UUID.randomUUID())
                .name("Loyal Shop")
                .createdAt(LocalDateTime.now().minusDays(35)) // account > 30 days old
                .creditLimit(null) // follow auto rules
                .build();
        
        when(billRepository.sumPaidAmountByCustomerId(customer.getId())).thenReturn(BigDecimal.valueOf(30000)); // sales > 25000
        
        BigDecimal limit = customerService.calculateEffectiveCreditLimit(customer);
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(limit));
    }

    @Test
    public void testCalculateEffectiveCreditLimit_AdminOverride_BypassesCriteria() {
        Customer customer = Customer.builder()
                .id(UUID.randomUUID())
                .name("New Trust Shop")
                .createdAt(LocalDateTime.now()) // brand new account
                .creditLimit(BigDecimal.valueOf(15000)) // manually override limit
                .build();
        
        BigDecimal limit = customerService.calculateEffectiveCreditLimit(customer);
        assertEquals(0, BigDecimal.valueOf(15000).compareTo(limit));
    }

    // ── 13. Route Optimization Tests ──
    @Test
    public void testRouteOptimization_NearestNeighborAndAreaClustering() {
        UUID deliveryBoyId = UUID.randomUUID();
        
        // Define some Areas
        Area areaA = Area.builder().id(UUID.randomUUID()).name("Sector A").build();
        Area areaB = Area.builder().id(UUID.randomUUID()).name("Sector B").build();
        
        // Customers with specific coordinates
        Customer customerA1 = Customer.builder()
                .id(UUID.randomUUID())
                .name("Shop A1")
                .shopName("Shop A1 Ltd")
                .phone("1234567890")
                .latitude(28.6139)
                .longitude(77.2090)
                .area(areaA)
                .build();
                
        Customer customerA2 = Customer.builder()
                .id(UUID.randomUUID())
                .name("Shop A2")
                .shopName("Shop A2 Ltd")
                .phone("1234567891")
                .latitude(28.6150)
                .longitude(77.2100)
                .area(areaA)
                .build();

        Customer customerA3 = Customer.builder()
                .id(UUID.randomUUID())
                .name("Shop A3 No GPS")
                .shopName("Shop A3 Ltd")
                .area(areaA)
                .latitude(null)
                .longitude(null)
                .build();
                
        Customer customerB1 = Customer.builder()
                .id(UUID.randomUUID())
                .name("Shop B1")
                .shopName("Shop B1 Ltd")
                .phone("1234567892")
                .latitude(28.7041)
                .longitude(77.1025)
                .area(areaB)
                .build();

        Bill billA1 = Bill.builder().billNumber("BILL-A1").customer(customerA1).pendingAmount(BigDecimal.valueOf(100)).build();
        Bill billA2 = Bill.builder().billNumber("BILL-A2").customer(customerA2).pendingAmount(BigDecimal.valueOf(200)).build();
        Bill billA3 = Bill.builder().billNumber("BILL-A3").customer(customerA3).pendingAmount(BigDecimal.valueOf(300)).build();
        Bill billB1 = Bill.builder().billNumber("BILL-B1").customer(customerB1).pendingAmount(BigDecimal.valueOf(400)).build();

        Delivery delA1 = Delivery.builder().id(UUID.randomUUID()).bill(billA1).status(DeliveryStatus.PENDING).build();
        Delivery delA2 = Delivery.builder().id(UUID.randomUUID()).bill(billA2).status(DeliveryStatus.PACKED).build();
        Delivery delA3 = Delivery.builder().id(UUID.randomUUID()).bill(billA3).status(DeliveryStatus.PENDING).build();
        Delivery delB1 = Delivery.builder().id(UUID.randomUUID()).bill(billB1).status(DeliveryStatus.PENDING).build();

        List<Delivery> deliveries = List.of(delA1, delA2, delA3, delB1);
        
        when(deliveryRepository.findByDeliveryBoyIdAndStatusIn(
                eq(deliveryBoyId),
                anyList()
        )).thenReturn(deliveries);

        RouteResult result = routeOptimizationService.optimizeRoute(deliveryBoyId);

        // Verify grouping and sorting
        assertEquals(deliveryBoyId, result.getDeliveryBoyId());
        assertEquals(4, result.getTotalStops());
        assertEquals(2, result.getAreaGroups().size());

        // Check Group 1 (Sector A)
        RouteAreaGroup groupA = result.getAreaGroups().stream()
                .filter(g -> g.getAreaName().equals("Sector A"))
                .findFirst().orElseThrow();
        assertEquals(3, groupA.getStopCount());
        
        List<RouteStop> stopsA = groupA.getStops();
        assertEquals("Shop A1", stopsA.get(0).getCustomerName());
        assertEquals("Shop A2", stopsA.get(1).getCustomerName());
        assertEquals("Shop A3 No GPS", stopsA.get(2).getCustomerName());
        
        assertTrue(stopsA.get(0).isHasLocation());
        assertTrue(stopsA.get(1).isHasLocation());
        assertFalse(stopsA.get(2).isHasLocation());

        int firstStopNum = stopsA.get(0).getStopNumber();
        assertEquals(firstStopNum + 1, stopsA.get(1).getStopNumber());
        assertEquals(firstStopNum + 2, stopsA.get(2).getStopNumber());
    }

    // ── 14. Custom Hinglish Reminder Template Fallback & Formatting Tests ──
    @Test
    public void testAiReminderGeneratorFallbackAndFormatting() {
        class TestReminderGenerator extends com.shop.modules.customer.AiReminderGenerator {
            public String testFallback(String name, String shopName, BigDecimal pendingAmount) {
                return generateLocalFallback(name, shopName, pendingAmount);
            }
        }
        TestReminderGenerator generator = new TestReminderGenerator();
        String message = generator.testFallback("Mahfooz", "Lari Traders Store", BigDecimal.valueOf(34579.40));

        String expected = "Mahfooz Ji (Lari Traders Store),\n\n" +
                "Lari Traders ki taraf se namaskar.\n\n" +
                "Hamare records ke anusaar aapka outstanding balance ₹34,579.40 hai. Kripya is baki rashi ka bhugtan jald se jald karne ka kasht karein, taaki vyavsayik len-den sughar roop se jaari rahe.\n\n" +
                "Yadi payment kar diya gaya hai, kripya is sandesh ko nazarandaaz karein. Kisi bhi prakar ki jankari ya sahayata ke liye hume sampark karein.\n\n" +
                "Dhanyavaad.\n\n" +
                "Lari Traders\n" +
                "📞 8707867040";

        assertEquals(expected, message);
    }

    @Test
    public void testAiReminderGeneratorFallbackNoShopName() {
        class TestReminderGenerator extends com.shop.modules.customer.AiReminderGenerator {
            public String testFallback(String name, String shopName, BigDecimal pendingAmount) {
                return generateLocalFallback(name, shopName, pendingAmount);
            }
        }
        TestReminderGenerator generator = new TestReminderGenerator();
        String message = generator.testFallback("Mahfooz", "", BigDecimal.valueOf(12500));

        String expected = "Mahfooz Ji,\n\n" +
                "Lari Traders ki taraf se namaskar.\n\n" +
                "Hamare records ke anusaar aapka outstanding balance ₹12,500.00 hai. Kripya is baki rashi ka bhugtan jald se jald karne ka kasht karein, taaki vyavsayik len-den sughar roop se jaari rahe.\n\n" +
                "Yadi payment kar diya gaya hai, kripya is sandesh ko nazarandaaz karein. Kisi bhi prakar ki jankari ya sahayata ke liye hume sampark karein.\n\n" +
                "Dhanyavaad.\n\n" +
                "Lari Traders\n" +
                "📞 8707867040";

        assertEquals(expected, message);
    }
}


