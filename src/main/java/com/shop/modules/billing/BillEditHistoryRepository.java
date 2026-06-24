package com.shop.modules.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface BillEditHistoryRepository extends JpaRepository<BillEditHistory, UUID> {
    List<BillEditHistory> findByBillIdOrderByEditedAtDesc(UUID billId);
}
