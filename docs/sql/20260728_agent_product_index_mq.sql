-- Agent iteration 4: PostgreSQL idempotency log for RabbitMQ incremental indexing.
-- Run against the zjzx_agent PostgreSQL database before enabling the consumer.

CREATE TABLE IF NOT EXISTS agent_mq_consume_log (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(128) NOT NULL,
    event_id VARCHAR(160) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    consume_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_mq_consumer_event
        UNIQUE (consumer_name, event_id)
);

CREATE INDEX IF NOT EXISTS idx_agent_mq_consume_event
    ON agent_mq_consume_log (event_id);
