package com.tzp.zjzx.ai.contract.dto;

import java.math.BigDecimal;
import java.util.List;

public class AgentOrderCancellationCandidateDto {

    private Integer recentPosition;
    private String orderNo;
    private BigDecimal totalAmount;
    private String createdAt;
    private List<String> productNames;

    public Integer getRecentPosition() {
        return recentPosition;
    }

    public void setRecentPosition(Integer recentPosition) {
        this.recentPosition = recentPosition;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
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

    public List<String> getProductNames() {
        return productNames;
    }

    public void setProductNames(List<String> productNames) {
        this.productNames = productNames;
    }
}
