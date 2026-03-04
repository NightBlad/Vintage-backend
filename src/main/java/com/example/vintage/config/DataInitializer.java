package com.example.vintage.config;

import com.example.vintage.entity.*;
import com.example.vintage.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

// Bật lại DataInitializer để có dữ liệu CRUD
@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository,
                          CategoryRepository categoryRepository, ProductRepository productRepository,
                          PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        initializeRoles();
        initializeUsers();
        initializeCategories();
        initializeProducts();
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
            Category[] categories = {
                new Category("Tim Mạch", "Sản phẩm hỗ trợ tim mạch và hệ tuần hoàn"),
                new Category("Xương Khớp", "Sản phẩm bổ sung canxi và hỗ trợ xương khớp"),
                new Category("Thần Kinh", "Sản phẩm hỗ trợ hệ thần kinh và trí não"),
                new Category("Mắt", "Sản phẩm bảo vệ và cải thiện thị lực"),
                new Category("Thảo Dược", "Sản phẩm từ thảo dược thiên nhiên"),
                new Category("Phụ Nữ", "Sản phẩm chăm sóc sức khỏe phụ nữ"),
                new Category("Nam Giới", "Sản phẩm chăm sóc sức khỏe nam giới"),
                new Category("Trẻ Em", "Sản phẩm dinh dưỡng cho trẻ em"),
                new Category("Người Cao Tuổi", "Sản phẩm dành cho người cao tuổi"),
                new Category("Vitamin Tổng Hợp", "Vitamin và khoáng chất tổng hợp")
            };

            for (Category category : categories) {
                categoryRepository.save(category);
            }

            System.out.println("Đã khởi tạo danh mục sản phẩm");
        }
    }

    private void initializeProducts() {
        if (productRepository.count() == 0) {
            Category timMach = categoryRepository.findByActiveTrueOrderByName().stream()
                    .filter(c -> c.getName().equals("Tim Mạch")).findFirst().orElse(null);
            Category xuongKhop = categoryRepository.findByActiveTrueOrderByName().stream()
                    .filter(c -> c.getName().equals("Xương Khớp")).findFirst().orElse(null);
            Category thanKinh = categoryRepository.findByActiveTrueOrderByName().stream()
                    .filter(c -> c.getName().equals("Thần Kinh")).findFirst().orElse(null);
            Category mat = categoryRepository.findByActiveTrueOrderByName().stream()
                    .filter(c -> c.getName().equals("Mắt")).findFirst().orElse(null);

            Product[] products = {
                // Tim Mạch
                createProduct("Omega-3 Fish Oil", "OMG001", new BigDecimal("299000"), 100,
                        "Dầu cá Omega-3 hỗ trợ tim mạch và não bộ",
                        "EPA 180mg, DHA 120mg, Vitamin E",
                        "Uống 1-2 viên/ngày sau bữa ăn",
                        "Người dị ứng hải sản",
                        "Nature Made", "Mỹ", "Viên nang mềm", "Hộp 100 viên",
                        timMach, true),

                createProduct("CoQ10 100mg", "COQ001", new BigDecimal("459000"), 80,
                        "Coenzyme Q10 hỗ trợ năng lượng tim và chống oxy hóa",
                        "Coenzyme Q10 100mg",
                        "Uống 1 viên/ngày với bữa ăn",
                        "Phụ nữ có thai, cho con bú",
                        "NOW Foods", "Mỹ", "Viên nang", "Hộp 60 viên",
                        timMach, true),

                // Xương Khớp
                createProduct("Calcium + Vitamin D3", "CAL001", new BigDecimal("189000"), 150,
                        "Bổ sung canxi và vitamin D3 cho xương chắc khỏe",
                        "Calcium Carbonate 600mg, Vitamin D3 400IU",
                        "Uống 1-2 viên/ngày với bữa ăn",
                        "Sỏi thận, tăng canxi máu",
                        "Kirkland", "Mỹ", "Viên nén", "Hộp 120 viên",
                        xuongKhop, false),

                createProduct("Glucosamine + Chondroitin", "GLU001", new BigDecimal("359000"), 90,
                        "Hỗ trợ sụn khớp và giảm đau khớp",
                        "Glucosamine 500mg, Chondroitin 400mg, MSM 300mg",
                        "Uống 2 viên/ngày chia 2 lần",
                        "Dị ứng tôm cua",
                        "Schiff", "Mỹ", "Viên nang", "Hộp 80 viên",
                        xuongKhop, true),

                // Thần Kinh
                createProduct("Ginkgo Biloba", "GIN001", new BigDecimal("249000"), 120,
                        "Hỗ trợ tuần hoàn não bộ và cải thiện trí nhớ",
                        "Ginkgo Biloba Extract 120mg",
                        "Uống 1 viên/ngày trước bữa ăn",
                        "Rối loạn đông máu, uống thuốc chống đông",
                        "Nature's Bounty", "Mỹ", "Viên nang", "Hộp 100 viên",
                        thanKinh, false),

                createProduct("Magnesium Glycinate", "MAG001", new BigDecimal("319000"), 100,
                        "Magie hỗ trợ thần kinh, giảm stress và cải thiện giấc ngủ",
                        "Magnesium Glycinate 400mg",
                        "Uống 1-2 viên trước khi ngủ",
                        "Suy thận nặng",
                        "Doctor's Best", "Mỹ", "Viên nang", "Hộp 120 viên",
                        thanKinh, true),

                // Mắt
                createProduct("Lutein + Zeaxanthin", "LUT001", new BigDecimal("389000"), 70,
                        "Bảo vệ mắt khỏi ánh sáng xanh và thoái hóa điểm vàng",
                        "Lutein 20mg, Zeaxanthin 4mg, Vitamin A, C, E",
                        "Uống 1 viên/ngày với bữa ăn",
                        "Phụ nữ có thai đang cho con bú",
                        "Bausch + Lomb", "Mỹ", "Viên nang mềm", "Hộp 60 viên",
                        mat, true),

                createProduct("Bilberry Extract", "BIL001", new BigDecimal("279000"), 85,
                        "Chiết xuất việt quất hỗ trợ thị lực và giảm mỏi mắt",
                        "Bilberry Extract 160mg, Anthocyanins 25%",
                        "Uống 1 viên 2 lần/ngày",
                        "Không có",
                        "Swanson", "Mỹ", "Viên nang", "Hộp 90 viên",
                        mat, false)
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
}
