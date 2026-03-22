package com.example.vintage.service;

import com.example.vintage.entity.*;
import com.example.vintage.repository.InventoryRepository;
import com.example.vintage.repository.StockTransactionRepository;
import com.example.vintage.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockTransactionRepository stockTransactionRepository;

    public InventoryService(InventoryRepository inventoryRepository,
                            WarehouseRepository warehouseRepository,
                            StockTransactionRepository stockTransactionRepository) {
        this.inventoryRepository = inventoryRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockTransactionRepository = stockTransactionRepository;
    }

    public Warehouse getDefaultWarehouse() {
        // đơn giản: lấy warehouse đầu tiên, sau này có thể cấu hình warehouse mặc định
        return warehouseRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Chưa cấu hình kho (warehouse)"));
    }

    public int getAvailableQuantity(Product product) {
        List<Inventory> inventories = inventoryRepository.findByProduct(product);
        return inventories.stream().mapToInt(inv -> inv.getQuantity() == null ? 0 : inv.getQuantity()).sum();
    }

    @Transactional
    public void importStock(Product product, Warehouse warehouse, int quantity, String referenceCode, String note) {
        adjustInventory(product, warehouse, quantity, StockTransaction.Type.IMPORT, referenceCode, note);
    }

    @Transactional
    public void exportStock(Product product, Warehouse warehouse, int quantity, String referenceCode, String note) {
        adjustInventory(product, warehouse, -quantity, StockTransaction.Type.EXPORT, referenceCode, note);
    }

    @Transactional
    public void adjustInventory(Product product, Warehouse warehouse, int delta,
                                StockTransaction.Type type, String referenceCode, String note) {
        Inventory inventory = inventoryRepository.findByProductAndWarehouse(product, warehouse)
                .orElseGet(() -> {
                    Inventory inv = new Inventory();
                    inv.setProduct(product);
                    inv.setWarehouse(warehouse);
                    inv.setQuantity(0);
                    return inv;
                });

        int current = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
        int updated = current + delta;
        if (updated < 0) {
            throw new IllegalArgumentException("Số lượng tồn kho không đủ");
        }
        inventory.setQuantity(updated);
        inventoryRepository.save(inventory);

        StockTransaction tx = new StockTransaction();
        tx.setProduct(product);
        tx.setWarehouse(warehouse);
        tx.setType(type);
        tx.setQuantity(delta);
        tx.setReferenceCode(referenceCode);
        tx.setNote(note);
        stockTransactionRepository.save(tx);
    }
}

