package com.shop;

import org.springframework.core.env.Environment;
import com.shop.modules.area.Area;
import com.shop.modules.area.AreaRepository;
import com.shop.modules.customer.Customer;
import com.shop.modules.customer.CustomerRepository;
import com.shop.modules.billing.Bill;
import com.shop.modules.billing.BillRepository;
import com.shop.modules.billing.BillStatus;
import com.shop.modules.billing.PaymentMode;
import com.shop.modules.delivery.Delivery;
import com.shop.modules.delivery.DeliveryRepository;
import com.shop.modules.delivery.DeliveryStatus;
import com.shop.modules.delivery.DeliveryType;
import com.shop.modules.user.User;
import com.shop.modules.user.UserRepository;
import com.shop.modules.user.UserRole;
import com.shop.modules.product.Category;
import com.shop.modules.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AreaRepository areaRepository;
    private final CustomerRepository customerRepository;
    private final BillRepository billRepository;
    private final DeliveryRepository deliveryRepository;
    private final Environment environment;

    @Override
    public void run(String... args) throws Exception {
        List<String> activeProfiles = java.util.Arrays.asList(environment.getActiveProfiles());
        boolean isProd = activeProfiles.contains("prod");
        boolean isUat = activeProfiles.contains("uat");
        boolean isDev = !isProd && !isUat;

        if (isDev) {
            System.out.println("🌱 Running in Development Mode. Active profiles: " + activeProfiles);
        } else if (isUat) {
            System.out.println("🛡️ Running in UAT Mode. Active profiles: " + activeProfiles);
        } else if (isProd) {
            System.out.println("🚀 Running in Production Mode. Active profiles: " + activeProfiles);
        }

        if (!isProd) {
            try {
                jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
                if (isDev) {
                    System.out.println("✅ Dropped users_role_check constraint to allow new roles");
                }
            } catch (Exception e) {
                if (isDev) {
                    System.out.println("⚠️ Could not drop users_role_check constraint: " + e.getMessage());
                }
            }

            // Allow PARTIAL and PAID bill statuses
            try {
                jdbcTemplate.execute("ALTER TABLE bills DROP CONSTRAINT IF EXISTS bills_status_check");
                jdbcTemplate.execute(
                    "ALTER TABLE bills ADD CONSTRAINT bills_status_check " +
                    "CHECK (status IN ('DRAFT','CONFIRMED','PARTIAL','PAID','CANCELLED'))"
                );
                if (isDev) {
                    System.out.println("✅ Updated bills_status_check to allow PARTIAL and PAID statuses");
                }
            } catch (Exception e) {
                if (isDev) {
                    System.out.println("⚠️ Could not update bills_status_check constraint: " + e.getMessage());
                }
            }

            // Allow new payment adjustment columns (no-op if already present — handled by Hibernate ddl-auto=update)
            try {
                jdbcTemplate.execute(
                    "ALTER TABLE payments " +
                    "ADD COLUMN IF NOT EXISTS applied_amount NUMERIC(19,2) DEFAULT 0, " +
                    "ADD COLUMN IF NOT EXISTS excess_amount NUMERIC(19,2) DEFAULT 0, " +
                    "ADD COLUMN IF NOT EXISTS adjusted_bill_id UUID, " +
                    "ADD COLUMN IF NOT EXISTS adjustment_type VARCHAR(32), " +
                    "ADD COLUMN IF NOT EXISTS adjustment_note TEXT"
                );
                if (isDev) {
                    System.out.println("✅ Ensured payment adjustment columns exist");
                }
            } catch (Exception e) {
                if (isDev) {
                    System.out.println("⚠️ Could not add payment adjustment columns: " + e.getMessage());
                }
            }

            // Patch Lays Chips Classic secondary unit to LADI
            productRepository.findByNameExact("Lays Chips Classic").forEach(product -> {
                if (!"LADI".equals(product.getSecondaryUnit())) {
                    product.setSecondaryUnit("LADI");
                    productRepository.save(product);
                    if (isDev) {
                        System.out.println("✅ Patched Lays Chips Classic secondary unit to LADI in database");
                    }
                }
            });
        }

        // Drop products_category_check constraint if it exists to allow the new enum value (all environments)
        try {
            jdbcTemplate.execute("ALTER TABLE products DROP CONSTRAINT IF EXISTS products_category_check");
            System.out.println("✅ Dropped products_category_check constraint to allow new categories (all environments)");
        } catch (Exception e) {
            System.out.println("⚠️ Could not drop products_category_check constraint: " + e.getMessage());
        }

        // Move any product containing "Chips" or "Chip" in name/brand from SNACKS to CHIPS (all environments)
        productRepository.findAll().forEach(product -> {
            if (product.getCategory() == Category.SNACKS) {
                String nameLower = product.getName() != null ? product.getName().toLowerCase() : "";
                String brandLower = product.getBrand() != null ? product.getBrand().toLowerCase() : "";
                if (nameLower.contains("chip") || nameLower.contains("chips") ||
                    brandLower.contains("chip") || brandLower.contains("chips")) {
                    product.setCategory(Category.CHIPS);
                    productRepository.save(product);
                    System.out.println("✅ Moved product '" + product.getName() + "' to CHIPS category");
                }
            }
        });


        // Create admin if not exists
        User admin = null;
        if (!userRepository.existsByPhone("9999999999")) {
            admin = User.builder()
                    .name("Admin")
                    .phone("9999999999")
                    .role(UserRole.ADMIN)
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .active(true)
                    .mustChangePassword(false)
                    .build();
            admin = userRepository.save(admin);
            if (isDev) {
                System.out.println("✅ Admin user created successfully");
            }
        } else {
            admin = userRepository.findByPhone("9999999999").orElse(null);
            if (isDev) {
                System.out.println("✅ Admin user already exists");
            }
        }

        if (isDev) {
            // Create default salesman if not exists
            if (!userRepository.existsByPhone("9876543210")) {
                User salesman = User.builder()
                        .name("Vikram Singh")
                        .phone("9876543210")
                        .role(UserRole.SALESMAN)
                        .passwordHash(passwordEncoder.encode("salesman123"))
                        .active(true)
                        .mustChangePassword(false)
                        .build();
                userRepository.save(salesman);
                System.out.println("✅ Default salesman user created successfully");
            }

            // Create default delivery boy if not exists
            User deliveryBoy = null;
            if (!userRepository.existsByPhone("9555555555")) {
                deliveryBoy = User.builder()
                        .name("Rahul Sharma")
                        .phone("9555555555")
                        .role(UserRole.DELIVERY_BOY)
                        .passwordHash(passwordEncoder.encode("delivery123"))
                        .active(true)
                        .mustChangePassword(false)
                        .build();
                deliveryBoy = userRepository.save(deliveryBoy);
                System.out.println("✅ Default delivery boy user 'Rahul Sharma' (9555555555 / delivery123) created successfully");
            } else {
                deliveryBoy = userRepository.findByPhone("9555555555").orElse(null);
            }

            // Seed 5 to 6 deliveries for testing if none exist
            if (deliveryRepository.count() == 0 && deliveryBoy != null && admin != null) {
                System.out.println("🚀 Seeding 6 sample deliveries with real coordinates for route optimization checking...");

                // Seed Areas
                Area area1 = Area.builder().name("Central Delhi").description("Sardar Patel & Central Secretariat Route").build();
                Area area2 = Area.builder().name("Connaught Place").description("Inner & Outer Circle Markets").build();
                area1 = areaRepository.save(area1);
                area2 = areaRepository.save(area2);

                // Customers data details
                // Stop 1: Gol Dak Khana (Central Delhi)
                Customer c1 = Customer.builder()
                        .customerCode("CUST-101")
                        .name("Gupta Kirana Store")
                        .shopName("Gupta Provisions")
                        .phone("9111111111")
                        .area(area1)
                        .latitude(28.6289)
                        .longitude(77.2065)
                        .locationMethod("MANUAL")
                        .totalPending(BigDecimal.valueOf(1500))
                        .active(true)
                        .build();

                // Stop 2: Connaught Place Inner Circle (Connaught Place)
                Customer c2 = Customer.builder()
                        .customerCode("CUST-102")
                        .name("Rajesh Departmental Store")
                        .shopName("Rajesh & Sons")
                        .phone("9222222222")
                        .area(area2)
                        .latitude(28.6304)
                        .longitude(77.2177)
                        .locationMethod("MANUAL")
                        .totalPending(BigDecimal.valueOf(3200))
                        .active(true)
                        .build();

                // Stop 3: Patel Nagar / RML (Central Delhi)
                Customer c3 = Customer.builder()
                        .customerCode("CUST-103")
                        .name("Aggarwal Sweets & Confectionery")
                        .shopName("Aggarwal Sweets")
                        .phone("9333333333")
                        .area(area1)
                        .latitude(28.6200)
                        .longitude(77.2030)
                        .locationMethod("MANUAL")
                        .totalPending(BigDecimal.valueOf(4500))
                        .active(true)
                        .build();

                // Stop 4: India Gate area (Connaught Place)
                Customer c4 = Customer.builder()
                        .customerCode("CUST-104")
                        .name("Sharma Ji Daily Needs")
                        .shopName("Sharma Daily Needs")
                        .phone("9444444444")
                        .area(area2)
                        .latitude(28.6145)
                        .longitude(77.2185)
                        .locationMethod("MANUAL")
                        .totalPending(BigDecimal.valueOf(1800))
                        .active(true)
                        .build();

                // Stop 5: Central Delhi Estate (Central Delhi)
                Customer c5 = Customer.builder()
                        .customerCode("CUST-105")
                        .name("Verma General Store")
                        .shopName("Verma Store")
                        .phone("9555555551")
                        .area(area1)
                        .latitude(28.6139)
                        .longitude(77.2090)
                        .locationMethod("MANUAL")
                        .totalPending(BigDecimal.valueOf(2900))
                        .active(true)
                        .build();

                // Stop 6: No GPS customer for testing fallback
                Customer c6 = Customer.builder()
                        .customerCode("CUST-106")
                        .name("Unknown Location Grocery")
                        .shopName("New Town Provisions")
                        .phone("9666666666")
                        .area(area2)
                        .latitude(null)
                        .longitude(null)
                        .totalPending(BigDecimal.valueOf(5000))
                        .active(true)
                        .build();

                c1 = customerRepository.save(c1);
                c2 = customerRepository.save(c2);
                c3 = customerRepository.save(c3);
                c4 = customerRepository.save(c4);
                c5 = customerRepository.save(c5);
                c6 = customerRepository.save(c6);

                // Create 6 Bills for these customers
                Bill b1 = Bill.builder().billNumber("BILL-9001").customer(c1).subtotal(BigDecimal.valueOf(1500)).grandTotal(BigDecimal.valueOf(1500)).pendingAmount(BigDecimal.valueOf(1500)).paymentMode(PaymentMode.UDHAR).status(BillStatus.CONFIRMED).createdBy(admin).build();
                Bill b2 = Bill.builder().billNumber("BILL-9002").customer(c2).subtotal(BigDecimal.valueOf(3200)).grandTotal(BigDecimal.valueOf(3200)).pendingAmount(BigDecimal.valueOf(3200)).paymentMode(PaymentMode.UDHAR).status(BillStatus.CONFIRMED).createdBy(admin).build();
                Bill b3 = Bill.builder().billNumber("BILL-9003").customer(c3).subtotal(BigDecimal.valueOf(4500)).grandTotal(BigDecimal.valueOf(4500)).pendingAmount(BigDecimal.valueOf(4500)).paymentMode(PaymentMode.UDHAR).status(BillStatus.CONFIRMED).createdBy(admin).build();
                Bill b4 = Bill.builder().billNumber("BILL-9004").customer(c4).subtotal(BigDecimal.valueOf(1800)).grandTotal(BigDecimal.valueOf(1800)).pendingAmount(BigDecimal.valueOf(1800)).paymentMode(PaymentMode.UDHAR).status(BillStatus.CONFIRMED).createdBy(admin).build();
                Bill b5 = Bill.builder().billNumber("BILL-9005").customer(c5).subtotal(BigDecimal.valueOf(2900)).grandTotal(BigDecimal.valueOf(2900)).pendingAmount(BigDecimal.valueOf(2900)).paymentMode(PaymentMode.UDHAR).status(BillStatus.CONFIRMED).createdBy(admin).build();
                Bill b6 = Bill.builder().billNumber("BILL-9006").customer(c6).subtotal(BigDecimal.valueOf(5000)).grandTotal(BigDecimal.valueOf(5000)).pendingAmount(BigDecimal.valueOf(5000)).paymentMode(PaymentMode.UDHAR).status(BillStatus.CONFIRMED).createdBy(admin).build();

                b1 = billRepository.save(b1);
                b2 = billRepository.save(b2);
                b3 = billRepository.save(b3);
                b4 = billRepository.save(b4);
                b5 = billRepository.save(b5);
                b6 = billRepository.save(b6);

                // Create 6 Deliveries assigned to our default deliveryBoy (Rahul Sharma)
                Delivery del1 = Delivery.builder().bill(b1).deliveryBoy(deliveryBoy).type(DeliveryType.SCHEDULED).scheduledDate(LocalDate.now()).status(DeliveryStatus.PENDING).build();
                Delivery del2 = Delivery.builder().bill(b2).deliveryBoy(deliveryBoy).type(DeliveryType.SCHEDULED).scheduledDate(LocalDate.now()).status(DeliveryStatus.PACKED).build();
                Delivery del3 = Delivery.builder().bill(b3).deliveryBoy(deliveryBoy).type(DeliveryType.SCHEDULED).scheduledDate(LocalDate.now()).status(DeliveryStatus.PENDING).build();
                Delivery del4 = Delivery.builder().bill(b4).deliveryBoy(deliveryBoy).type(DeliveryType.SCHEDULED).scheduledDate(LocalDate.now()).status(DeliveryStatus.PACKED).build();
                Delivery del5 = Delivery.builder().bill(b5).deliveryBoy(deliveryBoy).type(DeliveryType.SCHEDULED).scheduledDate(LocalDate.now()).status(DeliveryStatus.PENDING).build();
                Delivery del6 = Delivery.builder().bill(b6).deliveryBoy(deliveryBoy).type(DeliveryType.SCHEDULED).scheduledDate(LocalDate.now()).status(DeliveryStatus.PENDING).build();

                deliveryRepository.save(del1);
                deliveryRepository.save(del2);
                deliveryRepository.save(del3);
                deliveryRepository.save(del4);
                deliveryRepository.save(del5);
                deliveryRepository.save(del6);

                System.out.println("✅ Successfully seeded 6 check deliveries to 'Rahul Sharma' (9555555555 / delivery123)!");
            }
        }
    }
}