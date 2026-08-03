package com.tzp.zjzx.ai.contract.dto;

public class ProductKnowledgePageQueryDto {

    private Long afterSkuId;
    private Integer limit;

    public Long getAfterSkuId() {
        return afterSkuId;
    }

    public void setAfterSkuId(Long afterSkuId) {
        this.afterSkuId = afterSkuId;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
