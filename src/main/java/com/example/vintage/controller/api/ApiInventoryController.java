package com.example.vintage.controller.api;

import com.example.vintage.dto.InventoryDTO;
import com.example.vintage.dto.StockTransactionDTO;
import com.example.vintage.dto.WarehouseDTO;
import com.example.vintage.entity.Product;
import com.example.vintage.entity.Warehouse;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.repository.WarehouseRepository;
import com.example.vintage.service.InventoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/inventory", "/api/v1/inventory"})
public class ApiInventoryController {

    private final InventoryService inventoryService;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    public ApiInventoryController(InventoryService inventoryService,
                                  ProductRepository productRepository,
                                  WarehouseRepository warehouseRepository) {
        this.inventoryService = inventoryService;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
    }

    // ===== FRONTEND ENDPOINTS (khớp với Angular InventoryService) =====

    /**
     * GET /api/v1/inventory
     * Frontend gọi endpoint này để hiển thị bảng tồn kho.
     * Trả về array tất cả sản phẩm kèm thông tin kho.
     */
    @GetMapping
    public ResponseEntity<?> getInventoryList() {
        return ResponseEntity.ok(inventoryService.getInventoryListForFrontend());
    }

    /**
     * GET /api/v1/inventory/status/{productId}
     * Frontend gọi để lấy trạng thái tồn kho 1 sản phẩm.
     */
    @GetMapping("/status/{productId}")
    public ResponseEntity<?> getStockStatus(@PathVariable Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }
        int available = inventoryService.getAvailableQuantity(product);
        String status = available == 0 ? "OUT_OF_STOCK" : available < 10 ? "LOW_STOCK" : "AVAILABLE";
        return ResponseEntity.ok(Map.of(
                "productId", product.getId(),
                "productName", product.getName(),
                "availableQuantity", available,
                "status", status
        ));
    }

    /**
     * POST /api/v1/inventory/transaction
     * Frontend gọi để điều chỉnh tồn kho.
     * Body: { productId, warehouseId, quantityChange, note }
     */
    @PostMapping("/transaction")
    public ResponseEntity<?> handleTransaction(@RequestBody Map<String, Object> body) {
        Long productId = parseLong(body.get("productId"));
        Integer quantityChange = parseInt(body.get("quantityChange"));
        Long warehouseId = parseLong(body.get("warehouseId"));
        String note = body.get("note") != null ? body.get("note").toString() : "";

        if (productId == null || quantityChange == null || quantityChange == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId/quantityChange không hợp lệ"));
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }

        Warehouse warehouse;
        if (warehouseId != null) {
            warehouse = warehouseRepository.findById(warehouseId).orElse(null);
            if (warehouse == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Kho không tồn tại"));
            }
        } else {
            warehouse = inventoryService.getDefaultWarehouse();
        }

        try {
            if (quantityChange > 0) {
                inventoryService.importStock(product, warehouse, quantityChange, null, note);
            } else {
                inventoryService.exportStock(product, warehouse, Math.abs(quantityChange), null, note);
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }

        int available = inventoryService.getAvailableQuantity(product);
        return ResponseEntity.ok(Map.of(
                "message", "Điều chỉnh tồn kho thành công",
                "availableQuantity", available
        ));
    }

    // ===== WAREHOUSE CRUD =====

    @GetMapping("/warehouses")
    public ResponseEntity<?> getAllWarehouses() {
        return ResponseEntity.ok(inventoryService.getAllWarehouses());
    }

    @GetMapping("/warehouses/active")
    public ResponseEntity<?> getActiveWarehouses() {
        return ResponseEntity.ok(inventoryService.getActiveWarehouses());
    }

    @GetMapping("/warehouses/{id}")
    public ResponseEntity<?> getWarehouse(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(inventoryService.getWarehouseById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/warehouses")
    public ResponseEntity<?> createWarehouse(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        String name = body.get("name");
        String address = body.get("address");
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mã kho và tên kho bắt buộc"));
        }
        try {
            WarehouseDTO dto = inventoryService.createWarehouse(code.trim(), name.trim(), address);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/warehouses/{id}")
    public ResponseEntity<?> updateWarehouse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String code = body.get("code") != null ? body.get("code").toString() : null;
            String name = body.get("name") != null ? body.get("name").toString() : null;
            String address = body.get("address") != null ? body.get("address").toString() : null;
            Boolean active = body.get("active") != null ? Boolean.valueOf(body.get("active").toString()) : null;
            return ResponseEntity.ok(inventoryService.updateWarehouse(id, code, name, address, active));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/warehouses/{id}")
    public ResponseEntity<?> deleteWarehouse(@PathVariable Long id) {
        try {
            inventoryService.deleteWarehouse(id);
            return ResponseEntity.ok(Map.of("message", "Đã vô hiệu hóa kho"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ===== INVENTORY OVERVIEW =====

    @GetMapping("/all")
    public ResponseEntity<?> getAllInventory() {
        return ResponseEntity.ok(inventoryService.getAllInventory());
    }

    @GetMapping("/products")
    public ResponseEntity<?> getAllProductsWithInventory() {
        return ResponseEntity.ok(inventoryService.getAllProductInventorySummary());
    }

    @GetMapping("/low-stock")
    public ResponseEntity<?> getLowStock(@RequestParam(defaultValue = "10") int threshold) {
        return ResponseEntity.ok(inventoryService.getLowStockInventory(threshold));
    }

    @PostMapping("/migrate")
    public ResponseEntity<?> migrateExistingStock() {
        inventoryService.ensureDefaultWarehouse();
        int migrated = inventoryService.migrateExistingProductStock();
        return ResponseEntity.ok(Map.of(
                "message", "Đã chuyển đổi tồn kho thành công",
                "migratedProducts", migrated
        ));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getProductInventory(@PathVariable Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }
        List<InventoryDTO> details = inventoryService.getInventoryByProduct(productId);
        int totalAvailable = inventoryService.getAvailableQuantity(product);
        return ResponseEntity.ok(Map.of(
                "productId", product.getId(),
                "productName", product.getName(),
                "productCode", product.getProductCode(),
                "totalAvailableQuantity", totalAvailable,
                "warehouseDetails", details
        ));
    }

    @GetMapping("/warehouse/{warehouseId}/products")
    public ResponseEntity<?> getWarehouseInventory(@PathVariable Long warehouseId) {
        if (!warehouseRepository.existsById(warehouseId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Kho không tồn tại"));
        }
        return ResponseEntity.ok(inventoryService.getInventoryByWarehouse(warehouseId));
    }

    // ===== STOCK OPERATIONS =====

    @PostMapping("/import")
    public ResponseEntity<?> importStock(@RequestBody Map<String, Object> body) {
        Long productId = parseLong(body.get("productId"));
        Integer quantity = parseInt(body.get("quantity"));
        Long warehouseId = parseLong(body.get("warehouseId"));

        if (productId == null || quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId/quantity không hợp lệ"));
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }

        Warehouse warehouse;
        if (warehouseId != null) {
            warehouse = warehouseRepository.findById(warehouseId).orElse(null);
            if (warehouse == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Kho không tồn tại"));
            }
        } else {
            warehouse = inventoryService.getDefaultWarehouse();
        }

        String referenceCode = body.get("referenceCode") != null ? body.get("referenceCode").toString() : null;
        String note = body.get("note") != null ? body.get("note").toString() : null;

        inventoryService.importStock(product, warehouse, quantity, referenceCode, note);

        int available = inventoryService.getAvailableQuantity(product);
        return ResponseEntity.ok(Map.of(
                "message", "Nhập kho thành công",
                "productId", product.getId(),
                "warehouseId", warehouse.getId(),
                "warehouseName", warehouse.getName(),
                "importedQuantity", quantity,
                "totalAvailableQuantity", available
        ));
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportStock(@RequestBody Map<String, Object> body) {
        Long productId = parseLong(body.get("productId"));
        Integer quantity = parseInt(body.get("quantity"));
        Long warehouseId = parseLong(body.get("warehouseId"));

        if (productId == null || quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId/quantity không hợp lệ"));
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }

        Warehouse warehouse;
        if (warehouseId != null) {
            warehouse = warehouseRepository.findById(warehouseId).orElse(null);
            if (warehouse == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Kho không tồn tại"));
            }
        } else {
            warehouse = inventoryService.getDefaultWarehouse();
        }

        String referenceCode = body.get("referenceCode") != null ? body.get("referenceCode").toString() : null;
        String note = body.get("note") != null ? body.get("note").toString() : null;

        try {
            inventoryService.exportStock(product, warehouse, quantity, referenceCode, note);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }

        int available = inventoryService.getAvailableQuantity(product);
        return ResponseEntity.ok(Map.of(
                "message", "Xuất kho thành công",
                "productId", product.getId(),
                "warehouseId", warehouse.getId(),
                "warehouseName", warehouse.getName(),
                "exportedQuantity", quantity,
                "totalAvailableQuantity", available
        ));
    }

    @PostMapping("/adjust")
    public ResponseEntity<?> adjustStock(@RequestBody Map<String, Object> body) {
        Long productId = parseLong(body.get("productId"));
        Integer newQuantity = parseInt(body.get("newQuantity"));
        Long warehouseId = parseLong(body.get("warehouseId"));

        if (productId == null || newQuantity == null || newQuantity < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId/newQuantity không hợp lệ"));
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }

        Warehouse warehouse;
        if (warehouseId != null) {
            warehouse = warehouseRepository.findById(warehouseId).orElse(null);
            if (warehouse == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Kho không tồn tại"));
            }
        } else {
            warehouse = inventoryService.getDefaultWarehouse();
        }

        int currentInWarehouse = inventoryService.getInventoryByProduct(productId).stream()
                .filter(inv -> inv.getWarehouseId().equals(warehouse.getId()))
                .mapToInt(inv -> inv.getQuantity() != null ? inv.getQuantity() : 0)
                .sum();

        int delta = newQuantity - currentInWarehouse;
        String referenceCode = body.get("referenceCode") != null ? body.get("referenceCode").toString() : null;
        String note = body.get("note") != null ? body.get("note").toString() : "Điều chỉnh tồn kho";

        try {
            inventoryService.adjustInventory(product, warehouse, delta,
                    com.example.vintage.entity.StockTransaction.Type.ADJUSTMENT, referenceCode, note);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }

        int available = inventoryService.getAvailableQuantity(product);
        return ResponseEntity.ok(Map.of(
                "message", "Điều chỉnh tồn kho thành công",
                "productId", product.getId(),
                "warehouseId", warehouse.getId(),
                "previousQuantity", currentInWarehouse,
                "newQuantity", newQuantity,
                "totalAvailableQuantity", available
        ));
    }

    // ===== TRANSACTIONS =====

    @GetMapping("/transactions")
    public ResponseEntity<?> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StockTransactionDTO> transactions = inventoryService.getAllTransactions(pageable);
        return ResponseEntity.ok(Map.of(
                "content", transactions.getContent(),
                "totalElements", transactions.getTotalElements(),
                "totalPages", transactions.getTotalPages(),
                "currentPage", transactions.getNumber()
        ));
    }

    @GetMapping("/transactions/product/{productId}")
    public ResponseEntity<?> getProductTransactions(@PathVariable Long productId) {
        if (!productRepository.existsById(productId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }
        return ResponseEntity.ok(inventoryService.getTransactionsByProduct(productId));
    }

    @GetMapping("/transactions/warehouse/{warehouseId}")
    public ResponseEntity<?> getWarehouseTransactions(@PathVariable Long warehouseId) {
        if (!warehouseRepository.existsById(warehouseId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Kho không tồn tại"));
        }
        return ResponseEntity.ok(inventoryService.getTransactionsByWarehouse(warehouseId));
    }

    // ===== SYNC =====

    @PostMapping("/sync")
    public ResponseEntity<?> syncAllProductStock() {
        inventoryService.syncAllProductStock();
        return ResponseEntity.ok(Map.of("message", "Đồng bộ tồn kho sản phẩm thành công"));
    }

    // ===== UTILS =====

    private Long parseLong(Object val) {
        try {
            return val == null ? null : Long.valueOf(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInt(Object val) {
        try {
            return val == null ? null : Integer.valueOf(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
