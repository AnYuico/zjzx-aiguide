package com.tzp.zjzx.ai.contract.vo;

import java.math.BigDecimal;
import java.util.List;

public class AgentOrderSummaryVo {

    private Integer recentPosition;
    private String status;
    private String statusText;
    private BigDecimal totalAmount;
    private String createdAt;
    private String expiresAt;
    private List<String> productNames;

    public Integer getRecentPosition() {
        return recentPosition;
    }

    public void setRecentPosition(Integer recentPosition) {
        this.recentPosition = recentPosition;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public List<String> getProductNames() {
        return productNames;
    }

    public void setProductNames(List<String> productNames) {
        this.productNames = productNames;
    }
}
