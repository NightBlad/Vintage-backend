package com.example.vintage.service;

import com.example.vintage.dto.InventoryDTO;
import com.example.vintage.dto.ProductDTO;
import com.example.vintage.entity.Category;
import com.example.vintage.entity.Product;
import com.example.vintage.repository.CategoryRepository;
import com.example.vintage.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final FileUploadService fileUploadService;
    private final InventoryService inventoryService;

    public ProductService(ProductRepository productRepository, 
                         CategoryRepository categoryRepository,
                         FileUploadService fileUploadService,
                         InventoryService inventoryService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.fileUploadService = fileUploadService;
        this.inventoryService = inventoryService;
    }

    /**
     * Create new product with category validation.
     * After saving, initializes inventory in the default warehouse.
     */
    public Product createProduct(Product product) {
        if (productRepository.existsByProductCode(product.getProductCode())) {
            throw new IllegalArgumentException("Mã sản phẩm đã tồn tại");
        }

        if (product.getMainCategory() != null) {
            Category mainCat = categoryRepository.findById(product.getMainCategory().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Danh mục chính không tồn tại"));
            if (mainCat.getParent() != null) {
                throw new IllegalArgumentException("Danh mục chính không hợp lệ");
            }
            product.setMainCategory(mainCat);
        }

        if (product.getSubCategory() != null) {
            Category subCat = categoryRepository.findById(product.getSubCategory().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Danh mục phụ không tồn tại"));
            if (subCat.getParent() == null) {
                throw new IllegalArgumentException("Danh mục phụ không hợp lệ");
            }
            if (product.getMainCategory() != null && 
                !subCat.getParent().getId().equals(product.getMainCategory().getId())) {
                throw new IllegalArgumentException("Danh mục phụ không thuộc danh mục chính");
            }
            product.setSubCategory(subCat);
        }

        if (product.getSubCategory() != null) {
            product.setCategory(product.getSubCategory());
        } else if (product.getMainCategory() != null) {
            product.setCategory(product.getMainCategory());
        }

        int initialStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        product.setStockQuantity(0);
        Product saved = productRepository.save(product);

        if (initialStock > 0) {
            inventoryService.initializeProductStock(saved, initialStock);
        }

        return saved;
    }

    /**
     * Update existing product with category validation.
     * Stock changes go through InventoryService.
     */
    public Product updateProduct(Long id, Product updates) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại"));

        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getIngredients() != null) existing.setIngredients(updates.getIngredients());
        if (updates.getUsage() != null) existing.setUsage(updates.getUsage());
        if (updates.getContraindications() != null) existing.setContraindications(updates.getContraindications());
        if (updates.getPrice() != null) existing.setPrice(updates.getPrice());
        if (updates.getSalePrice() != null) existing.setSalePrice(updates.getSalePrice());
        if (updates.getManufacturer() != null) existing.setManufacturer(updates.getManufacturer());
        if (updates.getCountry() != null) existing.setCountry(updates.getCountry());
        if (updates.getDosageForm() != null) existing.setDosageForm(updates.getDosageForm());
        if (updates.getPackaging() != null) existing.setPackaging(updates.getPackaging());
        if (updates.getManufacturingDate() != null) existing.setManufacturingDate(updates.getManufacturingDate());
        if (updates.getExpiryDate() != null) existing.setExpiryDate(updates.getExpiryDate());
        if (updates.getImageUrl() != null) existing.setImageUrl(updates.getImageUrl());
        if (updates.getAdditionalImages() != null) existing.setAdditionalImages(updates.getAdditionalImages());

        existing.setPrescriptionRequired(updates.isPrescriptionRequired());
        existing.setActive(updates.isActive());
        existing.setFeatured(updates.isFeatured());

        if (updates.getProductCode() != null && !updates.getProductCode().equals(existing.getProductCode())) {
            if (productRepository.existsByProductCode(updates.getProductCode())) {
                throw new IllegalArgumentException("Mã sản phẩm đã tồn tại");
            }
            existing.setProductCode(updates.getProductCode());
        }

        if (updates.getMainCategory() != null) {
            Category mainCat = categoryRepository.findById(updates.getMainCategory().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Danh mục chính không tồn tại"));
            if (mainCat.getParent() != null) {
                throw new IllegalArgumentException("Danh mục chính không hợp lệ");
            }
            existing.setMainCategory(mainCat);
        }

        if (updates.getSubCategory() != null) {
            Category subCat = categoryRepository.findById(updates.getSubCategory().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Danh mục phụ không tồn tại"));
            if (subCat.getParent() == null) {
                throw new IllegalArgumentException("Danh mục phụ không hợp lệ");
            }
            if (existing.getMainCategory() != null && !subCat.getParent().getId().equals(existing.getMainCategory().getId())) {
                throw new IllegalArgumentException("Danh mục phụ không thuộc danh mục chính");
            }
            existing.setSubCategory(subCat);
        }

        if (existing.getSubCategory() != null) {
            existing.setCategory(existing.getSubCategory());
        } else if (existing.getMainCategory() != null) {
            existing.setCategory(existing.getMainCategory());
        }

        return productRepository.save(existing);
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại"));
        product.setActive(false);
        productRepository.save(product);
    }

    public void hardDeleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại"));
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            fileUploadService.deleteFile(product.getImageUrl());
        }
        if (product.getAdditionalImages() != null) {
            product.getAdditionalImages().forEach(fileUploadService::deleteFile);
        }
        productRepository.deleteById(id);
    }

    // Read operations
    public Page<Product> getAllActiveProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable);
    }

    public Page<Product> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByActiveTrueAndCategoryId(categoryId, pageable);
    }

    public Page<Product> getProductsByMainCategory(Long mainCategoryId, Pageable pageable) {
        return productRepository.findByActiveTrueAndMainCategoryId(mainCategoryId, pageable);
    }

    public Page<Product> getProductsBySubCategory(Long subCategoryId, Pageable pageable) {
        return productRepository.findByActiveTrueAndSubCategoryId(subCategoryId, pageable);
    }

    public List<Product> getFeaturedProducts() {
        return productRepository.findByActiveTrueAndFeaturedTrue();
    }

    public Page<Product> searchProducts(String keyword, Pageable pageable) {
        return productRepository.searchProducts(keyword, pageable);
    }

    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    public Optional<Product> findByProductCode(String code) {
        return productRepository.findByProductCode(code);
    }

    public List<Product> getLowStockProducts(Integer threshold) {
        return productRepository.findByStockQuantityLessThan(threshold);
    }

    public boolean existsByProductCode(String productCode) {
        return productRepository.existsByProductCode(productCode);
    }

    /**
     * Adjust stock via inventory system. Use for order-related stock changes.
     */
    public void updateStock(Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại"));
        inventoryService.exportStock(product, inventoryService.getDefaultWarehouse(),
                quantity, null, "Trừ kho từ hệ thống");
    }

    /**
     * Restore stock via inventory system. Use for order cancellation.
     */
    public void restoreStock(Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại"));
        inventoryService.importStock(product, inventoryService.getDefaultWarehouse(),
                quantity, null, "Hoàn kho từ hệ thống");
    }

    /**
     * Convert Product entity to ProductDTO with inventory information.
     */
    public ProductDTO toDTO(Product product) {
        if (product == null) return null;

        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setProductCode(product.getProductCode());
        dto.setDescription(product.getDescription());
        dto.setIngredients(product.getIngredients());
        dto.setUsage(product.getUsage());
        dto.setContraindications(product.getContraindications());
        dto.setPrice(product.getPrice());
        dto.setSalePrice(product.getSalePrice());
        dto.setManufacturer(product.getManufacturer());
        dto.setCountry(product.getCountry());
        dto.setDosageForm(product.getDosageForm());
        dto.setPackaging(product.getPackaging());
        dto.setManufacturingDate(product.getManufacturingDate());
        dto.setExpiryDate(product.getExpiryDate());
        dto.setImageUrl(product.getImageUrl());
        dto.setAdditionalImages(product.getAdditionalImages());
        dto.setPrescriptionRequired(product.isPrescriptionRequired());
        dto.setActive(product.isActive());
        dto.setFeatured(product.isFeatured());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());

        int totalInventoryStock = inventoryService.getAvailableQuantity(product);
        dto.setStockQuantity(totalInventoryStock);
        dto.setTotalInventoryStock(totalInventoryStock);

        List<InventoryDTO> inventoryDetails = inventoryService.getInventoryByProduct(product.getId());
        dto.setInventoryDetails(inventoryDetails);

        if (product.getMainCategory() != null) {
            dto.setMainCategoryId(product.getMainCategory().getId());
            dto.setMainCategoryName(product.getMainCategory().getName());
        }
        if (product.getSubCategory() != null) {
            dto.setSubCategoryId(product.getSubCategory().getId());
            dto.setSubCategoryName(product.getSubCategory().getName());
        }

        return dto;
    }
}
