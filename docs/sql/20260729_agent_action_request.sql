-- Agent iteration 7.2: confirmation state and idempotent action execution.
-- Run against the zjzx_agent PostgreSQL database before enabling personal actions.

CREATE TABLE IF NOT EXISTS agent_action_request (
    confirmation_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    payload_json JSONB NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    execution_started_at TIMESTAMPTZ NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    result_json JSONB NULL,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_agent_action_status CHECK (
        status IN (
            'PENDING',
            'EXECUTING',
            'SUCCEEDED',
            'FAILED_RETRYABLE',
            'EXPIRED',
            'REJECTED'
        )
    ),
    CONSTRAINT ck_agent_action_type CHECK (
        action_type IN ('ADD_TO_CART', 'CANCEL_RECENT_ORDER')
    )
);

CREATE INDEX IF NOT EXISTS idx_agent_action_user_created
    ON agent_action_request (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_action_status_expiry
    ON agent_action_request (status, expires_at);
