package com.tzp.zjzx.agent.service;

import java.time.Instant;

public record ProductKnowledgeIndexStatus(
        State state,
        int indexedCount,
        Instant startedAt,
        Instant completedAt,
        String message
) {

    public enum State {
        IDLE,
        RUNNING,
        SUCCEEDED,
        FAILED,
        DISABLED
    }

    public static ProductKnowledgeIndexStatus idle() {
        return new ProductKnowledgeIndexStatus(
                State.IDLE,
                0,
                null,
                null,
                "Product knowledge index has not been rebuilt"
        );
    }
}
