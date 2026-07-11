package com.shop.modules.dashboard;

import com.shop.modules.area.Area;
import com.shop.modules.area.AreaRepository;
import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.dashboard.dto.SalesmanPerformanceResponse;
import com.shop.modules.khata.Payment;
import com.shop.modules.khata.PaymentRepository;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.modules.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesmenPerformanceService {

    private final UserRepository userRepository;
    private final AreaRepository areaRepository;
    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;

    public List<SalesmanPerformanceResponse> getSalesmenPerformance() {
        List<User> salesmen = userRepository.findByRole(UserRole.SALESMAN);
        List<SalesmanPerformanceResponse> report = new ArrayList<>();

        for (User sm : salesmen) {
            List<Area> areas = areaRepository.findBySalesmanId(sm.getId());
            List<String> areaNames = areas.stream().map(Area::getName).collect(Collectors.toList());

            // GrandTotal of CONFIRMED, PARTIAL, and PAID bills created by this salesman
            List<Bill> bills = billRepository.findByCreatedByIdAndStatusIn(
                    sm.getId(),
                    List.of(BillStatus.CONFIRMED, BillStatus.PARTIAL, BillStatus.PAID)
            );
            BigDecimal totalRevenue = bills.stream()
                    .map(b -> b.getGrandTotal() != null ? b.getGrandTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Payments collected by this salesman (Khata payments)
            List<Payment> collections = paymentRepository.findByCollectedById(sm.getId());
            BigDecimal khataCollected = collections.stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Immediate payments collected by this salesman on bills they created
            BigDecimal immediateCollected = BigDecimal.ZERO;
            Map<UUID, BigDecimal> appliedAmountsMap = new java.util.HashMap<>();
            if (!bills.isEmpty()) {
                List<UUID> billIds = bills.stream().map(Bill::getId).collect(Collectors.toList());
                List<Payment> appliedPayments = paymentRepository.findByBillIdIn(billIds);
                for (Payment p : appliedPayments) {
                    if (p.getBill() != null) {
                        UUID bId = p.getBill().getId();
                        BigDecimal amt = p.getAppliedAmount() != null ? p.getAppliedAmount() : BigDecimal.ZERO;
                        appliedAmountsMap.put(bId, appliedAmountsMap.getOrDefault(bId, BigDecimal.ZERO).add(amt));
                    }
                }
            }
            for (Bill bill : bills) {
                BigDecimal billPaidAmount = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
                BigDecimal applied = appliedAmountsMap.getOrDefault(bill.getId(), BigDecimal.ZERO);
                immediateCollected = immediateCollected.add(billPaidAmount.subtract(applied));
            }

            BigDecimal totalCollected = khataCollected.add(immediateCollected);

            // Active route credit (outstanding pending debt in assigned areas)
            BigDecimal routeCredit = BigDecimal.ZERO;
            long activeCustomers = 0;

            for (Area area : areas) {
                List<Customer> customers = customerRepository.findByAreaId(area.getId());
                for (Customer c : customers) {
                    if (Boolean.TRUE.equals(c.getActive())) {
                        activeCustomers++;
                        if (c.getTotalPending() != null) {
                            routeCredit = routeCredit.add(c.getTotalPending());
                        }
                    }
                }
            }

            report.add(SalesmanPerformanceResponse.builder()
                    .salesmanId(sm.getId())
                    .salesmanName(sm.getName())
                    .salesmanPhone(sm.getPhone())
                    .assignedAreas(areaNames)
                    .totalRevenueGenerated(totalRevenue)
                    .totalCollectionsMade(totalCollected)
                    .activeRouteCredit(routeCredit)
                    .activeCustomersCount(activeCustomers)
                    .build());
        }

        return report;
    }
}
