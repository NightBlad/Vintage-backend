package com.example.vintage.service;

import com.example.vintage.dto.InventoryDTO;
import com.example.vintage.dto.StockTransactionDTO;
import com.example.vintage.dto.WarehouseDTO;
import com.example.vintage.entity.*;
import com.example.vintage.repository.InventoryRepository;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.repository.StockTransactionRepository;
import com.example.vintage.repository.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final ProductRepository productRepository;

    public InventoryService(InventoryRepository inventoryRepository,
                            WarehouseRepository warehouseRepository,
                            StockTransactionRepository stockTransactionRepository,
                            ProductRepository productRepository) {
        this.inventoryRepository = inventoryRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockTransactionRepository = stockTransactionRepository;
        this.productRepository = productRepository;
    }

    // ===== WAREHOUSE =====

    public Warehouse getDefaultWarehouse() {
        return warehouseRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Chưa cấu hình kho (warehouse)"));
    }

    public List<WarehouseDTO> getAllWarehouses() {
        return warehouseRepository.findAll().stream().map(this::toWarehouseDTO).collect(Collectors.toList());
    }

    public List<WarehouseDTO> getActiveWarehouses() {
        return warehouseRepository.findByActiveTrue().stream().map(this::toWarehouseDTO).collect(Collectors.toList());
    }

    public WarehouseDTO getWarehouseById(Long id) {
        Warehouse w = warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kho không tồn tại"));
        return toWarehouseDTO(w);
    }

    @Transactional
    public WarehouseDTO createWarehouse(String code, String name, String address) {
        if (warehouseRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Mã kho đã tồn tại");
        }
        Warehouse w = new Warehouse();
        w.setCode(code);
        w.setName(name);
        w.setAddress(address);
        w.setActive(true);
        return toWarehouseDTO(warehouseRepository.save(w));
    }

    @Transactional
    public WarehouseDTO updateWarehouse(Long id, String code, String name, String address, Boolean active) {
        Warehouse w = warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kho không tồn tại"));
        if (code != null && !code.equals(w.getCode())) {
            if (warehouseRepository.existsByCodeAndIdNot(code, id)) {
                throw new IllegalArgumentException("Mã kho đã tồn tại");
            }
            w.setCode(code);
        }
        if (name != null) w.setName(name);
        if (address != null) w.setAddress(address);
        if (active != null) w.setActive(active);
        return toWarehouseDTO(warehouseRepository.save(w));
    }

    @Transactional
    public void deleteWarehouse(Long id) {
        Warehouse w = warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kho không tồn tại"));
        int totalStock = inventoryRepository.sumQuantityByWarehouse(w);
        if (totalStock > 0) {
            throw new IllegalArgumentException("Không thể xóa kho còn hàng tồn (" + totalStock + " sản phẩm)");
        }
        w.setActive(false);
        warehouseRepository.save(w);
    }

    // ===== INVENTORY STOCK =====

    public int getAvailableQuantity(Product product) {
        return inventoryRepository.sumQuantityByProduct(product);
    }

    public int getAvailableQuantityByProductId(Long productId) {
        return inventoryRepository.sumQuantityByProductId(productId);
    }

    public List<InventoryDTO> getInventoryByProduct(Long productId) {
        return inventoryRepository.findByProductIdWithWarehouse(productId)
                .stream().map(this::toInventoryDTO).collect(Collectors.toList());
    }

    public List<InventoryDTO> getInventoryByWarehouse(Long warehouseId) {
        return inventoryRepository.findByWarehouseId(warehouseId)
                .stream().map(this::toInventoryDTO).collect(Collectors.toList());
    }

    public List<InventoryDTO> getAllInventory() {
        return inventoryRepository.findAllWithDetails()
                .stream().map(this::toInventoryDTO).collect(Collectors.toList());
    }

    public List<InventoryDTO> getLowStockInventory(int threshold) {
        return inventoryRepository.findLowStock(threshold)
                .stream().map(this::toInventoryDTO).collect(Collectors.toList());
    }

    // ===== STOCK OPERATIONS (with Product.stockQuantity sync) =====

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
            throw new IllegalArgumentException("Số lượng tồn kho không đủ (hiện có: " + current + ", yêu cầu xuất: " + Math.abs(delta) + ")");
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

        syncProductStock(product);
    }

    @Transactional
    public void initializeProductStock(Product product, int initialQuantity) {
        if (initialQuantity <= 0) return;
        Warehouse warehouse = getDefaultWarehouse();
        importStock(product, warehouse, initialQuantity, "INIT", "Khởi tạo tồn kho khi tạo sản phẩm");
    }

    /**
     * Sync Product.stockQuantity with the sum of all Inventory quantities for that product.
     * This keeps the denormalized field consistent with the inventory system.
     */
    @Transactional
    public void syncProductStock(Product product) {
        int totalStock = inventoryRepository.sumQuantityByProduct(product);
        product.setStockQuantity(totalStock);
        productRepository.save(product);
    }

    /**
     * Sync all products' stockQuantity with their inventory totals.
     */
    @Transactional
    public void syncAllProductStock() {
        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            int totalStock = inventoryRepository.sumQuantityByProduct(product);
            if (!Integer.valueOf(totalStock).equals(product.getStockQuantity())) {
                product.setStockQuantity(totalStock);
                productRepository.save(product);
            }
        }
    }

    // ===== MIGRATION =====

    /**
     * Ensure a default warehouse exists. Creates one if none found.
     */
    @Transactional
    public Warehouse ensureDefaultWarehouse() {
        List<Warehouse> warehouses = warehouseRepository.findAll();
        if (!warehouses.isEmpty()) {
            return warehouses.get(0);
        }
        Warehouse w = new Warehouse();
        w.setCode("WH-DEFAULT");
        w.setName("Kho Chính");
        w.setAddress("Kho mặc định");
        w.setActive(true);
        return warehouseRepository.save(w);
    }

    /**
     * Migrate existing Product.stockQuantity to Inventory records.
     * For each product that has stockQuantity > 0 but no Inventory record,
     * creates an Inventory record in the default warehouse.
     */
    @Transactional
    public int migrateExistingProductStock() {
        Warehouse warehouse = ensureDefaultWarehouse();
        List<Product> products = productRepository.findAll();
        int migrated = 0;

        for (Product product : products) {
            List<Inventory> existing = inventoryRepository.findByProduct(product);
            if (!existing.isEmpty()) continue;

            int stock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
            if (stock <= 0) {
                Inventory inv = new Inventory();
                inv.setProduct(product);
                inv.setWarehouse(warehouse);
                inv.setQuantity(0);
                inventoryRepository.save(inv);
                migrated++;
                continue;
            }

            Inventory inv = new Inventory();
            inv.setProduct(product);
            inv.setWarehouse(warehouse);
            inv.setQuantity(stock);
            inventoryRepository.save(inv);

            StockTransaction tx = new StockTransaction();
            tx.setProduct(product);
            tx.setWarehouse(warehouse);
            tx.setType(StockTransaction.Type.IMPORT);
            tx.setQuantity(stock);
            tx.setReferenceCode("MIGRATE");
            tx.setNote("Chuyển đổi dữ liệu tồn kho cũ sang hệ thống kho mới");
            stockTransactionRepository.save(tx);

            migrated++;
        }
        return migrated;
    }

    // ===== ALL PRODUCTS WITH INVENTORY STATUS =====

    /**
     * Returns all products with their total inventory stock.
     * Unlike getAllInventory() which only returns existing Inventory records,
     * this method returns EVERY product.
     */
    public List<Map<String, Object>> getAllProductInventorySummary() {
        List<Product> products = productRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Product product : products) {
            int totalStock = inventoryRepository.sumQuantityByProduct(product);
            List<InventoryDTO> details = inventoryRepository.findByProductIdWithWarehouse(product.getId())
                    .stream().map(this::toInventoryDTO).collect(Collectors.toList());

            Map<String, Object> item = new java.util.HashMap<>();
            item.put("productId", product.getId());
            item.put("productName", product.getName());
            item.put("productCode", product.getProductCode());
            item.put("imageUrl", product.getImageUrl());
            item.put("price", product.getPrice());
            item.put("active", product.isActive());
            item.put("totalStock", totalStock);
            item.put("warehouseDetails", details);
            result.add(item);
        }
        return result;
    }

    // ===== FRONTEND DATA =====

    /**
     * Returns inventory data in the format expected by the Angular frontend.
     * Each product appears once per warehouse it has stock in.
     * Products without any inventory record are shown with the default warehouse and quantity 0.
     */
    public List<Map<String, Object>> getInventoryListForFrontend() {
        Warehouse defaultWarehouse = ensureDefaultWarehouse();
        List<Product> allProducts = productRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Product product : allProducts) {
            List<Inventory> inventories = inventoryRepository.findByProduct(product);

            if (inventories.isEmpty()) {
                Map<String, Object> item = new java.util.HashMap<>();
                item.put("productId", product.getId());
                item.put("productName", product.getName());
                item.put("productCode", product.getProductCode());
                item.put("warehouseId", defaultWarehouse.getId());
                item.put("warehouseName", defaultWarehouse.getName());
                item.put("quantity", 0);
                item.put("lastUpdated", product.getUpdatedAt());
                result.add(item);
            } else {
                for (Inventory inv : inventories) {
                    Map<String, Object> item = new java.util.HashMap<>();
                    item.put("productId", product.getId());
                    item.put("productName", product.getName());
                    item.put("productCode", product.getProductCode());
                    item.put("warehouseId", inv.getWarehouse().getId());
                    item.put("warehouseName", inv.getWarehouse().getName());
                    item.put("quantity", inv.getQuantity() != null ? inv.getQuantity() : 0);
                    item.put("lastUpdated", inv.getUpdatedAt() != null ? inv.getUpdatedAt() : product.getUpdatedAt());
                    result.add(item);
                }
            }
        }
        return result;
    }

    // ===== TRANSACTIONS =====

    public List<StockTransactionDTO> getTransactionsByProduct(Long productId) {
        return stockTransactionRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream().map(this::toTransactionDTO).collect(Collectors.toList());
    }

    public List<StockTransactionDTO> getTransactionsByWarehouse(Long warehouseId) {
        return stockTransactionRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId)
                .stream().map(this::toTransactionDTO).collect(Collectors.toList());
    }

    public Page<StockTransactionDTO> getAllTransactions(Pageable pageable) {
        return stockTransactionRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toTransactionDTO);
    }

    // ===== DTO CONVERTERS =====

    public InventoryDTO toInventoryDTO(Inventory inv) {
        InventoryDTO dto = new InventoryDTO();
        dto.setId(inv.getId());
        dto.setQuantity(inv.getQuantity());
        dto.setCreatedAt(inv.getCreatedAt());
        dto.setUpdatedAt(inv.getUpdatedAt());
        if (inv.getProduct() != null) {
            dto.setProductId(inv.getProduct().getId());
            dto.setProductName(inv.getProduct().getName());
            dto.setProductCode(inv.getProduct().getProductCode());
        }
        if (inv.getWarehouse() != null) {
            dto.setWarehouseId(inv.getWarehouse().getId());
            dto.setWarehouseName(inv.getWarehouse().getName());
            dto.setWarehouseCode(inv.getWarehouse().getCode());
        }
        return dto;
    }

    public WarehouseDTO toWarehouseDTO(Warehouse w) {
        WarehouseDTO dto = new WarehouseDTO();
        dto.setId(w.getId());
        dto.setCode(w.getCode());
        dto.setName(w.getName());
        dto.setAddress(w.getAddress());
        dto.setActive(w.isActive());
        dto.setCreatedAt(w.getCreatedAt());
        dto.setUpdatedAt(w.getUpdatedAt());
        dto.setTotalProducts(inventoryRepository.countDistinctProductsByWarehouse(w));
        dto.setTotalQuantity(inventoryRepository.sumQuantityByWarehouse(w));
        return dto;
    }

    public StockTransactionDTO toTransactionDTO(StockTransaction tx) {
        StockTransactionDTO dto = new StockTransactionDTO();
        dto.setId(tx.getId());
        dto.setType(tx.getType().name());
        dto.setQuantity(tx.getQuantity());
        dto.setReferenceCode(tx.getReferenceCode());
        dto.setNote(tx.getNote());
        dto.setCreatedAt(tx.getCreatedAt());
        if (tx.getProduct() != null) {
            dto.setProductId(tx.getProduct().getId());
            dto.setProductName(tx.getProduct().getName());
            dto.setProductCode(tx.getProduct().getProductCode());
        }
        if (tx.getWarehouse() != null) {
            dto.setWarehouseId(tx.getWarehouse().getId());
            dto.setWarehouseName(tx.getWarehouse().getName());
            dto.setWarehouseCode(tx.getWarehouse().getCode());
        }
        return dto;
    }
}
