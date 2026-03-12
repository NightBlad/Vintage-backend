package com.example.vintage.repository;

import com.example.vintage.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByActiveTrueAndCategoryId(Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND (p.category.id = :mainCategoryId OR p.category.parent.id = :mainCategoryId)")
    Page<Product> findByActiveTrueAndMainCategoryId(@Param("mainCategoryId") Long mainCategoryId, Pageable pageable);

    List<Product> findByActiveTrueAndFeaturedTrue();

    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.ingredients) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchProducts(@Param("keyword") String keyword, Pageable pageable);

    List<Product> findByStockQuantityLessThan(Integer threshold);

    Boolean existsByProductCode(String productCode);

    // Tìm sản phẩm liên quan cùng danh mục (loại trừ sản phẩm hiện tại)
    Page<Product> findByActiveTrueAndCategoryIdAndIdNot(Long categoryId, Long excludeId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND (p.category.id = :categoryId OR p.category.parent.id = :categoryId) AND p.id <> :excludeId")
    Page<Product> findRelatedProductsByCategoryTree(@Param("categoryId") Long categoryId,
                                                    @Param("excludeId") Long excludeId,
                                                    Pageable pageable);
}
