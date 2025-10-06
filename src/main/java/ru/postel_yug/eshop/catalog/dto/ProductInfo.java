package ru.postel_yug.eshop.catalog.dto;

public class ProductInfo {
    private Long id;
    private String name;
    private String description;
    private double price;
    private int availableQuantity;

    public ProductInfo() {}

    public ProductInfo(Long id, String name, String description,
                       double price, int availableQuantity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.availableQuantity = availableQuantity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }
}
