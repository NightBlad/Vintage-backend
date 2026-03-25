package com.example.vintage.repository;

import com.example.vintage.entity.Inventory;
import com.example.vintage.entity.Product;
import com.example.vintage.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductAndWarehouse(Product product, Warehouse warehouse);
    List<Inventory> findByProduct(Product product);
    List<Inventory> findByWarehouse(Warehouse warehouse);
    List<Inventory> findByProductId(Long productId);
    List<Inventory> findByWarehouseId(Long warehouseId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.product = :product")
    int sumQuantityByProduct(@Param("product") Product product);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.product.id = :productId")
    int sumQuantityByProductId(@Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.warehouse = :warehouse")
    int sumQuantityByWarehouse(@Param("warehouse") Warehouse warehouse);

    @Query("SELECT COUNT(DISTINCT i.product) FROM Inventory i WHERE i.warehouse = :warehouse AND i.quantity > 0")
    int countDistinctProductsByWarehouse(@Param("warehouse") Warehouse warehouse);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product JOIN FETCH i.warehouse WHERE i.quantity < :threshold AND i.quantity > 0")
    List<Inventory> findLowStock(@Param("threshold") int threshold);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product JOIN FETCH i.warehouse")
    List<Inventory> findAllWithDetails();

    @Query("SELECT i FROM Inventory i JOIN FETCH i.warehouse WHERE i.product.id = :productId")
    List<Inventory> findByProductIdWithWarehouse(@Param("productId") Long productId);
}
