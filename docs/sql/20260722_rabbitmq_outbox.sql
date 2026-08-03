-- RabbitMQ v1: transactional outbox, consumer idempotency and reconciliation tasks.
-- Apply once to db_zjzx before starting the MQ-enabled services.

CREATE TABLE mq_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(128) NOT NULL,
    producer VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    exchange_name VARCHAR(128) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload LONGTEXT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-pending 1-sent 2-dead',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NOT NULL,
    deliver_at DATETIME NULL COMMENT 'absolute business delivery time for delayed messages',
    sent_time DATETIME NULL,
    last_error VARCHAR(1000) NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mq_outbox_event_id(event_id),
    KEY idx_mq_outbox_publish(producer, status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mq_consume_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    consumer_name VARCHAR(128) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    consume_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mq_consumer_event(consumer_name, event_id),
    KEY idx_mq_consume_event(event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inventory_operation_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    operation_type TINYINT NOT NULL COMMENT '1-confirm 2-release',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-pending 1-success 2-manual',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NOT NULL,
    last_error VARCHAR(500) NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_operation_order_type(order_no, operation_type),
    KEY idx_inventory_operation_retry(status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_exception_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(128) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    trade_no VARCHAR(128) NULL,
    amount DECIMAL(10, 2) NULL,
    reason VARCHAR(64) NOT NULL COMMENT 'LATE_PAYMENT or AMOUNT_MISMATCH',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-pending 1-resolved',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_exception_event(event_id),
    KEY idx_payment_exception_status(status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The incremental statistics consumer requires one aggregate row per date.
-- Check and merge historical duplicate dates before running this statement.
ALTER TABLE order_statistics
    ADD UNIQUE KEY uk_order_statistics_order_date(order_date);

ALTER TABLE order_info
    ADD KEY idx_order_info_payment(order_status, payment_time);
