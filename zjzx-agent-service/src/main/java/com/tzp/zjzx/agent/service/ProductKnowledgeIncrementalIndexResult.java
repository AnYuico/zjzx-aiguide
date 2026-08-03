package com.tzp.zjzx.agent.service;

public record ProductKnowledgeIncrementalIndexResult(
        String eventId,
        Long productId,
        int upsertedCount,
        int deletedCount,
        boolean duplicate
) {

    public static ProductKnowledgeIncrementalIndexResult duplicate(
            String eventId,
            Long productId) {
        return new ProductKnowledgeIncrementalIndexResult(
                eventId,
                productId,
                0,
                0,
                true
        );
    }
}
