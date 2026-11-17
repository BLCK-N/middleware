package com.myrtle.middleware.dtos.response;

public class ProductInitialLimitStatus {
    private String productName;
    private boolean hasPassInitalAmount;

    public ProductInitialLimitStatus() {
    }

    public ProductInitialLimitStatus(String productName, boolean hasPassInitalAmount) {
        this.productName = productName;
        this.hasPassInitalAmount = hasPassInitalAmount;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public boolean isHasPassInitalAmount() {
        return hasPassInitalAmount;
    }

    public void setHasPassInitalAmount(boolean hasPassInitalAmount) {
        this.hasPassInitalAmount = hasPassInitalAmount;
    }
}
