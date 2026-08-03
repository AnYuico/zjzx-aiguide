package com.tzp.zjzx.ai.contract.dto;

public class ProductGuideQueryDto {

    private String keyword;
    private Integer limit;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
