package com.tzp.zjzx.manager.service;

import com.tzp.zjzx.ai.contract.event.ProductKnowledgeChangedEvent;
import com.tzp.zjzx.ai.contract.mq.ProductKnowledgeMqConstants;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class ProductKnowledgeOutboxService {

    public static final String CREATED = "CREATED";
    public static final String UPDATED = "UPDATED";
    public static final String DELETED = "DELETED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String AUDIT_CHANGED = "AUDIT_CHANGED";

    private final MqOutboxService outboxService;

    public ProductKnowledgeOutboxService(MqOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    public void enqueue(Long productId, String reason) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("productId must be positive");
        }
        String eventId = "product.knowledge.changed:"
                + productId + ":" + UUID.randomUUID();
        ProductKnowledgeChangedEvent event = new ProductKnowledgeChangedEvent(
                eventId,
                productId,
                reason,
                new Date()
        );
        boolean inserted = outboxService.enqueue(
                eventId,
                ProductKnowledgeMqConstants.CHANGED_EVENT_TYPE,
                ProductKnowledgeMqConstants.EVENT_EXCHANGE,
                ProductKnowledgeMqConstants.CHANGED_ROUTING_KEY,
                event
        );
        if (!inserted) {
            throw new IllegalStateException(
                    "Product knowledge outbox event was not persisted"
            );
        }
    }
}
