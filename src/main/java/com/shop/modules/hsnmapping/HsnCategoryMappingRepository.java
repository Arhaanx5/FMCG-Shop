package com.shop.modules.hsnmapping;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HsnCategoryMappingRepository extends JpaRepository<HsnCategoryMapping, UUID> {
    Optional<HsnCategoryMapping> findByCategoryKey(String categoryKey);
}
