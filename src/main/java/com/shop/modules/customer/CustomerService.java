package com.shop.modules.customer;

import com.shop.modules.area.Area;
import com.shop.modules.area.AreaRepository;
import com.shop.modules.customer.dto.CreateCustomerRequest;
import com.shop.modules.customer.dto.CustomerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AreaRepository areaRepository;
    private final com.shop.modules.billing.BillRepository billRepository;

    public java.math.BigDecimal calculateEffectiveCreditLimit(Customer customer) {
        if (customer.getCreditLimit() != null) {
            return customer.getCreditLimit();
        }
        
        long daysActive = 0;
        if (customer.getCreatedAt() != null) {
            daysActive = java.time.temporal.ChronoUnit.DAYS.between(customer.getCreatedAt(), java.time.LocalDateTime.now());
        }
        
        java.math.BigDecimal cumulativePaid = billRepository.sumPaidAmountByCustomerId(customer.getId());
        if (cumulativePaid == null) {
            cumulativePaid = java.math.BigDecimal.ZERO;
        }
        
        if (daysActive >= 30 && cumulativePaid.compareTo(new java.math.BigDecimal("25000.00")) >= 0) {
            return new java.math.BigDecimal("50000.00");
        }
        
        return java.math.BigDecimal.ZERO;
    }

    // Convert entity to DTO
    private CustomerResponse toResponse(Customer customer) {

        boolean isInactive = customer.getLastOrderAt() != null
                && customer.getLastOrderAt()
                .isBefore(LocalDateTime.now().minusDays(30));

        java.math.BigDecimal effectiveLimit = calculateEffectiveCreditLimit(customer);
        long daysActive = 0;
        if (customer.getCreatedAt() != null) {
            daysActive = java.time.temporal.ChronoUnit.DAYS.between(customer.getCreatedAt(), java.time.LocalDateTime.now());
        }
        java.math.BigDecimal cumulativePaid = billRepository.sumPaidAmountByCustomerId(customer.getId());
        if (cumulativePaid == null) {
            cumulativePaid = java.math.BigDecimal.ZERO;
        }
        boolean isManualOverride = customer.getCreditLimit() != null;
        boolean autoEligible = daysActive >= 30 && cumulativePaid.compareTo(new java.math.BigDecimal("25000.00")) >= 0;

        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .customerCode(customer.getCustomerCode())
                .shopName(customer.getShopName())
                .phone(customer.getPhone())
                .areaId(customer.getArea() != null
                        ? customer.getArea().getId() : null)
                .areaName(customer.getArea() != null
                        ? customer.getArea().getName() : null)
                .latitude(customer.getLatitude())
                .longitude(customer.getLongitude())
                .locationMethod(customer.getLocationMethod())
                .hasLocation(customer.getLatitude() != null
                        && customer.getLongitude() != null)
                .totalPending(customer.getTotalPending())
                .hasOutstanding(customer.getTotalPending()
                        .compareTo(java.math.BigDecimal.ZERO) > 0)
                .openingBalance(customer.getOpeningBalance())
                .creditLimit(effectiveLimit)
                .manualCreditLimit(customer.getCreditLimit())
                .effectiveCreditLimit(effectiveLimit)
                .cumulativePaidAmount(cumulativePaid)
                .daysActive(daysActive)
                .autoEligible(autoEligible)
                .isManualOverride(isManualOverride)
                .isNpa(customer.getIsNpa())
                .lastOrderAt(customer.getLastOrderAt())
                .inactive(isInactive)
                .active(customer.getActive())
                .createdAt(customer.getCreatedAt())
                .build();
    }

    public Customer findCustomerByIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new RuntimeException("Identifier cannot be blank");
        }
        String trimmed = identifier.trim();
        try {
            UUID uuid = UUID.fromString(trimmed);
            return customerRepository.findById(uuid)
                    .orElseThrow(() -> new RuntimeException("Customer not found with ID: " + uuid));
        } catch (IllegalArgumentException e) {
            return customerRepository.findByCustomerCodeIgnoreCase(trimmed)
                    .orElseThrow(() -> new RuntimeException("Customer not found with code: " + trimmed));
        }
    }

    public CustomerResponse getCustomerByIdentifier(String identifier) {
        return toResponse(findCustomerByIdentifier(identifier));
    }

    private String generateCustomerCode() {
        int nextSeq = customerRepository.findMaxCustomerSequence() + 1;
        return String.format("CUST-%05d", nextSeq);
    }

    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findByActiveTrue()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public org.springframework.data.domain.Page<CustomerResponse> getAllCustomers(org.springframework.data.domain.Pageable pageable) {
        return customerRepository.findByActiveTrue(pageable)
                .map(this::toResponse);
    }

    public CustomerResponse getCustomerById(UUID id) {
        return toResponse(customerRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Customer not found: " + id)));
    }

    public List<CustomerResponse> searchCustomers(
            String name) {
        if (name == null || name.trim().isBlank()) {
            throw new RuntimeException(
                    "Search term cannot be blank");
        }
        return customerRepository
                .findByNameContainingIgnoreCaseAndActiveTrue(
                        name.trim())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public org.springframework.data.domain.Page<CustomerResponse> searchCustomers(
            String name, org.springframework.data.domain.Pageable pageable) {
        if (name == null || name.trim().isBlank()) {
            throw new RuntimeException("Search term cannot be blank");
        }
        return customerRepository
                .findByNameContainingIgnoreCaseAndActiveTrue(name.trim(), pageable)
                .map(this::toResponse);
    }

    public List<CustomerResponse> getInactiveCustomers() {
        LocalDateTime cutoff =
                LocalDateTime.now().minusDays(30);
        return customerRepository
                .findInactiveCustomers(cutoff)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public CustomerResponse createCustomer(
            CreateCustomerRequest req) {

        // Trim name
        String name = req.getName().trim();
        if (name.isBlank()) {
            throw new RuntimeException(
                    "Customer name cannot be blank");
        }

        // Check duplicate phone
        boolean phoneExists = customerRepository
                .findByActiveTrue()
                .stream()
                .anyMatch(c -> c.getPhone() != null
                        && c.getPhone().equals(req.getPhone()));

        if (phoneExists) {
            throw new RuntimeException(
                    "Customer with phone "
                            + req.getPhone()
                            + " already exists");
        }

        // Get area if provided
        Area area = null;
        if (req.getAreaId() != null) {
            area = areaRepository
                    .findById(req.getAreaId())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Area not found: "
                                            + req.getAreaId()));
        }

        Customer customer = Customer.builder()
                .name(name)
                .customerCode(generateCustomerCode())
                .shopName(req.getShopName() != null
                        ? req.getShopName().trim() : null)
                .phone(req.getPhone())
                .area(area)
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .locationMethod(req.getLocationMethod())
                .totalPending(java.math.BigDecimal.ZERO)
                .openingBalance(req.getOpeningBalance() != null ? req.getOpeningBalance() : java.math.BigDecimal.ZERO)
                .isNpa(req.getIsNpa() != null ? req.getIsNpa() : false)
                .creditLimit(req.getCreditLimit() != null ? req.getCreditLimit() : new java.math.BigDecimal("50000.00"))
                .active(true)
                .build();

        return toResponse(customerRepository.save(customer));
    }

    public CustomerResponse updateCustomer(
            String idOrCode, CreateCustomerRequest req) {

        Customer customer = findCustomerByIdentifier(idOrCode);

        // Trim name
        String name = req.getName().trim();
        if (name.isBlank()) {
            throw new RuntimeException(
                    "Customer name cannot be blank");
        }

        // Check duplicate phone — exclude current
        boolean phoneExists = customerRepository
                .findByActiveTrue()
                .stream()
                .anyMatch(c -> c.getPhone() != null
                        && c.getPhone().equals(req.getPhone())
                        && !c.getId().equals(customer.getId()));

        if (phoneExists) {
            throw new RuntimeException(
                    "Phone " + req.getPhone()
                            + " already in use");
        }

        // Get area
        Area area = null;
        if (req.getAreaId() != null) {
            area = areaRepository
                    .findById(req.getAreaId())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Area not found"));
        }

        customer.setName(name);
        customer.setShopName(req.getShopName() != null
                ? req.getShopName().trim() : null);
        customer.setPhone(req.getPhone());
        customer.setArea(area);
        if (req.getIsNpa() != null) {
            customer.setIsNpa(req.getIsNpa());
        }
        if (req.getCreditLimit() != null) {
            customer.setCreditLimit(req.getCreditLimit());
        }

        return toResponse(customerRepository.save(customer));
    }

    public CustomerResponse updateLocation(
            String idOrCode,
            Double lat,
            Double lng,
            String method) {

        if (lat == null || lng == null) {
            throw new RuntimeException(
                    "Latitude and longitude are required");
        }

        if (lat < -90 || lat > 90) {
            throw new RuntimeException(
                    "Invalid latitude value");
        }

        if (lng < -180 || lng > 180) {
            throw new RuntimeException(
                    "Invalid longitude value");
        }

        Customer customer = findCustomerByIdentifier(idOrCode);

        customer.setLatitude(lat);
        customer.setLongitude(lng);
        customer.setLocationMethod(method);

        return toResponse(customerRepository.save(customer));
    }

    public void deactivateCustomer(String idOrCode) {
        Customer customer = findCustomerByIdentifier(idOrCode);
        customer.setActive(false);
        customerRepository.save(customer);
    }

    public void scanAndMarkNpaCustomers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<Customer> overdueCustomers = billRepository.findCustomersWithOverdueBills(cutoff);
        List<Customer> allCustomers = customerRepository.findByActiveTrue();
        
        for (Customer c : allCustomers) {
            boolean shouldBeNpa = overdueCustomers.stream()
                    .anyMatch(oc -> oc.getId().equals(c.getId()));
            
            if (c.getIsNpa() == null || c.getIsNpa() != shouldBeNpa) {
                c.setIsNpa(shouldBeNpa);
                customerRepository.save(c);
            }
        }
    }
}