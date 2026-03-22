package com.example.vintage.repository;

import com.example.vintage.entity.StockTransaction;
import com.example.vintage.entity.Product;
import com.example.vintage.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {
    List<StockTransaction> findByProduct(Product product);
    List<StockTransaction> findByWarehouse(Warehouse warehouse);
    List<StockTransaction> findByProductAndWarehouse(Product product, Warehouse warehouse);
}

