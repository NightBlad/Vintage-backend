# Backend Implementation: Complete Product CRUD with Hierarchical Categories

## Summary of Changes

This implementation adds comprehensive product management capabilities with support for hierarchical categories (main + sub), improved file handling, standardized API responses, and global exception handling.

---

## Phase 1: Entity Layer Enhancements

### 1.1 Product Entity (`entity/Product.java`)

**Added Fields:**
- `@ManyToOne mainCategory` - Reference to main (parent) category
- `@ManyToOne subCategory` - Reference to sub (child) category
- `@ManyToOne category` - Kept for backward compatibility

**Database Schema:**
```sql
ALTER TABLE products ADD COLUMN main_category_id BIGINT;
ALTER TABLE products ADD COLUMN sub_category_id BIGINT;
ALTER TABLE products ADD FOREIGN KEY (main_category_id) REFERENCES categories(id);
ALTER TABLE products ADD FOREIGN KEY (sub_category_id) REFERENCES categories(id);
```

### 1.2 Category Entity

Already supports hierarchical structure:
- `parent_id` field for parent category reference
- `children` OneToMany relationship
- `isMainCategory()` helper method (returns `parent == null`)

---

## Phase 2: Data Transfer Object (DTO)

### Created: `dto/ProductDTO.java`

**Purpose:** Standardized API request/response format

**Key Fields:**
- All product attributes
- `mainCategoryId`, `mainCategoryName`
- `subCategoryId`, `subCategoryName`
- Helper methods: `getCurrentPrice()`, `hasDiscount()`

**Usage:** Controllers return ProductDTO instead of raw Product entities

---

## Phase 3: Service Layer Improvements

### 3.1 FileUploadService (`service/FileUploadService.java`)

**Enhancements:**
1. **Validation**
   - MIME types: `image/jpeg`, `image/png`, `image/webp` (removed GIF)
   - File size limit: 5MB (reduced from 10MB)
   - Returns clear error messages

2. **File Naming**
   - Pattern: `{timestamp}_{uuid}.{ext}`
   - Example: `1645234567890_abc12345.jpg`

3. **Path Management**
   - Saves to: `/uploads/products/`
   - Returns relative path: `products/1645234567890_abc12345.jpg`
   - Frontend builds full URL: `http://localhost:1199/uploads/products/...jpg`

4. **Safe Deletion**
   - `deleteFile(String relativePath)` method
   - Logs errors but doesn't throw exceptions
   - Allows operation to continue if deletion fails

### 3.2 ProductService (`service/ProductService.java`)

**Enabled:** Now annotated with `@Service` and `@Transactional`

**CRUD Operations:**
```java
// Create
Product createProduct(Product product) throws Exception
  - Validates productCode uniqueness
  - Validates category hierarchy
  - Throws IllegalArgumentException on validation failure

// Read
Page<Product> getAllActiveProducts(Pageable pageable)
Page<Product> getProductsByMainCategory(Long mainCategoryId, Pageable pageable)
Page<Product> getProductsBySubCategory(Long subCategoryId, Pageable pageable)
Optional<Product> findByProductCode(String code)

// Update
Product updateProduct(Long id, Product updates) throws Exception
  - Handles partial updates (null fields are skipped)
  - Validates category assignments
  - Prevents duplicate productCode

// Delete
void deleteProduct(Long id) throws Exception          // Soft delete (sets active=false)
void hardDeleteProduct(Long id) throws Exception      // Hard delete + file cleanup

// Conversion
ProductDTO toDTO(Product product)
```

**Error Handling:**
- `IllegalArgumentException` for business logic errors
- `Exception` for database/I/O errors
- All errors propagate to GlobalExceptionHandler

---

## Phase 4: Repository Layer

### Updated: `repository/ProductRepository.java`

**New Query Methods:**
```java
// Hierarchical category queries
Page<Product> findByActiveTrueAndMainCategoryId(Long mainCategoryId, Pageable p)
Page<Product> findByActiveTrueAndSubCategoryId(Long subCategoryId, Pageable p)

// ProductCode lookup
Optional<Product> findByProductCode(String productCode)

// Existing queries (backward compatible)
Page<Product> findByActiveTrueAndCategoryId(Long categoryId, Pageable p)
List<Product> findByActiveTrueAndFeaturedTrue()
Page<Product> searchProducts(String keyword, Pageable p)
```

