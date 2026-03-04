package com.example.vintage.controller;

import com.example.vintage.entity.Category;
import com.example.vintage.entity.Product;
import com.example.vintage.repository.CategoryRepository;
import com.example.vintage.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class HomeController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public HomeController(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/")
    public String home(Model model) {
        try {
            List<Product> featuredProducts = productRepository.findByActiveTrueAndFeaturedTrue();
            List<Category> categories = categoryRepository.findByActiveTrueOrderByName();

            model.addAttribute("featuredProducts", featuredProducts);
            model.addAttribute("categories", categories);
        } catch (Exception e) {
            // Nếu có lỗi, vẫn hiển thị trang với danh sách rỗng
            model.addAttribute("featuredProducts", List.of());
            model.addAttribute("categories", List.of());
        }
        return "index";
    }

    @GetMapping("/home")
    public String homePage(Model model) {
        return home(model);
    }

    @GetMapping("/products")
    public String products(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "12") int size,
                          @RequestParam(required = false) Long categoryId,
                          Model model) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Product> products;

            if (categoryId != null) {
                products = productRepository.findByActiveTrueAndCategoryId(categoryId, pageable);
                categoryRepository.findById(categoryId).ifPresent(category ->
                    model.addAttribute("selectedCategory", category));
            } else {
                products = productRepository.findByActiveTrue(pageable);
            }

            model.addAttribute("products", products);
            model.addAttribute("categories", categoryRepository.findByActiveTrueOrderByName());
            model.addAttribute("currentPage", page);
            model.addAttribute("currentCategoryId", categoryId);
        } catch (Exception e) {
            // Nếu có lỗi, hiển thị trang rỗng
            model.addAttribute("products", Page.empty());
            model.addAttribute("categories", List.of());
            model.addAttribute("error", "Có lỗi xảy ra khi tải sản phẩm");
        }
        return "products";
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        try {
            Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));

            if (!product.isActive()) {
                throw new RuntimeException("Sản phẩm không khả dụng");
            }

            // Lấy sản phẩm liên quan cùng danh mục
            List<Product> relatedProducts = List.of();
            if (product.getCategory() != null) {
                Page<Product> relatedPage = productRepository.findByActiveTrueAndCategoryIdAndIdNot(
                    product.getCategory().getId(), id, PageRequest.of(0, 4));
                relatedProducts = relatedPage.getContent();
            }

            model.addAttribute("product", product);
            model.addAttribute("relatedProducts", relatedProducts);
        } catch (Exception e) {
            model.addAttribute("error", "Không tìm thấy sản phẩm hoặc sản phẩm không khả dụng");
            return "error/error";
        }
        return "product-detail";
    }

    @GetMapping("/categories")
    public String categoriesPage(Model model) {
        try {
            List<Category> categories = categoryRepository.findByActiveTrueOrderByName();
            model.addAttribute("categories", categories);
        } catch (Exception e) {
            model.addAttribute("categories", List.of());
            model.addAttribute("error", "Có lỗi xảy ra khi tải danh mục");
        }
        return "categories";
    }

    @GetMapping("/search")
    public String search(@RequestParam String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "12") int size,
                        Model model) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Product> products = productRepository.searchProducts(keyword, pageable);

            model.addAttribute("products", products);
            model.addAttribute("categories", categoryRepository.findByActiveTrueOrderByName());
            model.addAttribute("keyword", keyword);
            model.addAttribute("currentPage", page);
        } catch (Exception e) {
            model.addAttribute("products", Page.empty());
            model.addAttribute("categories", List.of());
            model.addAttribute("error", "Có lỗi xảy ra khi tìm kiếm");
        }
        return "search-results";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }
}
