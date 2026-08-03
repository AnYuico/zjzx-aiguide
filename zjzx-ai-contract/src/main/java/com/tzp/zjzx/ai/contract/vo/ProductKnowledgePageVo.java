package com.tzp.zjzx.ai.contract.vo;

import java.util.List;

public class ProductKnowledgePageVo {

    private List<ProductKnowledgeDocumentVo> items;
    private Long nextCursor;
    private boolean hasMore;

    public ProductKnowledgePageVo() {
    }

    public ProductKnowledgePageVo(List<ProductKnowledgeDocumentVo> items,
                                  Long nextCursor,
                                  boolean hasMore) {
        this.items = items;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<ProductKnowledgeDocumentVo> getItems() {
        return items;
    }

    public void setItems(List<ProductKnowledgeDocumentVo> items) {
        this.items = items;
    }

    public Long getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(Long nextCursor) {
        this.nextCursor = nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