---

## Phase 5: Configuration Layer

### 5.1 WebMvcConfig (`config/WebMvcConfig.java`) - NEW

**Purpose:** Serve static files (uploaded images) with caching

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
            .addResourceHandler("/uploads/**")
            .addResourceLocations("file:${app.upload.dir}/")
            .setCachePeriod(31536000);  // 1 year cache
    }
}
```

**Result:**
- URL: `http://localhost:1199/uploads/products/1645234567890_abc12345.jpg`
- Files served from: `${user.dir}/uploads/products/`

### 5.2 GlobalExceptionHandler (`config/GlobalExceptionHandler.java`) - NEW

**Purpose:** Centralized exception handling with standardized error responses

**Handlers:**
- `@ExceptionHandler(IllegalArgumentException.class)` → 400 Bad Request
- `@ExceptionHandler(MaxUploadSizeExceededException.class)` → 413 Payload Too Large
- `@ExceptionHandler(RuntimeException.class)` → 400 Bad Request
- `@ExceptionHandler(Exception.class)` → 500 Internal Server Error

**Response Format:**
```json
{
  "status": "ERROR",
  "code": "BAD_REQUEST",
  "message": "Mã sản phẩm đã tồn tại",
  "timestamp": "2026-03-24T10:30:00",
  "httpStatus": 400
}
```

---

## Phase 6: Controller Layer

### 6.1 ApiAdminController (`controller/api/ApiAdminController.java`)

#### Endpoint: POST /api/v1/admin/products
**Content-Type:** `multipart/form-data`

**Parameters:**
```
name (string, required)
productCode (string, required, unique)
description (string, optional)
ingredients (string, optional)
usage (string, optional)
contraindications (string, optional)
price (BigDecimal, required, > 0)
salePrice (BigDecimal, optional)
stockQuantity (int, required, >= 0)
manufacturer (string, optional)
country (string, optional)
dosageForm (string, optional)
packaging (string, optional)
mainCategoryId (Long, optional)
subCategoryId (Long, optional)
prescriptionRequired (boolean, default false)
active (boolean, default true)
featured (boolean, default false)
imageFile (MultipartFile, optional, max 5MB)
```

**Response (201 Created):**
```json
{
  "id": 1,
  "name": "Vitamin C 500mg",
  "productCode": "MED-001",
  "price": 25000,
  "salePrice": null,
  "stockQuantity": 100,
  "imageUrl": "products/1645234567890_abc123.jpg",
  "mainCategoryId": 1,
  "mainCategoryName": "Vitamin & Khoáng chất",
  "subCategoryId": null,
  "subCategoryName": null,
  "active": true,
  "featured": false,
  "createdAt": "2026-03-24T10:30:00",
  "updatedAt": "2026-03-24T10:30:00"
}
```

**Error Cases:**
- 400: `"Mã sản phẩm đã tồn tại"`
- 400: `"Danh mục chính không tồn tại"`
- 400: `"File phải là JPG, PNG, WEBP, max 5MB"`
- 400: `"Tên sản phẩm bắt buộc"`

#### Endpoint: PUT /api/v1/admin/products/{id}
**Content-Type:** `multipart/form-data`

**Parameters:** All optional (partial update supported)

**Features:**
- If new `imageFile` provided:
  - Delete old image from disk
  - Upload new image
  - Update `imageUrl`
- If no new image: Keep existing `imageUrl`
- All other fields can be updated independently

**Response (200 OK):** ProductDTO

#### Endpoint: DELETE /api/v1/admin/products/{id}

**Response (204 No Content)**

**Operation:** Soft delete (sets `active = false`)

**Alternatives:**
- For hard delete: `productService.hardDeleteProduct(id)` (deletes file + record)

### 6.2 ApiProductController (`controller/api/ApiProductController.java`)

#### Endpoint: GET /api/v1/products

**Query Parameters:**
```
page (int, default 0)
size (int, default 12)
mainCategoryId (Long, optional)      - Filter by main category
subCategoryId (Long, optional)        - Filter by sub category
categoryId (Long, optional)           - Backward compatibility
```

**Filtering Priority:**
1. `subCategoryId` (highest priority)
2. `mainCategoryId`
3. `categoryId`
4. All products (if no filter)

