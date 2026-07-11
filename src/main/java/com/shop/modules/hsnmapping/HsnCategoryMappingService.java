package com.shop.modules.hsnmapping;

import com.shop.modules.hsnmapping.dto.HsnCategoryMappingRequest;
import com.shop.modules.hsnmapping.dto.HsnCategoryMappingResponse;
import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.product.Category;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HsnCategoryMappingService {

    private final HsnCategoryMappingRepository mappingRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public List<HsnCategoryMappingResponse> getAllMappings() {
        return mappingRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public HsnCategoryMappingResponse saveMapping(HsnCategoryMappingRequest req, String username) {
        User user = userRepository.findByPhone(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        String key = req.getCategoryKey().trim().toLowerCase();

        HsnCategoryMapping mapping = mappingRepository.findByCategoryKey(key)
                .orElse(HsnCategoryMapping.builder().categoryKey(key).build());

        mapping.setHsnCode(req.getHsnCode().trim());
        mapping.setUpdatedBy(user.getId());
        mapping.setUpdatedAt(LocalDateTime.now());

        HsnCategoryMapping saved = mappingRepository.save(mapping);
        return toResponse(saved);
    }

    @SuppressWarnings("unchecked")
    public List<String> getLiveCategories(LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT DISTINCT " +
                "  CASE WHEN category = 'OTHER' THEN LOWER(TRIM(other_category_detail)) " +
                "       ELSE LOWER(TRIM(category)) END as cat_key " +
                "FROM products " +
                "WHERE is_active = true " +
                "   OR id IN (" +
                "       SELECT DISTINCT bi.product_id " +
                "       FROM bill_items bi " +
                "       JOIN bills b ON bi.bill_id = b.id " +
                "       WHERE b.status = 'CONFIRMED' " +
                "         AND b.created_at >= :start " +
                "         AND b.created_at <= :end " +
                "   )";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("start", start);
        query.setParameter("end", end);
        List<?> resultList = query.getResultList();
        return resultList.stream()
                .filter(Objects::nonNull)
                .map(obj -> obj.toString().trim().toLowerCase())
                .collect(Collectors.toList());
    }

    @Transactional
    public void applyMapping(String username) {
        // Fetch all active mappings
        List<HsnCategoryMapping> mappings = mappingRepository.findAll();
        Map<String, String> mappingMap = mappings.stream()
                .collect(Collectors.toMap(
                        HsnCategoryMapping::getCategoryKey,
                        HsnCategoryMapping::getHsnCode
                ));

        // Fetch all products
        List<Product> products = productRepository.findAll();
        List<Product> updatedProducts = new ArrayList<>();

        for (Product product : products) {
            String key = null;
            if (product.getCategory() == Category.OTHER) {
                if (product.getOtherCategoryDetail() != null) {
                    key = product.getOtherCategoryDetail().trim().toLowerCase();
                }
            } else if (product.getCategory() != null) {
                key = product.getCategory().name().trim().toLowerCase();
            }

            if (key != null && mappingMap.containsKey(key)) {
                product.setHsnCode(mappingMap.get(key));
                updatedProducts.add(product);
            }
        }

        if (!updatedProducts.isEmpty()) {
            productRepository.saveAll(updatedProducts);
        }
    }

    private HsnCategoryMappingResponse toResponse(HsnCategoryMapping mapping) {
        String updatedByName = null;
        if (mapping.getUpdatedBy() != null) {
            updatedByName = userRepository.findById(mapping.getUpdatedBy())
                    .map(User::getName)
                    .orElse(null);
        }

        return HsnCategoryMappingResponse.builder()
                .id(mapping.getId())
                .categoryKey(mapping.getCategoryKey())
                .hsnCode(mapping.getHsnCode())
                .updatedBy(mapping.getUpdatedBy())
                .updatedByName(updatedByName)
                .updatedAt(mapping.getUpdatedAt())
                .build();
    }
}
