package com.tzp.zjzx.ai.contract.event;

import java.util.Date;

public class ProductKnowledgeChangedEvent {

    private String eventId;
    private Long productId;
    private String reason;
    private Date changedAt;

    public ProductKnowledgeChangedEvent() {
    }

    public ProductKnowledgeChangedEvent(String eventId,
                                        Long productId,
                                        String reason,
                                        Date changedAt) {
        this.eventId = eventId;
        this.productId = productId;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Date getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Date changedAt) {
        this.changedAt = changedAt;
    }
}
