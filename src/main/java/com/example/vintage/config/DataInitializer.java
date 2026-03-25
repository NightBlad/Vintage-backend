package com.example.vintage.config;

import com.example.vintage.entity.*;
import com.example.vintage.repository.*;
import com.example.vintage.service.InventoryService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@Transactional
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final InventoryService inventoryService;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository,
                          CategoryRepository categoryRepository, ProductRepository productRepository,
                          PasswordEncoder passwordEncoder, InventoryService inventoryService) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
        this.inventoryService = inventoryService;
    }

    @Override
    public void run(String... args) {
        initializeRoles();
        initializeUsers();
        initializeCategories();
        migrateExistingCategoriesToHierarchy();
        initializeProducts();
        initializeInventory();
    }

    private void initializeRoles() {
        if (roleRepository.count() == 0) {
            roleRepository.save(new Role(RoleName.ROLE_ADMIN));
            roleRepository.save(new Role(RoleName.ROLE_USER));
            System.out.println("Đã khởi tạo các role: ADMIN và USER");
        }
    }

    private void initializeUsers() {
        if (userRepository.count() == 0) {
            // Tạo admin
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@vintage-pharmacy.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setFullName("Quản trị viên hệ thống");
            admin.setPhone("0123456789");
            admin.setAddress("123 Đường ABC, TP.HCM");

            Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElse(null);
            Set<Role> adminRoles = new HashSet<>();
            adminRoles.add(adminRole);
            admin.setRoles(adminRoles);

            userRepository.save(admin);

            // Tạo user thường
            User user = new User();
            user.setUsername("user");
            user.setEmail("user@vintage-pharmacy.com");
            user.setPassword(passwordEncoder.encode("user123"));
            user.setFullName("Khách hàng mẫu");
            user.setPhone("0987654321");
            user.setAddress("456 Đường DEF, TP.HCM");

            Role userRole = roleRepository.findByName(RoleName.ROLE_USER).orElse(null);
            Set<Role> userRoles = new HashSet<>();
            userRoles.add(userRole);
            user.setRoles(userRoles);

            userRepository.save(user);

            System.out.println("Đã khởi tạo tài khoản:");
            System.out.println("- Admin: admin/admin123");
            System.out.println("- User: user/user123");
        }
    }

    private void initializeCategories() {
        if (categoryRepository.count() == 0) {
            Category timMach = categoryRepository.save(new Category("Tim Mạch", "Sản phẩm hỗ trợ tim mạch và hệ tuần hoàn"));
            Category xuongKhop = categoryRepository.save(new Category("Xương Khớp", "Sản phẩm bổ sung canxi và hỗ trợ xương khớp"));
            Category thanKinh = categoryRepository.save(new Category("Thần Kinh", "Sản phẩm hỗ trợ hệ thần kinh và trí não"));
            Category mat = categoryRepository.save(new Category("Mắt", "Sản phẩm bảo vệ và cải thiện thị lực"));
            Category thaoDuoc = categoryRepository.save(new Category("Thảo Dược", "Sản phẩm từ thảo dược thiên nhiên"));

            Category boTroTim = new Category("Bổ Trợ Tim", "Hỗ trợ chức năng tim mạch");
            boTroTim.setParent(timMach);
            categoryRepository.save(boTroTim);

            Category omega3 = new Category("Omega 3", "Nhóm Omega 3 và dầu cá");
            omega3.setParent(timMach);
            categoryRepository.save(omega3);

            Category canxi = new Category("Canxi", "Bổ sung canxi cho xương");
            canxi.setParent(xuongKhop);
            categoryRepository.save(canxi);

            Category glucosamine = new Category("Glucosamine", "Hỗ trợ sụn khớp");
            glucosamine.setParent(xuongKhop);
            categoryRepository.save(glucosamine);

            Category naoBo = new Category("Não Bộ", "Hỗ trợ trí nhớ và thần kinh");
            naoBo.setParent(thanKinh);
            categoryRepository.save(naoBo);

            Category giamStress = new Category("Giảm Stress", "Hỗ trợ giấc ngủ và giảm stress");
            giamStress.setParent(thanKinh);
            categoryRepository.save(giamStress);

            Category thiLuc = new Category("Thị Lực", "Hỗ trợ bảo vệ mắt");
            thiLuc.setParent(mat);
            categoryRepository.save(thiLuc);

            Category moimat = new Category("Mỏi Mắt", "Hỗ trợ giảm mỏi mắt");
            moimat.setParent(mat);
            categoryRepository.save(moimat);

            Category boGan = new Category("Bổ Gan", "Hỗ trợ chức năng gan từ thảo dược");
            boGan.setParent(thaoDuoc);
            categoryRepository.save(boGan);

            System.out.println("Đã khởi tạo danh mục chính và danh mục phụ");
        }
    }

    private void migrateExistingCategoriesToHierarchy() {
        List<Category> rootCategories = categoryRepository.findAll().stream()
                .filter(category -> category.getParent() == null)
                .toList();

        if (rootCategories.isEmpty()) {
            return;
        }

        Map<String, String> defaultSubCategoryNames = Map.of(
                "Tim Mạch", "Khác - Tim Mạch",
                "Xương Khớp", "Khác - Xương Khớp",
                "Thần Kinh", "Khác - Thần Kinh",
                "Mắt", "Khác - Mắt",
                "Thảo Dược", "Khác - Thảo Dược"
        );

        // Ensure known subcategories exist for major roots used by current product catalog.
        ensureSubCategory("Tim Mạch", "Bổ Trợ Tim", "Hỗ trợ chức năng tim mạch");
        ensureSubCategory("Tim Mạch", "Omega 3", "Nhóm Omega 3 và dầu cá");
        ensureSubCategory("Xương Khớp", "Canxi", "Bổ sung canxi cho xương");
        ensureSubCategory("Xương Khớp", "Glucosamine", "Hỗ trợ sụn khớp");
        ensureSubCategory("Thần Kinh", "Não Bộ", "Hỗ trợ trí nhớ và thần kinh");
        ensureSubCategory("Thần Kinh", "Giảm Stress", "Hỗ trợ giấc ngủ và giảm stress");
        ensureSubCategory("Mắt", "Thị Lực", "Hỗ trợ bảo vệ mắt");
        ensureSubCategory("Mắt", "Mỏi Mắt", "Hỗ trợ giảm mỏi mắt");
        ensureSubCategory("Thảo Dược", "Bổ Gan", "Hỗ trợ chức năng gan từ thảo dược");

        List<Product> productsToUpdate = new ArrayList<>();
        for (Product product : productRepository.findAll()) {
            Category category = product.getCategory();
            if (category == null || category.getParent() != null) {
                continue;
            }

            Category targetCategory = determineTargetSubCategory(product, category, defaultSubCategoryNames);
            if (targetCategory != null && !targetCategory.getId().equals(category.getId())) {
                product.setCategory(targetCategory);
                productsToUpdate.add(product);
            }
        }

        if (!productsToUpdate.isEmpty()) {
            productRepository.saveAll(productsToUpdate);
            System.out.println("Đã chuyển dữ liệu sản phẩm cũ sang danh mục phụ");
        }
    }

    private void initializeProducts() {
        if (productRepository.count() == 0) {
            Category omega3 = categoryRepository.findByName("Omega 3").orElse(null);
            Category boTroTim = categoryRepository.findByName("Bổ Trợ Tim").orElse(null);
            Category canxi = categoryRepository.findByName("Canxi").orElse(null);
            Category glucosamine = categoryRepository.findByName("Glucosamine").orElse(null);
            Category naoBo = categoryRepository.findByName("Não Bộ").orElse(null);
            Category giamStress = categoryRepository.findByName("Giảm Stress").orElse(null);
            Category thiLuc = categoryRepository.findByName("Thị Lực").orElse(null);
            Category moiMat = categoryRepository.findByName("Mỏi Mắt").orElse(null);

            Product[] products = {
                // Tim Mạch
                createProduct("Omega-3 Fish Oil", "OMG001", new BigDecimal("299000"), 100,
                        "Dầu cá Omega-3 hỗ trợ tim mạch và não bộ",
                        "EPA 180mg, DHA 120mg, Vitamin E",
                        "Uống 1-2 viên/ngày sau bữa ăn",
                        "Người dị ứng hải sản",
                        "Nature Made", "Mỹ", "Viên nang mềm", "Hộp 100 viên",
                        omega3, true),

                createProduct("CoQ10 100mg", "COQ001", new BigDecimal("459000"), 80,
                        "Coenzyme Q10 hỗ trợ năng lượng tim và chống oxy hóa",
                        "Coenzyme Q10 100mg",
                        "Uống 1 viên/ngày với bữa ăn",
                        "Phụ nữ có thai, cho con bú",
                        "NOW Foods", "Mỹ", "Viên nang", "Hộp 60 viên",
                        boTroTim, true),

                // Xương Khớp
                createProduct("Calcium + Vitamin D3", "CAL001", new BigDecimal("189000"), 150,
                        "Bổ sung canxi và vitamin D3 cho xương chắc khỏe",
                        "Calcium Carbonate 600mg, Vitamin D3 400IU",
                        "Uống 1-2 viên/ngày với bữa ăn",
                        "Sỏi thận, tăng canxi máu",
                        "Kirkland", "Mỹ", "Viên nén", "Hộp 120 viên",
                        canxi, false),

                createProduct("Glucosamine + Chondroitin", "GLU001", new BigDecimal("359000"), 90,
                        "Hỗ trợ sụn khớp và giảm đau khớp",
                        "Glucosamine 500mg, Chondroitin 400mg, MSM 300mg",
                        "Uống 2 viên/ngày chia 2 lần",
                        "Dị ứng tôm cua",
                        "Schiff", "Mỹ", "Viên nang", "Hộp 80 viên",
                        glucosamine, true),

                // Thần Kinh
                createProduct("Ginkgo Biloba", "GIN001", new BigDecimal("249000"), 120,
                        "Hỗ trợ tuần hoàn não bộ và cải thiện trí nhớ",
                        "Ginkgo Biloba Extract 120mg",
                        "Uống 1 viên/ngày trước bữa ăn",
                        "Rối loạn đông máu, uống thuốc chống đông",
                        "Nature's Bounty", "Mỹ", "Viên nang", "Hộp 100 viên",
                        naoBo, false),

                createProduct("Magnesium Glycinate", "MAG001", new BigDecimal("319000"), 100,
                        "Magie hỗ trợ thần kinh, giảm stress và cải thiện giấc ngủ",
                        "Magnesium Glycinate 400mg",
                        "Uống 1-2 viên trước khi ngủ",
                        "Suy thận nặng",
                        "Doctor's Best", "Mỹ", "Viên nang", "Hộp 120 viên",
                        giamStress, true),

                // Mắt
                createProduct("Lutein + Zeaxanthin", "LUT001", new BigDecimal("389000"), 70,
                        "Bảo vệ mắt khỏi ánh sáng xanh và thoái hóa điểm vàng",
                        "Lutein 20mg, Zeaxanthin 4mg, Vitamin A, C, E",
                        "Uống 1 viên/ngày với bữa ăn",
                        "Phụ nữ có thai đang cho con bú",
                        "Bausch + Lomb", "Mỹ", "Viên nang mềm", "Hộp 60 viên",
                        thiLuc, true),

                createProduct("Bilberry Extract", "BIL001", new BigDecimal("279000"), 85,
                        "Chiết xuất việt quất hỗ trợ thị lực và giảm mỏi mắt",
                        "Bilberry Extract 160mg, Anthocyanins 25%",
                        "Uống 1 viên 2 lần/ngày",
                        "Không có",
                        "Swanson", "Mỹ", "Viên nang", "Hộp 90 viên",
                        moiMat, false)
            };

            for (Product product : products) {
                productRepository.save(product);
            }

            System.out.println("Đã khởi tạo sản phẩm mẫu");
        }
    }

    private Product createProduct(String name, String code, BigDecimal price, int stock,
                                String description, String ingredients, String usage,
                                String contraindications, String manufacturer, String country,
                                String dosageForm, String packaging, Category category, boolean featured) {
        Product product = new Product();
        product.setName(name);
        product.setProductCode(code);
        product.setPrice(price);
        product.setStockQuantity(stock);
        product.setDescription(description);
        product.setIngredients(ingredients);
        product.setUsage(usage);
        product.setContraindications(contraindications);
        product.setManufacturer(manufacturer);
        product.setCountry(country);
        product.setDosageForm(dosageForm);
        product.setPackaging(packaging);
        product.setCategory(category);
        product.setFeatured(featured);
        product.setExpiryDate(LocalDate.now().plusYears(2));
        product.setManufacturingDate(LocalDate.now().minusMonths(3));
        return product;
    }

    private Category ensureSubCategory(String mainCategoryName, String subCategoryName, String description) {
        Category mainCategory = categoryRepository.findByName(mainCategoryName).orElse(null);
        if (mainCategory == null) {
            mainCategory = categoryRepository.save(new Category(mainCategoryName, description));
        }

        Category existingSubCategory = categoryRepository.findByName(subCategoryName).orElse(null);
        if (existingSubCategory != null) {
            if (existingSubCategory.getParent() == null) {
                existingSubCategory.setParent(mainCategory);
                existingSubCategory.setDescription(description);
                return categoryRepository.save(existingSubCategory);
            }
            return existingSubCategory;
        }

        Category subCategory = new Category(subCategoryName, description);
        subCategory.setParent(mainCategory);
        return categoryRepository.save(subCategory);
    }

    private Category determineTargetSubCategory(Product product,
                                                Category rootCategory,
                                                Map<String, String> defaultSubCategoryNames) {
        String productCode = product.getProductCode();
        if (productCode == null) {
            return ensureFallbackSubCategory(rootCategory, defaultSubCategoryNames);
        }

        return switch (productCode) {
            case "OMG001" -> categoryRepository.findByName("Omega 3").orElseGet(() -> ensureFallbackSubCategory(rootCategory, defaultSubCategoryNames));
            case "COQ001" -> categoryRepository.findByName("Bổ Trợ Tim").orElseGet(() -> ensureFallbackSubCategory(rootCategory, defaultSubCategoryNames));
            case "CAL001" -> categoryRepository.findByName("Canxi").orElseGet(() -> ensureFallbackSubCategory(rootCategory, defaultSubCategoryNames));
            case "GLU001" -> categoryRepository.findByName("Glucosamine").orElseGet(() -> ensureFallbackSubCategory(rootCategory, defaultSubCategoryNames));
            case "GIN001" -> categoryRepository.findByName("Não Bộ").orElseGet(() -> ensureFallbackSubCategory(rootCategory, defaultSubCategoryNames));
            case "MAG001" -> categoryRepository.findByName("Giảm Stress").orElseGet(() -> ensureFallbackSubCategory(rootCategory, defaultSubCategoryNames));
            case "LUT001" -> categoryRepository.findByName("Thị Lực").orElseGet(() -> ensureFallbackSubCategory(rootCategory, defaultSubCategoryNames));
            case "BIL001" -> categoryRepository.findByName("Mỏi Mắt").orElseGet(() -> ensureFallbackSubCategory(rootCategory, defaultSubCategoryNames));
            default -> ensureFallbackSubCategory(rootCategory, defaultSubCategoryNames);
        };
    }

    private Category ensureFallbackSubCategory(Category rootCategory, Map<String, String> defaultSubCategoryNames) {
        String fallbackName = defaultSubCategoryNames.getOrDefault(rootCategory.getName(), "Khác - " + rootCategory.getName());
        Category existing = categoryRepository.findByName(fallbackName).orElse(null);
        if (existing != null) {
            if (existing.getParent() == null) {
                existing.setParent(rootCategory);
                return categoryRepository.save(existing);
            }
            return existing;
        }

        Category fallback = new Category(fallbackName, "Danh mục phụ mặc định cho " + rootCategory.getName());
        fallback.setParent(rootCategory);
        return categoryRepository.save(fallback);
    }

    private void initializeInventory() {
        inventoryService.ensureDefaultWarehouse();
        int migrated = inventoryService.migrateExistingProductStock();
        if (migrated > 0) {
            System.out.println("Đã chuyển đổi tồn kho cho " + migrated + " sản phẩm sang hệ thống kho mới");
        }
    }
}
