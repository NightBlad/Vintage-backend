package com.example.vintage.repository;

import com.example.vintage.entity.StockTransaction;
import com.example.vintage.entity.Product;
import com.example.vintage.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {
    List<StockTransaction> findByProduct(Product product);
    List<StockTransaction> findByWarehouse(Warehouse warehouse);
    List<StockTransaction> findByProductAndWarehouse(Product product, Warehouse warehouse);
    List<StockTransaction> findByProductIdOrderByCreatedAtDesc(Long productId);
    List<StockTransaction> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);

    Page<StockTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<StockTransaction> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
    Page<StockTransaction> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId, Pageable pageable);
    Page<StockTransaction> findByTypeOrderByCreatedAtDesc(StockTransaction.Type type, Pageable pageable);

    @Query("SELECT t FROM StockTransaction t JOIN FETCH t.product JOIN FETCH t.warehouse ORDER BY t.createdAt DESC")
    List<StockTransaction> findAllWithDetails();
}
