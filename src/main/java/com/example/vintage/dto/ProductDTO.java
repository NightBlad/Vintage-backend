package com.example.vintage.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ProductDTO {
    
    private Long id;
    private String name;
    private String productCode;
    private String description;
    private String ingredients;
    private String usage;
    private String contraindications;
    private BigDecimal price;
    private BigDecimal salePrice;
    private Integer stockQuantity;
    private String manufacturer;
    private String country;
    private String dosageForm;
    private String packaging;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    private String imageUrl;
    private List<String> additionalImages;
    private boolean prescriptionRequired;
    private boolean active;
    private boolean featured;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Category information
    private Long mainCategoryId;
    private String mainCategoryName;
    private Long subCategoryId;
    private String subCategoryName;

    // Inventory information
    private Integer totalInventoryStock;
    private List<InventoryDTO> inventoryDetails;

    // Constructors
    public ProductDTO() {}

    public ProductDTO(Long id, String name, String productCode, BigDecimal price, 
                      Integer stockQuantity, String imageUrl, boolean active) {
        this.id = id;
        this.name = name;
        this.productCode = productCode;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.imageUrl = imageUrl;
        this.active = active;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIngredients() { return ingredients; }
    public void setIngredients(String ingredients) { this.ingredients = ingredients; }

    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }

    public String getContraindications() { return contraindications; }
    public void setContraindications(String contraindications) { this.contraindications = contraindications; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getDosageForm() { return dosageForm; }
    public void setDosageForm(String dosageForm) { this.dosageForm = dosageForm; }

    public String getPackaging() { return packaging; }
    public void setPackaging(String packaging) { this.packaging = packaging; }

    public LocalDate getManufacturingDate() { return manufacturingDate; }
    public void setManufacturingDate(LocalDate manufacturingDate) { this.manufacturingDate = manufacturingDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public List<String> getAdditionalImages() { return additionalImages; }
    public void setAdditionalImages(List<String> additionalImages) { this.additionalImages = additionalImages; }

    public boolean isPrescriptionRequired() { return prescriptionRequired; }
    public void setPrescriptionRequired(boolean prescriptionRequired) { this.prescriptionRequired = prescriptionRequired; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getMainCategoryId() { return mainCategoryId; }
    public void setMainCategoryId(Long mainCategoryId) { this.mainCategoryId = mainCategoryId; }

    public String getMainCategoryName() { return mainCategoryName; }
    public void setMainCategoryName(String mainCategoryName) { this.mainCategoryName = mainCategoryName; }

    public Long getSubCategoryId() { return subCategoryId; }
    public void setSubCategoryId(Long subCategoryId) { this.subCategoryId = subCategoryId; }

    public String getSubCategoryName() { return subCategoryName; }
    public void setSubCategoryName(String subCategoryName) { this.subCategoryName = subCategoryName; }

    public Integer getTotalInventoryStock() { return totalInventoryStock; }
    public void setTotalInventoryStock(Integer totalInventoryStock) { this.totalInventoryStock = totalInventoryStock; }

    public List<InventoryDTO> getInventoryDetails() { return inventoryDetails; }
    public void setInventoryDetails(List<InventoryDTO> inventoryDetails) { this.inventoryDetails = inventoryDetails; }

    // Helper methods
    public BigDecimal getCurrentPrice() {
        return salePrice != null && salePrice.compareTo(BigDecimal.ZERO) > 0 ? salePrice : price;
    }

    public boolean hasDiscount() {
        return salePrice != null && salePrice.compareTo(price) < 0;
    }
}

