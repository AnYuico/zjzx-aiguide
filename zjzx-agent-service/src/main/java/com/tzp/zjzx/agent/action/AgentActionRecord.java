package com.tzp.zjzx.agent.action;

import java.time.Instant;

public record AgentActionRecord(
        String confirmationId,
        Long userId,
        AgentActionType actionType,
        String payloadJson,
        String payloadHash,
        String summary,
        AgentActionStatus status,
        Instant expiresAt,
        Instant executionStartedAt,
        Integer attemptCount,
        String resultJson,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {
}