**Response:**
```json
{
  "content": [
    { "id": 1, "name": "Product 1", ... }
  ],
  "totalElements": 100,
  "totalPages": 9,
  "currentPage": 0
}
```

---

## Phase 7: Application Configuration

### Updated: `application.properties`

```properties
# File upload - reduced from 10MB to 5MB
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=5MB

# Upload directory (new)
app.upload.dir=${user.dir}/uploads
```

---

## Database Migration

### H2 DDL (Auto-applied with `ddl-auto=update`)

```sql
-- Add new category FK columns to products table
ALTER TABLE products ADD COLUMN main_category_id BIGINT;
ALTER TABLE products ADD COLUMN sub_category_id BIGINT;

-- Add foreign key constraints
ALTER TABLE products ADD CONSTRAINT fk_product_main_cat
  FOREIGN KEY (main_category_id) REFERENCES categories(id);

ALTER TABLE products ADD CONSTRAINT fk_product_sub_cat
  FOREIGN KEY (sub_category_id) REFERENCES categories(id);

-- Indexes for query performance
CREATE INDEX idx_product_main_cat ON products(main_category_id);
CREATE INDEX idx_product_sub_cat ON products(sub_category_id);
```

---

## API Response Format Standardization

### Success Response (2xx)
```json
{
  "id": 1,
  "name": "Product Name",
  "productCode": "MED-001",
  ...
}
```

### Error Response (4xx, 5xx)
```json
{
  "status": "ERROR",
  "code": "BAD_REQUEST",
  "message": "User-friendly error message",
  "timestamp": "2026-03-24T10:30:00",
  "httpStatus": 400
}
```

---

## Testing Checklist

- [ ] POST /api/v1/admin/products (with image)
- [ ] POST /api/v1/admin/products (without image)
- [ ] POST /api/v1/admin/products (duplicate productCode) → 400
- [ ] POST /api/v1/admin/products (invalid category) → 400
- [ ] POST /api/v1/admin/products (invalid file) → 400
- [ ] PUT /api/v1/admin/products/{id} (update image)
- [ ] PUT /api/v1/admin/products/{id} (keep image)
- [ ] PUT /api/v1/admin/products/{id} (invalid productCode) → 400
- [ ] DELETE /api/v1/admin/products/{id} → 204
- [ ] GET /api/v1/products (no filter)
- [ ] GET /api/v1/products?mainCategoryId=1
- [ ] GET /api/v1/products?subCategoryId=5
- [ ] GET /uploads/products/{filename} (verify static file serving)
- [ ] File upload validation (size > 5MB) → 413
- [ ] File upload validation (invalid MIME type) → 400
- [ ] Verify images are deleted on product update
- [ ] Verify category hierarchy validation

---

## Deployment Notes

1. **Directory Permissions**: Ensure `/uploads/` directory is writable by application process
2. **File System**: Use `user.dir` property (Spring Boot root) for cross-platform compatibility
3. **Caching**: Static files cached for 1 year; clear browser cache or use versioning for updates
4. **Backward Compatibility**: Old `categoryId` field still supported; new `mainCategory`/`subCategory` recommended
5. **Database**: Flyway migrations auto-applied; verify column creation in first run

---

## Future Enhancements

1. **Soft Delete Recovery**: Add `DELETE /api/v1/admin/products/{id}/restore` endpoint
2. **Batch Operations**: Multi-product upload/delete
3. **Image Optimization**: Resize/compress on upload
4. **CDN Integration**: Move uploads to S3/cloud storage
5. **Product Versioning**: Track change history
6. **Audit Trail**: Log all admin operations

---

## Key Files Modified/Created

### Created
- `dto/ProductDTO.java`
- `config/WebMvcConfig.java`
- `config/GlobalExceptionHandler.java`

### Modified
- `entity/Product.java` (+3 FK fields)
- `service/FileUploadService.java` (enhanced validation)
- `service/ProductService.java` (enabled + full CRUD)
- `repository/ProductRepository.java` (+2 query methods)
- `controller/api/ApiAdminController.java` (improved POST/PUT/DELETE)
- `controller/api/ApiProductController.java` (category filtering)
- `application.properties` (5MB limit + app.upload.dir)


