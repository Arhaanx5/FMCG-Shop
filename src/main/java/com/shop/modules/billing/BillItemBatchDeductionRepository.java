package com.shop.modules.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface BillItemBatchDeductionRepository extends JpaRepository<BillItemBatchDeduction, UUID> {
    List<BillItemBatchDeduction> findByBillItemId(UUID billItemId);
}
