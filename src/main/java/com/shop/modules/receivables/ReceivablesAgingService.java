package com.shop.modules.receivables;

import com.shop.modules.billing.Bill;
import com.shop.modules.receivables.dto.ReceivablesAgingResult;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReceivablesAgingService {

    public ReceivablesAgingResult calculateAging(List<Bill> pendingBills) {
        BigDecimal age30 = BigDecimal.ZERO;
        BigDecimal age60 = BigDecimal.ZERO;
        BigDecimal age90 = BigDecimal.ZERO;
        BigDecimal age90Plus = BigDecimal.ZERO;
        LocalDateTime now = LocalDateTime.now();

        for (Bill pb : pendingBills) {
            if (pb.getCreatedAt() == null) continue;
            long days = ChronoUnit.DAYS.between(pb.getCreatedAt(), now);
            BigDecimal pending = pb.getPendingAmount() != null ? pb.getPendingAmount() : BigDecimal.ZERO;
            if (days <= 30) {
                age30 = age30.add(pending);
            } else if (days <= 60) {
                age60 = age60.add(pending);
            } else if (days <= 90) {
                age90 = age90.add(pending);
            } else {
                age90Plus = age90Plus.add(pending);
            }
        }
        return new ReceivablesAgingResult(age30, age60, age90, age90Plus);
    }
}
