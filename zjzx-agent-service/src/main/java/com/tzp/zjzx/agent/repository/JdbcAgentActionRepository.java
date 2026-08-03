package com.tzp.zjzx.agent.repository;

import com.tzp.zjzx.agent.action.AgentActionRecord;
import com.tzp.zjzx.agent.action.AgentActionStatus;
import com.tzp.zjzx.agent.action.AgentActionType;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcAgentActionRepository implements AgentActionRepository {

    private static final String SELECT_COLUMNS = """
            confirmation_id, user_id, action_type, payload_json::text,
            payload_hash, summary, status, expires_at, execution_started_at,
            attempt_count, result_json::text, last_error, created_at, updated_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentActionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void create(AgentActionRecord action) {
        jdbcTemplate.update("""
                insert into agent_action_request(
                    confirmation_id, user_id, action_type, payload_json,
                    payload_hash, summary, status, expires_at,
                    attempt_count, created_at, updated_at
                ) values (?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, 0, now(), now())
                """,
                action.confirmationId(),
                action.userId(),
                action.actionType().name(),
                action.payloadJson(),
                action.payloadHash(),
                action.summary(),
                action.status().name(),
                Timestamp.from(action.expiresAt())
        );
    }

    @Override
    public Optional<AgentActionRecord> findForUser(
            String confirmationId,
            Long userId) {
        try {
            AgentActionRecord action = jdbcTemplate.queryForObject(
                    "select " + SELECT_COLUMNS
                            + " from agent_action_request"
                            + " where confirmation_id = ? and user_id = ?",
                    this::mapAction,
                    confirmationId,
                    userId
            );
            return Optional.ofNullable(action);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean claimExecution(
            String confirmationId,
            Long userId,
            Instant now,
            Instant staleBefore) {
        return jdbcTemplate.update("""
                update agent_action_request
                set status = 'EXECUTING',
                    execution_started_at = ?,
                    attempt_count = attempt_count + 1,
                    last_error = null,
                    updated_at = now()
                where confirmation_id = ?
                  and user_id = ?
                  and expires_at > ?
                  and (
                    status in ('PENDING', 'FAILED_RETRYABLE')
                    or (
                      status = 'EXECUTING'
                      and execution_started_at < ?
                    )
                  )
                """,
                Timestamp.from(now),
                confirmationId,
                userId,
                Timestamp.from(now),
                Timestamp.from(staleBefore)
        ) == 1;
    }

    @Override
    public boolean markSucceeded(
            String confirmationId,
            Long userId,
            String resultJson) {
        return jdbcTemplate.update("""
                update agent_action_request
                set status = 'SUCCEEDED',
                    result_json = cast(? as jsonb),
                    last_error = null,
                    updated_at = now()
                where confirmation_id = ?
                  and user_id = ?
                  and status = 'EXECUTING'
                """,
                resultJson,
                confirmationId,
                userId
        ) == 1;
    }

    @Override
    public boolean markRetryable(
            String confirmationId,
            Long userId,
            String lastError) {
        return jdbcTemplate.update("""
                update agent_action_request
                set status = 'FAILED_RETRYABLE',
                    last_error = ?,
                    updated_at = now()
                where confirmation_id = ?
                  and user_id = ?
                  and status = 'EXECUTING'
                """,
                truncate(lastError, 500),
                confirmationId,
                userId
        ) == 1;
    }

    @Override
    public boolean rejectPending(
            String confirmationId,
            Long userId,
            String reason) {
        return jdbcTemplate.update("""
                update agent_action_request
                set status = 'REJECTED',
                    last_error = ?,
                    updated_at = now()
                where confirmation_id = ?
                  and user_id = ?
                  and status in ('PENDING', 'FAILED_RETRYABLE')
                """,
                truncate(reason, 500),
                confirmationId,
                userId
        ) == 1;
    }

    @Override
    public boolean markExecutionRejected(
            String confirmationId,
            Long userId,
            String reason) {
        return jdbcTemplate.update("""
                update agent_action_request
                set status = 'REJECTED',
                    last_error = ?,
                    updated_at = now()
                where confirmation_id = ?
                  and user_id = ?
                  and status = 'EXECUTING'
                """,
                truncate(reason, 500),
                confirmationId,
                userId
        ) == 1;
    }

    @Override
    public boolean expire(String confirmationId, Long userId, Instant now) {
        return jdbcTemplate.update("""
                update agent_action_request
                set status = 'EXPIRED',
                    updated_at = now()
                where confirmation_id = ?
                  and user_id = ?
                  and expires_at <= ?
                  and status in ('PENDING', 'FAILED_RETRYABLE', 'EXECUTING')
                """,
                confirmationId,
                userId,
                Timestamp.from(now)
        ) == 1;
    }

    private AgentActionRecord mapAction(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new AgentActionRecord(
                resultSet.getString("confirmation_id"),
                resultSet.getLong("user_id"),
                AgentActionType.valueOf(resultSet.getString("action_type")),
                resultSet.getString("payload_json"),
                resultSet.getString("payload_hash"),
                resultSet.getString("summary"),
                AgentActionStatus.valueOf(resultSet.getString("status")),
                instant(resultSet, "expires_at"),
                instant(resultSet, "execution_started_at"),
                resultSet.getInt("attempt_count"),
                resultSet.getString("result_json"),
                resultSet.getString("last_error"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at")
        );
    }

    private Instant instant(ResultSet resultSet, String column)
            throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }
}
