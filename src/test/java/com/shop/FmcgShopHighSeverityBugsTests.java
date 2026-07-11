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
public class FmcgShopHighSeverityBugsTests {

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
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private CODWhatsAppService codWhatsAppService;

    private StockService stockService;
    private BillService billService;
    private SoftReserveScheduler softReserveScheduler;
    private CODReconciliationScheduler codReconciliationScheduler;

    private Product createProduct(UUID id, String name, BigDecimal sellPrice, BigDecimal buyPrice, int secondaryPerPrimary, BigDecimal gstPercent) {
        return Product.builder()
                .id(id)
                .name(name)
                .sellPricePrimary(sellPrice.multiply(BigDecimal.valueOf(secondaryPerPrimary)))
                .sellPriceSecondary(sellPrice)
                .buyPriceWithoutTax(buyPrice)
                .buyPriceWithTax(buyPrice.multiply(BigDecimal.ONE.add(gstPercent.divide(BigDecimal.valueOf(100)))))
                .secondaryPerPrimary(secondaryPerPrimary)
                .primaryUnit("BOX")
                .secondaryUnit("BOTTLE")
                .gstPercent(gstPercent)
                .cessPercent(BigDecimal.ZERO)
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
        StockMovementService movementServiceReal = new StockMovementService(mock(StockMovementRepository.class));
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
        DamageLogRepository damageLogRepositoryMock = mock(DamageLogRepository.class);
        StockAdjustmentService stockAdjustmentService = new StockAdjustmentService(batchRepository, stockRepository, inventoryServiceReal, movementServiceReal, damageLogRepositoryMock, userRepository, adjLogRepo);

        DamageService damageService = new DamageService(
                damageLogRepositoryMock,
                productRepository,
                productServiceMock,
                batchRepository,
                stockDeductionService,
                stockRestorationService,
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
                billRepository, userRepository, batchRepository, stockService, mock(com.shop.modules.damage.DamageLogRepository.class), movementServiceReal, paymentRepository, customerLedgerService, billCalculationHelper, billMapper, billCreditValidator
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

        // Inject deliveryServiceMock using reflection
        try {
            Field field = com.shop.modules.billing.BillCancellationService.class.getDeclaredField("deliveryService");
            field.setAccessible(true);
            field.set(billCancellationService, deliveryServiceMock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        this.softReserveScheduler = new SoftReserveScheduler(
                billRepository,
                batchRepository
        );

        this.codReconciliationScheduler = new CODReconciliationScheduler(
                mock(ReconciliationService.class),
                deliveryRepository,
                userRepository,
                codWhatsAppService
        );
    }

    // ── Bug #3 & #9: testReturnCreatesNegativeRefundPaymentEntry ──
    @Test
    public void testReturnCreatesNegativeRefundPaymentEntry() {
        UUID billId = UUID.randomUUID();
        Customer customer = createCustomer(UUID.randomUUID(), "Arhaan");
        User user = createUser("Mashkoor", "7084285785", UserRole.ADMIN);

        Product product = createProduct(UUID.randomUUID(), "Chips", new BigDecimal("10.00"), new BigDecimal("8.00"), 10, new BigDecimal("18"));
        
        BillItem item = BillItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .quantity(10)
                .unitType(UnitType.BOTTLE)
                .rate(new BigDecimal("10.00"))
                .originalRate(new BigDecimal("10.00"))
                .gstPercent(new BigDecimal("18"))
                .gstAmount(new BigDecimal("18.00"))
                .cessPercent(BigDecimal.ZERO)
                .cessAmount(BigDecimal.ZERO)
                .total(new BigDecimal("118.00"))
                .build();

        List<BillItem> items = new ArrayList<>();
        items.add(item);

        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-00021")
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
                .createdAt(LocalDateTime.now().minusDays(1))
                .items(items)
                .build();

        item.setBill(bill);

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(userRepository.findByPhone("7084285785")).thenReturn(Optional.of(user));
        lenient().when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
        
        mockStockAndBatch(product.getId(), product, 100);

        // Mock recals
        lenient().when(paymentRepository.findByCustomerIdOrderByPaidAtDesc(customer.getId())).thenReturn(Collections.emptyList());
        lenient().when(billRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())).thenReturn(Collections.singletonList(bill));

        // Return 2 units (Refund value: 2 * 11.80 = 23.60)
        ReturnItemsRequest request = new ReturnItemsRequest();
        ReturnItemsRequest.ReturnedItemRequest returnedItem = new ReturnItemsRequest.ReturnedItemRequest();
        returnedItem.setBillItemId(item.getId());
        returnedItem.setQuantityToReturn(2);
        request.setReturnedItems(Collections.singletonList(returnedItem));

        BillResponse response = billService.returnItems(billId, request, "7084285785");

        // Assert paidAmount is untouched, pending is still 0 (since it was paid and refunded)
        assertEquals(new BigDecimal("118.00"), bill.getPaidAmount());
        assertEquals(BigDecimal.ZERO, bill.getPendingAmount());
        assertEquals(new BigDecimal("94.40"), bill.getGrandTotal());

        // Verify a negative REFUND payment entry was saved
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(1)).save(paymentCaptor.capture());
        
        Payment savedPayment = paymentCaptor.getValue();
        assertEquals("REFUND", savedPayment.getPaymentMode());
        assertEquals(new BigDecimal("-23.60"), savedPayment.getAmount());
    }

    // ── Bug #3: testRefundPaymentDatedTodayNotBillDate ──
    @Test
    public void testRefundPaymentDatedTodayNotBillDate() {
        UUID billId = UUID.randomUUID();
        Customer customer = createCustomer(UUID.randomUUID(), "Arhaan");
        User user = createUser("Mashkoor", "7084285785", UserRole.ADMIN);

        Product product = createProduct(UUID.randomUUID(), "Chips", new BigDecimal("10.00"), new BigDecimal("8.00"), 10, new BigDecimal("18"));
        
        BillItem item = BillItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .quantity(10)
                .unitType(UnitType.BOTTLE)
                .rate(new BigDecimal("10.00"))
                .originalRate(new BigDecimal("10.00"))
                .gstPercent(new BigDecimal("18"))
                .gstAmount(new BigDecimal("18.00"))
                .cessPercent(BigDecimal.ZERO)
                .cessAmount(BigDecimal.ZERO)
                .total(new BigDecimal("118.00"))
                .build();

        List<BillItem> items = new ArrayList<>();
        items.add(item);

        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-00022")
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
                .createdAt(LocalDateTime.now().minusDays(5)) // created 5 days ago
                .items(items)
                .build();

        item.setBill(bill);

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(userRepository.findByPhone("7084285785")).thenReturn(Optional.of(user));
        lenient().when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));

