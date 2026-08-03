-- Seckill v1 schema.
-- The activity quota is independent from product_sku.stock_num. Redis is the
-- admission counter, while these MySQL columns remain the final authority.

CREATE TABLE IF NOT EXISTS seckill_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-draft 1-preheating 2-published 3-ending 4-ended',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_seckill_activity_public (status, start_time, end_time),
    CONSTRAINT chk_seckill_activity_time CHECK (end_time > start_time),
    CONSTRAINT chk_seckill_activity_status CHECK (status IN (0, 1, 2, 3, 4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS seckill_sku (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    seckill_price DECIMAL(10, 2) NOT NULL,
    total_stock INT NOT NULL,
    available_stock INT NOT NULL,
    limit_per_user INT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-draft 1-active 2-ended',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_seckill_sku_activity_sku (activity_id, sku_id),
    KEY idx_seckill_sku_activity_status (activity_id, status),
    CONSTRAINT chk_seckill_sku_price CHECK (seckill_price >= 0),
    CONSTRAINT chk_seckill_sku_stock CHECK (
        total_stock >= 0 AND available_stock >= 0 AND available_stock <= total_stock
    ),
    CONSTRAINT chk_seckill_sku_limit CHECK (limit_per_user = 1),
    CONSTRAINT chk_seckill_sku_status CHECK (status IN (0, 1, 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS seckill_order_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    activity_id BIGINT NOT NULL,
    seckill_sku_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_address_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    order_id BIGINT DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-queued 1-processing 2-success 3-failed 4-cancelled',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fail_reason VARCHAR(500) DEFAULT NULL,
    stock_returned TINYINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_seckill_request_id (request_id),
    UNIQUE KEY uk_seckill_request_order_no (order_no),
    UNIQUE KEY uk_seckill_request_user_sku (activity_id, user_id, sku_id),
    KEY idx_seckill_request_retry (status, next_retry_time),
    KEY idx_seckill_request_order (order_id),
    CONSTRAINT chk_seckill_request_status CHECK (status IN (0, 1, 2, 3, 4)),
    CONSTRAINT chk_seckill_request_retry CHECK (retry_count >= 0),
    CONSTRAINT chk_seckill_request_returned CHECK (stock_returned IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

