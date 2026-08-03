package com.tzp.zjzx.agent.repository;

import com.tzp.zjzx.agent.action.AgentActionRecord;

import java.time.Instant;
import java.util.Optional;

public interface AgentActionRepository {

    void create(AgentActionRecord action);

    Optional<AgentActionRecord> findForUser(String confirmationId, Long userId);

    boolean claimExecution(
            String confirmationId,
            Long userId,
            Instant now,
            Instant staleBefore);

    boolean markSucceeded(
            String confirmationId,
            Long userId,
            String resultJson);

    boolean markRetryable(
            String confirmationId,
            Long userId,
            String lastError);

    boolean rejectPending(
            String confirmationId,
            Long userId,
            String reason);

    boolean markExecutionRejected(
            String confirmationId,
            Long userId,
            String reason);

    boolean expire(String confirmationId, Long userId, Instant now);
}