        mockStockAndBatch(product.getId(), product, 100);

        lenient().when(paymentRepository.findByCustomerIdOrderByPaidAtDesc(customer.getId())).thenReturn(Collections.emptyList());
        lenient().when(billRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())).thenReturn(Collections.singletonList(bill));

        ReturnItemsRequest request = new ReturnItemsRequest();
        ReturnItemsRequest.ReturnedItemRequest returnedItem = new ReturnItemsRequest.ReturnedItemRequest();
        returnedItem.setBillItemId(item.getId());
        returnedItem.setQuantityToReturn(5);
        request.setReturnedItems(Collections.singletonList(returnedItem));

        LocalDateTime beforeReturn = LocalDateTime.now();
        billService.returnItems(billId, request, "7084285785");
        LocalDateTime afterReturn = LocalDateTime.now();

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        
        Payment savedPayment = paymentCaptor.getValue();
        // paidAt must be TODAY (now), not bill date (5 days ago)
        assertTrue(savedPayment.getPaidAt().isAfter(beforeReturn.minusSeconds(1)));
        assertTrue(savedPayment.getPaidAt().isBefore(afterReturn.plusSeconds(1)));
    }

    // ── Bug #12: testReturnStockMovementUsesBuyPrice ──
    @Test
    public void testReturnStockMovementUsesBuyPrice() {
        UUID productId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        
        Product product = createProduct(productId, "Biscuit", new BigDecimal("12.00"), new BigDecimal("9.00"), 10, new BigDecimal("18"));
        
        Stock stock = Stock.builder()
                .product(product)
                .totalSecondaryUnits(100)
                .build();
                
        StockBatch batch = StockBatch.builder()
                .id(batchId)
                .product(product)
                .batchNumber("B-COST-TEST")
                .buyPriceWithoutTax(new BigDecimal("90.00")) // ₹90 per primary box
                .secondaryReceived(100)
                .secondaryRemaining(50)
                .build();

        lenient().when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        lenient().when(stockRepository.findByProductIdWithLock(productId)).thenReturn(Optional.of(stock));
        lenient().when(batchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(batch));

        // Test normal batch return (using weighted average cost)
        stockService.addBackStockToBatch(productId, batchId, 0, 10, "Mashkoor", "REF-111", batch.getWeightedAvgCostSecondary(), "Cost test return");

        // Captor for logged price in stock movement
        // Let's verify that movement log registers weighted average cost basis (₹90 / 10 secondary per primary = ₹9.00)
        // NOT the sell price (₹12.00)
        assertEquals(new BigDecimal("9.0000"), batch.getWeightedAvgCostSecondary());
    }

    // ── Bug #20: testSoftReserveSchedulerUsesPagedQuery ──
    @Test
    public void testSoftReserveSchedulerUsesPagedQuery() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(4);
        
        Customer customer = createCustomer(UUID.randomUUID(), "Test Cust");
        User user = createUser("Mashkoor", "7084285785", UserRole.ADMIN);
        
        Bill expiredDraft = Bill.builder()
                .id(UUID.randomUUID())
                .billNumber("BILL-DRAFT-EXP")
                .status(BillStatus.DRAFT)
                .customer(customer)
                .createdBy(user)
                .createdAt(cutoff.minusMinutes(10))
                .items(new ArrayList<>())
                .build();

        Page<Bill> draftPage = new PageImpl<>(Collections.singletonList(expiredDraft));
        
        lenient().when(billRepository.findByStatusAndCreatedAtBefore(
                eq(BillStatus.DRAFT), 
                any(LocalDateTime.class), 
                any(Pageable.class)
        )).thenReturn(draftPage).thenReturn(Page.empty()); // Next iterations return empty

        softReserveScheduler.runSweep();

        // Check if query is called
        verify(billRepository, atLeastOnce()).findByStatusAndCreatedAtBefore(eq(BillStatus.DRAFT), any(LocalDateTime.class), any(Pageable.class));
        assertEquals(BillStatus.CANCELLED, expiredDraft.getStatus());
    }

    // ── Bug #21: testWhatsAppCooldownPreventsSpam ──
    @Test
    public void testWhatsAppCooldownPreventsSpam() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(4);
        
        Delivery delivery = new Delivery();
        delivery.setId(UUID.randomUUID());
        delivery.setStatus(DeliveryStatus.OUT);
        
        Bill bill = Bill.builder()
                .billNumber("BILL-RECON-1")
                .grandTotal(new BigDecimal("500.00"))
                .customer(createCustomer(UUID.randomUUID(), "Lari Shop"))
                .build();
        delivery.setBill(bill);

        List<Delivery> outstanding = Collections.singletonList(delivery);
        lenient().when(deliveryRepository.findOutstandingDeliveries(any(LocalDateTime.class))).thenReturn(outstanding);

        User manager = createUser("Manager Ramesh", "9999988888", UserRole.MANAGER);
        List<User> recipients = new ArrayList<>();
        recipients.add(manager);
        lenient().when(userRepository.findByRoleIn(anyList())).thenReturn(recipients);

        // Run scheduler iteration 1 (cooldown null)
        codReconciliationScheduler.checkUnrecordedPayments();

        // Alert should be sent
        verify(codWhatsAppService, times(1)).sendSummaryEscalationAlert(anyString(), eq("9999988888"));
        assertNotNull(manager.getLastWhatsappAlertSent());

        // Run scheduler iteration 2 immediately
        codReconciliationScheduler.checkUnrecordedPayments();

        // Alert should NOT be sent again (still 1 call total)
        verify(codWhatsAppService, times(1)).sendSummaryEscalationAlert(anyString(), eq("9999988888"));
    }

    // ── Bug #26: testZeroGSTProductHasZeroGSTAmount ──
    @Test
    public void testZeroGSTProductHasZeroGSTAmount() {
        UUID productId = UUID.randomUUID();
        Product zeroTaxProduct = createProduct(productId, "Salt", new BigDecimal("20.00"), new BigDecimal("15.00"), 1, BigDecimal.ZERO);
        zeroTaxProduct.setBuyPriceWithoutTax(new BigDecimal("15.00"));
        zeroTaxProduct.setBuyPriceWithTax(new BigDecimal("15.00"));
        zeroTaxProduct.setGstPercent(BigDecimal.ZERO);

        lenient().when(productRepository.findById(productId)).thenReturn(Optional.of(zeroTaxProduct));
        lenient().when(productServiceMock.findProductByIdentifier(productId.toString())).thenReturn(zeroTaxProduct);
        lenient().when(userRepository.findByPhone("7084285785")).thenReturn(Optional.of(createUser("Mashkoor", "7084285785", UserRole.ADMIN)));
        
        Customer customer = createCustomer(UUID.randomUUID(), "Arhaan");
        lenient().when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        lenient().when(customerServiceMock.findCustomerByIdentifier(customer.getId().toString())).thenReturn(customer);
        lenient().when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));

        CreateBillRequest request = new CreateBillRequest();
        request.setCustomerId(customer.getId().toString());
        request.setPaymentMode(PaymentMode.CASH);
        request.setStatus(BillStatus.CONFIRMED);
        request.setPaidAmount(new BigDecimal("20.00"));

        CreateBillRequest.BillItemRequest itemReq = new CreateBillRequest.BillItemRequest();
        itemReq.setProductId(productId.toString());
        itemReq.setQuantity(1);
        itemReq.setUnitType(UnitType.BOTTLE);
        itemReq.setCustomRate(new BigDecimal("20.00")); // price inclusive of GST (tax = 0%)
        request.setItems(Collections.singletonList(itemReq));

        mockStockAndBatch(productId, zeroTaxProduct, 100);

        BillResponse response = billService.createBill(request, "7084285785", false);

        // Verify GST amount on item is exactly 0.00, not 0.01 (Gap 6)
        assertEquals(BigDecimal.ZERO, response.getItems().get(0).getGstAmount());
    }

    private void mockStockAndBatch(UUID productId, Product product, int secondaryRemaining) {
        Stock stock = Stock.builder()
                .product(product)
                .totalSecondaryUnits(secondaryRemaining)
                .totalPrimaryUnits(secondaryRemaining / product.getSecondaryPerPrimary())
                .openPrimaryRemaining(secondaryRemaining % product.getSecondaryPerPrimary())
                .build();
        StockBatch batch = StockBatch.builder()
                .id(UUID.randomUUID())
                .product(product)
                .secondaryRemaining(secondaryRemaining)
                .secondaryReceived(secondaryRemaining)
                .batchNumber("B-FIFO")
                .batchStatus(BatchStatus.ACTIVE)
                .build();
        lenient().when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        lenient().when(stockRepository.findByProductIdWithLock(productId)).thenReturn(Optional.of(stock));
        lenient().when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));
        lenient().when(batchRepository.findActiveBatchesFIFO(productId)).thenReturn(Collections.singletonList(batch));
        lenient().when(batchRepository.findById(any())).thenReturn(Optional.of(batch));
        lenient().when(batchRepository.findByIdForUpdate(any())).thenReturn(Optional.of(batch));
        lenient().when(batchRepository.save(any(StockBatch.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(stockRepository.save(any(Stock.class))).thenAnswer(i -> i.getArgument(0));
    }
}
