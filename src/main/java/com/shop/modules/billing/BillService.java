package com.shop.modules.billing;

import com.shop.common.PagedResponse;
import com.shop.modules.billing.dto.BillResponse;
import com.shop.modules.billing.dto.CreateBillRequest;
import com.shop.modules.billing.dto.ReturnItemsRequest;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.customer.CustomerService;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.modules.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service orchestrator for Billing.
 * Maintains all Read/Query operations and delegates state modifications.
 */
@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CustomerService customerService;
    private final BillEditHistoryRepository billEditHistoryRepository;
    private final BillMapper billMapper;

    private final BillCreationService billCreationService;
    private final BillUpdateService billUpdateService;
    private final BillCancellationService billCancellationService;
    private final BillConfirmationService billConfirmationService;
    private final com.shop.modules.khata.PaymentRepository paymentRepository;


    // ─────────────────────────────────────────────────────────────
    // Read (Query) Operations
    // ─────────────────────────────────────────────────────────────

    public List<BillResponse> getAllBills() {
        return billMapper.toResponses(billRepository.findAll());
    }

    public List<BillResponse> getRecentBills(int limit) {
        return billMapper.toResponses(billRepository.findRecentBills(PageRequest.of(0, limit)));
    }

    public BillResponse getBillById(UUID id) {
        return billMapper.toResponse(
                billRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Bill not found: " + id))
        );
    }

    public BillResponse getBillById(UUID id, String username) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + id));
        User user = userRepository.findByPhone(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        if (user.getRole() == UserRole.SALESMAN || user.getRole() == UserRole.DELIVERY_BOY) {
            if (bill.getCreatedBy() == null || !bill.getCreatedBy().getId().equals(user.getId())) {
                throw new AccessDeniedException("You are not authorized to view this bill");
            }
        }
        return billMapper.toResponse(bill);
    }

    public List<BillResponse> getPendingBills() {
        return billMapper.toResponses(billRepository.findPendingBills());
    }

    public List<BillResponse> getCustomerHistory(UUID customerId) {
        return billMapper.toResponses(billRepository.findByCustomerIdOrderByCreatedAtDesc(customerId));
    }

    public PagedResponse<BillResponse> getBillsPaged(
            BillStatus status,
            Boolean excludeDrafts,
            String search,
            int page,
            int size,
            String sort,
            String username) {
        User user = userRepository.findByPhone(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        String[] sortParts = sort.split(",");
        Sort sortObj = Sort.unsorted();
        if (sortParts.length == 2) {
            Sort.Direction dir = sortParts[1].equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            sortObj = Sort.by(dir, sortParts[0]);
        }
        Pageable pageable = PageRequest.of(page, size, sortObj);

        String searchVal = (search == null || search.trim().isEmpty()) ? null : search.trim();
        UUID salesmanId = (user.getRole() == UserRole.SALESMAN || user.getRole() == UserRole.DELIVERY_BOY) ? user.getId() : null;

        Page<Bill> bills = billRepository.findBillsPaged(
                status,
                excludeDrafts != null && excludeDrafts,
                salesmanId,
                searchVal,
                pageable
        );

        List<BillResponse> content = billMapper.toResponses(bills.getContent());
        return PagedResponse.<BillResponse>builder()
                .content(content)
                .currentPage(bills.getNumber())
                .totalPages(bills.getTotalPages())
                .totalElements(bills.getTotalElements())
                .size(bills.getSize())
                .first(bills.isFirst())
                .last(bills.isLast())
                .build();
    }

    public PagedResponse<BillResponse> getCustomerHistoryPaged(
            UUID customerId,
            int page,
            int size,
            String username) {
        User user = userRepository.findByPhone(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        Pageable pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "createdAt")
        );

        UUID salesmanId = (user.getRole() == UserRole.SALESMAN || user.getRole() == UserRole.DELIVERY_BOY) ? user.getId() : null;
        Page<Bill> bills = billRepository.findCustomerHistoryPaged(customerId, salesmanId, pageable);

        List<BillResponse> content = billMapper.toResponses(bills.getContent());
        return PagedResponse.<BillResponse>builder()
                .content(content)
                .currentPage(bills.getNumber())
                .totalPages(bills.getTotalPages())
                .totalElements(bills.getTotalElements())
                .size(bills.getSize())
                .first(bills.isFirst())
                .last(bills.isLast())
                .build();
    }

    public List<BillEditHistory> getBillEditHistory(UUID billId) {
        if (!billRepository.existsById(billId)) {
            throw new EntityNotFoundException("Bill not found: " + billId);
        }
        return billEditHistoryRepository.findByBillIdOrderByEditedAtDesc(billId);
    }

    // ─────────────────────────────────────────────────────────────
    // Write (Command) Operations — Delegated
    // ─────────────────────────────────────────────────────────────

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse createBill(CreateBillRequest req, String createdByPhone) {
        return billCreationService.createBill(req, createdByPhone, false);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse createBill(CreateBillRequest req, String createdByPhone, boolean overrideCost) {
        return billCreationService.createBill(req, createdByPhone, overrideCost);
    }

    @Transactional
    public void cancelBill(UUID id) {
        billCancellationService.cancelBill(id);
    }

    @Transactional
    public void cancelBill(UUID id, String username) {
        billCancellationService.cancelBill(id, username);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse restoreBill(UUID id) {
        return billCancellationService.restoreBill(id);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse restoreBill(UUID id, String username) {
        return billCancellationService.restoreBill(id, username);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse confirmBill(UUID billId) {
        return billConfirmationService.confirmBill(billId);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse confirmBill(UUID billId, String username) {
        return billConfirmationService.confirmBill(billId, username);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse confirmBill(UUID billId, boolean overrideCost, String username) {
        return billConfirmationService.confirmBill(billId, overrideCost, username);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BulkConfirmResult> bulkConfirmBills(List<UUID> billIds) {
        return billConfirmationService.bulkConfirmBills(billIds);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public List<BulkConfirmResult> bulkConfirmBills(List<UUID> billIds, String username) {
        return billConfirmationService.bulkConfirmBills(billIds, username);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class BulkConfirmResult {
        private UUID billId;
        private boolean success;
        private String message;
    }


    @Transactional(rollbackFor = Exception.class)
    public BillResponse updateBillDetails(UUID id, PaymentMode paymentMode, String notes, BillStatus status, BigDecimal paidAmount) {
        return billUpdateService.updateBillDetails(id, paymentMode, notes, status, paidAmount);
    }

    @Transactional(rollbackFor = Exception.class)
    public BillResponse updateBillDetails(UUID id, PaymentMode paymentMode, String notes, BillStatus status, BigDecimal paidAmount, String username) {
        return billUpdateService.updateBillDetails(id, paymentMode, notes, status, paidAmount, username);
    }

    @Transactional(rollbackFor = Exception.class)
    public BillResponse updateBillDetails(UUID id, PaymentMode paymentMode, String notes, BillStatus status, BigDecimal paidAmount,
                                          BigDecimal discount, Integer version, String editReason,
                                          List<CreateBillRequest.BillItemRequest> newItems, String username) {
        return billUpdateService.updateBillDetails(id, paymentMode, notes, status, paidAmount, discount, version, editReason, newItems, username);
    }

    @Transactional(rollbackFor = Exception.class)
    public BillResponse updateBillDetails(UUID id, PaymentMode paymentMode, String notes, BillStatus status, BigDecimal paidAmount,
                                          BigDecimal discount, Integer version, String editReason,
                                          List<CreateBillRequest.BillItemRequest> newItems, boolean overrideCost, String partialPaymentMode, String username) {
        return billUpdateService.updateBillDetails(id, paymentMode, notes, status, paidAmount, discount, version, editReason, newItems, overrideCost, partialPaymentMode, username);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse returnItems(UUID billId, ReturnItemsRequest req) {
        return billCancellationService.returnItems(billId, req);
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public BillResponse returnItems(UUID billId, ReturnItemsRequest req, String username) {
        return billCancellationService.returnItems(billId, req, username);
    }

    @Transactional
    public void deleteBill(UUID id) {
        Bill bill = billRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bill not found: " + id));

        if (bill.getStatus() != BillStatus.CANCELLED) {
            throw new RuntimeException("Only CANCELLED bills can be deleted. Cancel the bill first.");
        }

        List<com.shop.modules.khata.Payment> payments = paymentRepository.findByBillIdIn(List.of(bill.getId()));
        for (com.shop.modules.khata.Payment payment : payments) {
            payment.setBill(null);
            String oldNotes = payment.getNotes() != null ? payment.getNotes() : "";
            payment.setNotes((oldNotes + " | (Linked Bill " + bill.getBillNumber() + " was deleted)").trim());
            paymentRepository.save(payment);
        }

        billRepository.delete(bill);
    }
}