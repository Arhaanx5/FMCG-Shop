package com.shop;

import com.shop.modules.billing.*;
import com.shop.modules.billing.dto.*;
import com.shop.modules.customer.*;
import com.shop.modules.product.*;
import com.shop.modules.stock.*;
import com.shop.modules.user.*;
import com.shop.modules.delivery.*;
import com.shop.modules.khata.*;
import com.shop.modules.damage.DamageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FmcgShopBugsVerificationTests {

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

    private StockService stockService;
    private BillService billService;

    // Helper to create a product
    private Product createProduct(UUID id, String name, BigDecimal sellPrice, BigDecimal buyPrice, int secondaryPerPrimary) {
        return Product.builder()
                .id(id)
                .name(name)
                .sellPricePrimary(sellPrice.multiply(BigDecimal.valueOf(secondaryPerPrimary)))
                .sellPriceSecondary(sellPrice)
                .buyPriceWithoutTax(buyPrice)
                .buyPriceWithTax(buyPrice.multiply(BigDecimal.valueOf(1.18)))
                .secondaryPerPrimary(secondaryPerPrimary)
                .primaryUnit("BOX")
                .secondaryUnit("BOTTLE")
                .gstPercent(BigDecimal.valueOf(18))
                .cessPercent(BigDecimal.ZERO)
                .active(true)
                .build();
    }

    // Helper to create a customer
    private Customer createCustomer(UUID id, String name) {
        return Customer.builder()
                .id(id)
                .name(name)
                .totalPending(BigDecimal.ZERO)
                .isNpa(false)
                .active(true)
                .build();
    }

    // Helper to create a user
    private User createUser(String name, String phone, UserRole role) {
        return User.builder()
                .id(UUID.randomUUID())
                .name(name)
                .phone(phone)
                .role(role)
                .active(true)
                .build();
    }

    // Helper to mock stock and batch lookup
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

    // Helper to mock customer balance recalculation queries
    private void mockRecalculateCustomerPending(Customer customer, List<Bill> bills) {
        lenient().when(paymentRepository.findByCustomerIdOrderByPaidAtDesc(customer.getId())).thenReturn(Collections.emptyList());
        lenient().when(billRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())).thenReturn(bills);
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

        this.stockService = new StockService(
                stockRepository,
                batchRepository,
                productRepository,
                mock(StockAdjustmentLogRepository.class),
                userRepository,
                mock(DamageLogRepository.class),
                mock(com.shop.modules.expense.ExpenseRepository.class),
                receiveServiceReal,
                movementServiceReal,
                inventoryServiceReal
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
                paymentRepository,
                billEditHistoryRepository,
                mock(com.shop.modules.damage.DamageLogRepository.class),
                movementServiceReal
        );

        // Inject deliveryServiceMock using reflection
        try {
            java.lang.reflect.Field field = BillService.class.getDeclaredField("deliveryService");
            field.setAccessible(true);
            field.set(this.billService, deliveryServiceMock);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject deliveryServiceMock", e);
        }
    }

    // ── Bug #1 — Compounding Tax / Price Mismatch (Edit Bill) ──
    @Test
    public void testEditBillDoesNotCompoundPaidAmount() {
        UUID billId = UUID.randomUUID();
        Customer customer = createCustomer(UUID.randomUUID(), "Ramesh Gupta");
        User user = createUser("Mashkoor", "7084285785", UserRole.ADMIN);

        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-00011")
                .customer(customer)
                .createdBy(user)
                .subtotal(new BigDecimal("1000.00"))
                .gstTotal(new BigDecimal("180.00"))
                .cessTotal(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("1180.00"))
                .paidAmount(new BigDecimal("500.00"))
                .pendingAmount(new BigDecimal("680.00"))
                .paymentMode(PaymentMode.PARTIAL)
                .status(BillStatus.PARTIAL)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(userRepository.findByPhone("7084285785")).thenReturn(Optional.of(user));
        lenient().when(paymentRepository.findByBillIdIn(anyList())).thenReturn(Collections.emptyList());
        lenient().when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));
        mockRecalculateCustomerPending(customer, List.of(bill));

        // Perform edit with no changes to paid amount or items (simulate saving note edit)
        BillResponse response = billService.updateBillDetails(
                billId, PaymentMode.PARTIAL, "Note update", BillStatus.PARTIAL, null, 
                null, null, null, null, false, null, "7084285785"
        );

        // Assert paid amount is preserved and does not double/compound
        assertEquals(new BigDecimal("500.00"), response.getPaidAmount());
        assertEquals(new BigDecimal("680.00"), response.getPendingAmount());
        assertEquals(0, response.getPaidAmount().compareTo(new BigDecimal("500.00")));
    }

    // ── Bug #2 — Role-based Price Override ──
    @Test
    public void testRoleBasedPriceOverrideSalesmanBlocked() {
        UUID productId = UUID.randomUUID();
        Product product = createProduct(productId, "Limca", new BigDecimal("10.00"), new BigDecimal("8.00"), 12);
        User salesman = createUser("Vikram", "9876543210", UserRole.SALESMAN);

        CreateBillRequest.BillItemRequest itemReq = new CreateBillRequest.BillItemRequest();
        itemReq.setProductId(productId.toString());
        itemReq.setUnitType(UnitType.BOTTLE);
        itemReq.setQuantity(5);
        itemReq.setCustomRate(new BigDecimal("0.10")); // Clearly below secondary cost price of ₹0.7867

        CreateBillRequest request = new CreateBillRequest();
        request.setCustomerId(UUID.randomUUID().toString());
        request.setPaymentMode(PaymentMode.CASH);
        request.setItems(Collections.singletonList(itemReq));

        mockStockAndBatch(productId, product, 20);

        lenient().when(customerServiceMock.findCustomerByIdentifier(anyString())).thenReturn(createCustomer(UUID.randomUUID(), "Test"));
        lenient().when(userRepository.findByPhone("9876543210")).thenReturn(Optional.of(salesman));
        lenient().when(productServiceMock.findProductByIdentifier(productId.toString())).thenReturn(product);

        // Salesman rate below cost is blocked -> throws exception
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            billService.createBill(request, "9876543210");
        });
        assertTrue(ex.getMessage().contains("cannot be lower than purchase cost"));
    }

    @Test
    public void testRoleBasedPriceOverrideManagerWarned() {
        UUID productId = UUID.randomUUID();
        Product product = createProduct(productId, "Limca", new BigDecimal("10.00"), new BigDecimal("8.00"), 12);
        User manager = createUser("Amit", "8707867084", UserRole.MANAGER);

        CreateBillRequest.BillItemRequest itemReq = new CreateBillRequest.BillItemRequest();
        itemReq.setProductId(productId.toString());
        itemReq.setUnitType(UnitType.BOTTLE);
        itemReq.setQuantity(5);
        itemReq.setCustomRate(new BigDecimal("0.10")); // Clearly below secondary cost price of ₹0.7867

        CreateBillRequest request = new CreateBillRequest();
        request.setCustomerId(UUID.randomUUID().toString());
        request.setPaymentMode(PaymentMode.CASH);
        request.setItems(Collections.singletonList(itemReq));

        mockStockAndBatch(productId, product, 20);

        lenient().when(customerServiceMock.findCustomerByIdentifier(anyString())).thenReturn(createCustomer(UUID.randomUUID(), "Test"));
        lenient().when(userRepository.findByPhone("8707867084")).thenReturn(Optional.of(manager));
        lenient().when(productServiceMock.findProductByIdentifier(productId.toString())).thenReturn(product);

        // Manager price override without override flag throws BelowCostWarningException
        assertThrows(com.shop.common.BelowCostWarningException.class, () -> {
            billService.createBill(request, "8707867084");
        });
    }

    @Test
    public void testRoleBasedPriceOverrideManagerWithOverrideFlag() {
        UUID productId = UUID.randomUUID();
        Product product = createProduct(productId, "Limca", new BigDecimal("10.00"), new BigDecimal("8.00"), 12);
        User manager = createUser("Amit", "8707867084", UserRole.MANAGER);

        CreateBillRequest.BillItemRequest itemReq = new CreateBillRequest.BillItemRequest();
        itemReq.setProductId(productId.toString());
        itemReq.setUnitType(UnitType.BOTTLE);
        itemReq.setQuantity(5);
        itemReq.setCustomRate(new BigDecimal("0.10")); // Below cost price

        CreateBillRequest request = new CreateBillRequest();
        request.setCustomerId(UUID.randomUUID().toString());
        request.setPaymentMode(PaymentMode.CASH);
        request.setItems(Collections.singletonList(itemReq));

        mockStockAndBatch(productId, product, 20);

        lenient().when(customerServiceMock.findCustomerByIdentifier(anyString())).thenReturn(createCustomer(UUID.randomUUID(), "Test"));
        lenient().when(userRepository.findByPhone("8707867084")).thenReturn(Optional.of(manager));
        lenient().when(productServiceMock.findProductByIdentifier(productId.toString())).thenReturn(product);
        lenient().when(billRepository.save(any(Bill.class))).thenAnswer(i -> i.getArgument(0));

        // Manager price override with override flag succeeds
        assertDoesNotThrow(() -> {
            billService.createBill(request, "8707867084", true); // passing overrideCost = true
        });
    }

    // ── Bug #11 — Batch Deduction Tracking & Returns ──
    @Test
    public void testFifoBatchDeductionAndPreciseReturn() {
        UUID billId = UUID.randomUUID();
        UUID billItemId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Product product = createProduct(UUID.randomUUID(), "Pepsi", new BigDecimal("10.00"), new BigDecimal("8.00"), 12);
        Customer customer = createCustomer(UUID.randomUUID(), "Test Customer");
        customer.setTotalPending(new BigDecimal("100.00"));

        StockBatch batch = StockBatch.builder()
                .id(batchId)
                .product(product)
                .secondaryReceived(20)
                .secondaryRemaining(10) // 10 remaining (10 deducted)
                .batchStatus(BatchStatus.ACTIVE)
                .build();

        BillItem item = BillItem.builder()
                .id(billItemId)
                .product(product)
                .batch(batch)
                .unitType(UnitType.BOTTLE)
                .quantity(10)
                .freeQuantity(0)
                .rate(new BigDecimal("10.00"))
                .gstPercent(BigDecimal.ZERO)
                .gstAmount(BigDecimal.ZERO)
                .cessPercent(BigDecimal.ZERO)
                .cessAmount(BigDecimal.ZERO)
                .total(new BigDecimal("100.00"))
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
                .billNumber("BILL-12345")
                .customer(customer)
                .subtotal(new BigDecimal("100.00"))
                .gstTotal(BigDecimal.ZERO)
                .cessTotal(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("100.00"))
                .paidAmount(BigDecimal.ZERO)
                .pendingAmount(new BigDecimal("100.00"))
                .paymentMode(PaymentMode.UDHAR)
                .status(BillStatus.CONFIRMED)
                .items(new ArrayList<>(Collections.singletonList(item)))
                .build();
        item.setBill(bill);

        ReturnItemsRequest.ReturnedItemRequest returnReq = new ReturnItemsRequest.ReturnedItemRequest();
        returnReq.setBillItemId(billItemId);
        returnReq.setQuantityToReturn(3);

        ReturnItemsRequest req = new ReturnItemsRequest();
        req.setReturnedItems(Collections.singletonList(returnReq));

        mockStockAndBatch(product.getId(), product, 10);

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        lenient().when(batchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(batch));
        lenient().when(billRepository.save(any(Bill.class))).thenReturn(bill);
        mockRecalculateCustomerPending(customer, List.of(bill));

        // Perform return
        billService.returnItems(billId, req, "System");

        // Verify batch stock restored: 10 + 3 = 13 secondary units
        assertEquals(13, batch.getSecondaryRemaining());
    }

    // ── Bug #14 — Transaction Atomicity ──
    @Test
    public void testTransactionAtomicityAnnotation() throws Exception {
        // Assert that the confirmBill method has @Transactional and does NOT have REQUIRES_NEW propagation
        Method method = BillService.class.getMethod("confirmBill", UUID.class, String.class);
        org.springframework.transaction.annotation.Transactional annotation = 
                method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        
        assertNotNull(annotation, "confirmBill method must be annotated with @Transactional");
        assertEquals(org.springframework.transaction.annotation.Propagation.REQUIRED, annotation.propagation(), 
                "Propagation must be REQUIRED (default) to participate in and rollback parent transactions correctly.");
    }

    // ── Bug #15 — Foreign Key Deletion ──
    @Test
    public void testForeignKeyDeletionPreservesPaymentsNullifiesBillId() {
        UUID billId = UUID.randomUUID();
        Customer customer = createCustomer(UUID.randomUUID(), "Test");
        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-999")
                .customer(customer)
                .status(BillStatus.CANCELLED) // Must be cancelled first
                .items(new ArrayList<>())
                .build();

        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .bill(bill)
                .amount(new BigDecimal("500.00"))
                .notes("Original Note")
                .build();

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(paymentRepository.findByBillIdIn(List.of(billId))).thenReturn(List.of(payment));

        // Delete the bill
        billService.deleteBill(billId);

        // Verify that the bill was deleted from the repository
        verify(billRepository, times(1)).delete(bill);

        // Verify payment properties (bill reference nullified, audit message appended to notes)
        assertNull(payment.getBill());
        assertTrue(payment.getNotes().contains("deleted"));
        verify(paymentRepository, times(1)).save(payment);
    }

    // ── Bug #16 — Delivery Cancel Sync ──
    @Test
    public void testDeliveryCancellationSync() {
        UUID billId = UUID.randomUUID();
        Customer customer = createCustomer(UUID.randomUUID(), "Test Customer");
        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-COD-1")
                .customer(customer)
                .status(BillStatus.CONFIRMED)
                .items(new ArrayList<>())
                .build();

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(billRepository.save(any(Bill.class))).thenReturn(bill);
        mockRecalculateCustomerPending(customer, List.of(bill));

        // Cancel the bill
        billService.cancelBill(billId, "System");

        // Verify that deliveryService.cancelDeliveryForBill was called with correct arguments
        verify(deliveryServiceMock, times(1)).cancelDeliveryForBill(
                eq(billId), 
                contains("Linked Bill BILL-COD-1 was cancelled")
        );
    }

    // ── Bug #17 — Pagination & Search ──
    @Test
    public void testPaginationAndSearchMethodExists() {
        assertDoesNotThrow(() -> {
            Method m = BillRepository.class.getMethod("findBillsPaged", 
                    BillStatus.class, boolean.class, UUID.class, String.class, org.springframework.data.domain.Pageable.class);
            assertNotNull(m);
        });
    }

    // ── Bug #23 — N+1 Queries (JOIN optimization) ──
    @Test
    public void testEntityGraphOptimizationPreventingNPlusOneQueries() throws Exception {
        // Assert repository methods use EntityGraph to load relationships in single query
        Method m1 = BillRepository.class.getMethod("findAll");
        Method m2 = BillRepository.class.getMethod("findByCustomerIdOrderByCreatedAtDesc", UUID.class);
        Method m3 = BillRepository.class.getMethod("findPendingBills");

        assertTrue(m1.isAnnotationPresent(org.springframework.data.jpa.repository.EntityGraph.class));
        assertTrue(m2.isAnnotationPresent(org.springframework.data.jpa.repository.EntityGraph.class));
        assertTrue(m3.isAnnotationPresent(org.springframework.data.jpa.repository.EntityGraph.class));

        org.springframework.data.jpa.repository.EntityGraph eg = m1.getAnnotation(org.springframework.data.jpa.repository.EntityGraph.class);
        List<String> paths = Arrays.asList(eg.attributePaths());
        assertTrue(paths.contains("customer"));
        assertTrue(paths.contains("items.product"));
    }

    // ── Bug #24 — Flexible Restore & Exhausted Batch Fallback ──
    @Test
    public void testFlexibleRestoreExhaustedBatchFallback() {
        UUID billId = UUID.randomUUID();
        Product product = createProduct(UUID.randomUUID(), "Pepsi", new BigDecimal("10.00"), new BigDecimal("8.00"), 12);
        Customer customer = createCustomer(UUID.randomUUID(), "Test Customer");

        StockBatch exhaustedBatch = StockBatch.builder()
                .id(UUID.randomUUID())
                .product(product)
                .secondaryRemaining(0) // exhausted
                .exhausted(true)
                .batchStatus(BatchStatus.ACTIVE)
                .build();

        StockBatch activeBatch = StockBatch.builder()
                .id(UUID.randomUUID())
                .product(product)
                .secondaryRemaining(15) // Enough stock to deduct 10
                .secondaryReceived(20)
                .exhausted(false)
                .batchStatus(BatchStatus.ACTIVE)
                .build();

        BillItem item = BillItem.builder()
                .product(product)
                .batch(exhaustedBatch)
                .unitType(UnitType.BOTTLE)
                .quantity(10)
                .freeQuantity(0)
                .rate(new BigDecimal("10.00"))
                .build();

        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-RESTORE")
                .customer(customer)
                .status(BillStatus.CANCELLED)
                .items(new ArrayList<>(Collections.singletonList(item)))
                .build();

        Stock stock = Stock.builder()
                .product(product)
                .totalSecondaryUnits(15)
                .build();

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(userRepository.findByPhone("System")).thenReturn(Optional.of(createUser("Admin", "System", UserRole.ADMIN)));
        lenient().when(billRepository.save(any(Bill.class))).thenReturn(bill);
        
        // Mock fallback active batches retrieval
        lenient().when(batchRepository.findByProductIdOrderByReceivedAtDesc(product.getId())).thenReturn(List.of(activeBatch, exhaustedBatch));
        lenient().when(batchRepository.findActiveBatchesFIFO(product.getId())).thenReturn(List.of(activeBatch, exhaustedBatch));
        lenient().when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        lenient().when(stockRepository.findByProductId(product.getId())).thenReturn(Optional.of(stock));
        lenient().when(stockRepository.findByProductIdWithLock(product.getId())).thenReturn(Optional.of(stock));
        lenient().when(batchRepository.findById(any())).thenReturn(Optional.of(activeBatch));
        lenient().when(batchRepository.findByIdForUpdate(any())).thenReturn(Optional.of(activeBatch));

        mockRecalculateCustomerPending(customer, List.of(bill));

        // Restore bill
        billService.restoreBill(billId, "System");

        // Verify that the restore operation fell back to activeBatch and restored stock
        // original was 15, we deduct 10 units -> 15 - 10 = 5
        assertEquals(5, activeBatch.getSecondaryRemaining());
    }

    // ── Bug #25 — Return Item Proportional Discount Adjustment ──
    @Test
    public void testReturnItemProportionalDiscountAdjustment() {
        UUID billId = UUID.randomUUID();
        UUID billItemId = UUID.randomUUID();
        Product product = createProduct(UUID.randomUUID(), "Item A", new BigDecimal("10.00"), new BigDecimal("8.00"), 1);
        Customer customer = createCustomer(UUID.randomUUID(), "Test");
        customer.setTotalPending(new BigDecimal("18.00")); // Grand Total (20 - 2 discount = 18)

        // Sold: 2 units at 10.00 = 20.00 subtotal. Discount 2.00. Grand Total = 18.00.
        BillItem item = BillItem.builder()
                .id(billItemId)
                .product(product)
                .unitType(UnitType.BOTTLE)
                .quantity(2)
                .rate(new BigDecimal("10.00"))
                .gstPercent(BigDecimal.ZERO)
                .gstAmount(BigDecimal.ZERO)
                .cessPercent(BigDecimal.ZERO)
                .cessAmount(BigDecimal.ZERO)
                .total(new BigDecimal("20.00"))
                .batchDeductions(new ArrayList<>())
                .build();

        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-DISCOUNT")
                .customer(customer)
                .subtotal(new BigDecimal("20.00"))
                .gstTotal(BigDecimal.ZERO)
                .cessTotal(BigDecimal.ZERO)
                .discount(new BigDecimal("2.00"))
                .grandTotal(new BigDecimal("18.00"))
                .paidAmount(BigDecimal.ZERO)
                .pendingAmount(new BigDecimal("18.00"))
                .paymentMode(PaymentMode.UDHAR)
                .status(BillStatus.CONFIRMED)
                .items(new ArrayList<>(Collections.singletonList(item)))
                .build();
        item.setBill(bill);

        ReturnItemsRequest.ReturnedItemRequest returnReq = new ReturnItemsRequest.ReturnedItemRequest();
        returnReq.setBillItemId(billItemId);
        returnReq.setQuantityToReturn(1); // Return 1 unit

        ReturnItemsRequest req = new ReturnItemsRequest();
        req.setReturnedItems(Collections.singletonList(returnReq));

        mockStockAndBatch(product.getId(), product, 10);

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(billRepository.save(any(Bill.class))).thenReturn(bill);
        mockRecalculateCustomerPending(customer, List.of(bill));

        // Perform return
        billService.returnItems(billId, req, "System");

        // Assert Net Refund: 1 unit value (10.00) - proportional discount share (1.00) = 9.00 refund.
        // New grand total: 18.00 - 9.00 = 9.00.
        // New customer pending total: 18.00 - 9.00 = 9.00.
        assertEquals(new BigDecimal("9.00"), bill.getGrandTotal());
        assertEquals(new BigDecimal("9.00"), customer.getTotalPending());
        assertEquals(new BigDecimal("1.00"), bill.getDiscount()); // remaining discount should be 1.00
    }

    // ── Bug #28 — Broken Object-Level Authorization (BOLA) Security ──
    @Test
    public void testBolaSecurityAccessDeniedForOtherSalesmanBill() {
        UUID billId = UUID.randomUUID();
        User salesmanA = createUser("Vikram", "9876543210", UserRole.SALESMAN);
        salesmanA.setId(UUID.randomUUID());

        User salesmanB = createUser("Rohan", "9111111111", UserRole.SALESMAN);
        salesmanB.setId(UUID.randomUUID());

        Bill bill = Bill.builder()
                .id(billId)
                .billNumber("BILL-SALES-B")
                .createdBy(salesmanB)
                .items(new ArrayList<>())
                .customer(createCustomer(UUID.randomUUID(), "B's Customer"))
                .build();

        lenient().when(billRepository.findById(billId)).thenReturn(Optional.of(bill));
        lenient().when(userRepository.findByPhone("9876543210")).thenReturn(Optional.of(salesmanA));

        // Salesman A attempts to fetch Salesman B's bill -> AccessDeniedException expected
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            billService.getBillById(billId, "9876543210");
        });
    }
}
