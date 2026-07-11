package com.shop.modules.billing.reports;

import com.shop.modules.billing.*;
import com.shop.modules.hsnmapping.HsnCategoryMapping;
import com.shop.modules.hsnmapping.HsnCategoryMappingRepository;
import com.shop.modules.hsnmapping.HsnCategoryMappingService;
import com.shop.modules.product.Category;
import com.shop.modules.product.Product;
import com.shop.modules.product.ProductRepository;
import com.shop.modules.shopprofile.*;
import com.shop.modules.shopprofile.dto.UpdateShopProfileRequest;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GstBillingAndFilingTest {

    @Mock
    private ShopProfileRepository shopProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private HsnCategoryMappingRepository hsnCategoryMappingRepository;

    @Mock
    private BillRepository billRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    private ShopProfileService shopProfileService;
    private HsnCategoryMappingService hsnCategoryMappingService;
    private Gstr1ReportService gstr1ReportService;

    @BeforeEach
    public void setUp() {
        shopProfileService = new ShopProfileService(shopProfileRepository, userRepository);
        hsnCategoryMappingService = new HsnCategoryMappingService(hsnCategoryMappingRepository, productRepository, userRepository);

        // Inject entityManager via reflection
        try {
            java.lang.reflect.Field field = HsnCategoryMappingService.class.getDeclaredField("entityManager");
            field.setAccessible(true);
            field.set(hsnCategoryMappingService, entityManager);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject entityManager mock via reflection", e);
        }

        gstr1ReportService = new Gstr1ReportService(billRepository, shopProfileService);
    }

    @Test
    public void testShopProfileSingletonConstraint() {
        // Arrange
        UpdateShopProfileRequest req = new UpdateShopProfileRequest();
        req.setCompanyName("LARI TRADERS NEW");
        req.setGstin("09DIMPA1174G1ZC");
        req.setStateCode("09");
        req.setStateName("Uttar Pradesh");

        User admin = User.builder().id(UUID.randomUUID()).name("Arhaan").build();
        when(userRepository.findByPhone(anyString())).thenReturn(Optional.of(admin));
        when(userRepository.findById(any())).thenReturn(Optional.of(admin));
        when(shopProfileRepository.findById(ShopProfileService.SHOP_PROFILE_ID)).thenReturn(Optional.empty());

        ArgumentCaptor<ShopProfile> captor = ArgumentCaptor.forClass(ShopProfile.class);
        when(shopProfileRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        shopProfileService.updateProfile(req, "9450821033");

        // Assert
        ShopProfile saved = captor.getValue();
        assertNotNull(saved);
        assertEquals(ShopProfileService.SHOP_PROFILE_ID, saved.getId(), "Shop profile ID must be fixed to SHOP_PROFILE_ID to enforce singleton row constraint");
        assertEquals("LARI TRADERS NEW", saved.getCompanyName());
    }

    @Test
    public void testApplyHsnMappingOverwritesExistingValues() {
        // Arrange
        HsnCategoryMapping m1 = HsnCategoryMapping.builder()
                .categoryKey("biscuits")
                .hsnCode("19053100")
                .build();
        when(hsnCategoryMappingRepository.findAll()).thenReturn(List.of(m1));

        Product p1 = Product.builder()
                .id(UUID.randomUUID())
                .name("Parle G")
                .category(Category.BISCUITS)
                .hsnCode("99999999") // Existing HSN
                .build();
        when(productRepository.findAll()).thenReturn(List.of(p1));

        // Act
        hsnCategoryMappingService.applyMapping("Arhaan");

        // Assert
        assertEquals("19053100", p1.getHsnCode(), "Apply mapping should overwrite existing HSN codes for matching categories");
    }

    @Test
    public void testApplyHsnMappingLeavesUnmappedCategoriesUntouched() {
        // Arrange
        HsnCategoryMapping m1 = HsnCategoryMapping.builder()
                .categoryKey("biscuits")
                .hsnCode("19053100")
                .build();
        when(hsnCategoryMappingRepository.findAll()).thenReturn(List.of(m1));

        Product p1 = Product.builder()
                .id(UUID.randomUUID())
                .name("Maaza")
                .category(Category.BEVERAGES) // Unmapped category
                .hsnCode("22021010")
                .build();
        when(productRepository.findAll()).thenReturn(List.of(p1));

        // Act
        hsnCategoryMappingService.applyMapping("Arhaan");

        // Assert
        assertEquals("22021010", p1.getHsnCode(), "Apply mapping must leave products of unmapped categories completely untouched");
    }

    @Test
    public void testInactiveBilledProductsIncludedInCategoriesList() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LocalDateTime end = LocalDateTime.now();

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(new ArrayList<>(List.of("biscuits", "beverages", "chips")));

        // Act
        List<String> categories = hsnCategoryMappingService.getLiveCategories(start, end);

        // Assert
        assertNotNull(categories);
        assertEquals(3, categories.size());
        assertTrue(categories.contains("biscuits"));
        assertTrue(categories.contains("beverages"));
        assertTrue(categories.contains("chips"));
    }

    @Test
    public void testGstr1BlockingOnMissingHsn() {
        // Arrange
        ShopProfile profile = ShopProfile.builder()
                .id(ShopProfileService.SHOP_PROFILE_ID)
                .companyName("Lari Traders")
                .gstin("09DIMPA1174G1ZC")
                .stateCode("09")
                .build();
        when(shopProfileRepository.findById(ShopProfileService.SHOP_PROFILE_ID)).thenReturn(Optional.of(profile));

        Product productWithoutHsn = Product.builder()
                .id(UUID.randomUUID())
                .name("Cadbury Dairy Milk")
                .category(Category.SNACKS)
                .hsnCode(null) // Missing HSN code!
                .build();

        BillItem billItem = BillItem.builder()
                .product(productWithoutHsn)
                .quantity(10)
                .gstPercent(BigDecimal.valueOf(18))
                .total(BigDecimal.valueOf(118))
                .gstAmount(BigDecimal.valueOf(18))
                .build();

        Bill bill = Bill.builder()
                .id(UUID.randomUUID())
                .billNumber("BILL-2026-0001")
                .status(BillStatus.CONFIRMED)
                .items(List.of(billItem))
                .createdAt(LocalDateTime.of(2026, 7, 10, 12, 0))
                .build();

        when(billRepository.findBillsBetween(any(), any()))
                .thenReturn(List.of(bill));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            gstr1ReportService.generateGstr1Report("2026-07");
        });

        assertTrue(exception.getMessage().contains("GSTR-1 Export Blocked:"), "Expected blocking warning message not found in exception");
        assertTrue(exception.getMessage().contains("Cadbury Dairy Milk"), "Expected product name missing in exception message");
    }
}
