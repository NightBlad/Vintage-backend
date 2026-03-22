package com.example.vintage.controller.api;

import com.example.vintage.entity.Product;
import com.example.vintage.entity.StockTransaction;
import com.example.vintage.entity.Warehouse;
import com.example.vintage.repository.ProductRepository;
import com.example.vintage.repository.StockTransactionRepository;
import com.example.vintage.repository.WarehouseRepository;
import com.example.vintage.service.InventoryService;
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
    private final StockTransactionRepository stockTransactionRepository;

    public ApiInventoryController(InventoryService inventoryService,
                                  ProductRepository productRepository,
                                  WarehouseRepository warehouseRepository,
                                  StockTransactionRepository stockTransactionRepository) {
        this.inventoryService = inventoryService;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockTransactionRepository = stockTransactionRepository;
    }

    // GET /api/inventory/product/{productId}
    // Lấy tồn kho hiện tại của 1 sản phẩm (vì chỉ có 1 kho nên trả về tổng)
    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getProductInventory(@PathVariable Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }
        int available = inventoryService.getAvailableQuantity(product);
        return ResponseEntity.ok(Map.of(
                "productId", product.getId(),
                "productName", product.getName(),
                "availableQuantity", available
        ));
    }

    // POST /api/inventory/import
    // Body: { "productId": 1, "quantity": 10, "referenceCode": "PO001", "note": "Nhập hàng" }
    @PostMapping("/import")
    public ResponseEntity<?> importStock(@RequestBody Map<String, Object> body) {
        Long productId = parseLong(body.get("productId"));
        Integer quantity = parseInt(body.get("quantity"));
        if (productId == null || quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId/quantity không hợp lệ"));
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }

        Warehouse warehouse = inventoryService.getDefaultWarehouse();
        String referenceCode = body.get("referenceCode") != null ? body.get("referenceCode").toString() : null;
        String note = body.get("note") != null ? body.get("note").toString() : null;

        inventoryService.importStock(product, warehouse, quantity, referenceCode, note);

        int available = inventoryService.getAvailableQuantity(product);
        return ResponseEntity.ok(Map.of(
                "message", "Nhập kho thành công",
                "availableQuantity", available
        ));
    }

    // POST /api/inventory/export
    // Body: { "productId": 1, "quantity": 2, "referenceCode": "SO001", "note": "Xuất bán lẻ" }
    @PostMapping("/export")
    public ResponseEntity<?> exportStock(@RequestBody Map<String, Object> body) {
        Long productId = parseLong(body.get("productId"));
        Integer quantity = parseInt(body.get("quantity"));
        if (productId == null || quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId/quantity không hợp lệ"));
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }

        Warehouse warehouse = inventoryService.getDefaultWarehouse();
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
                "availableQuantity", available
        ));
    }

    // GET /api/inventory/transactions/product/{productId}
    // Lịch sử nhập xuất 1 sản phẩm trong kho duy nhất
    @GetMapping("/transactions/product/{productId}")
    public ResponseEntity<?> getProductTransactions(@PathVariable Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sản phẩm không tồn tại"));
        }
        Warehouse warehouse = inventoryService.getDefaultWarehouse();
        List<StockTransaction> list = stockTransactionRepository.findByProductAndWarehouse(product, warehouse);
        return ResponseEntity.ok(list);
    }

    // (Optional) GET /api/inventory/warehouse
    // Vì chỉ có 1 kho nên trả về kho duy nhất hiện có
    @GetMapping("/warehouse")
    public ResponseEntity<?> getWarehouseInfo() {
        List<Warehouse> list = warehouseRepository.findAll();
        if (list.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Chưa cấu hình kho"));
        }
        return ResponseEntity.ok(list.get(0));
    }

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

