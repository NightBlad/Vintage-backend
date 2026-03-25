package com.example.vintage.repository;

import com.example.vintage.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByCode(String code);
    List<Warehouse> findByActiveTrue();
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
}
