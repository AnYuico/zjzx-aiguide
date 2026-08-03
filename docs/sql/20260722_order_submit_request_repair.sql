-- Repair for environments where the order idempotency table from
-- 20260711_inventory_reservation.sql was not created.
-- Safe to run repeatedly against db_zjzx.

CREATE TABLE IF NOT EXISTS order_submit_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-processing 1-success 2-failed',
    order_id BIGINT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_submit_request_id(request_id),
    UNIQUE KEY uk_order_submit_order_no(order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SHOW TABLES LIKE 'order_submit_request';
SHOW CREATE TABLE order_submit_request;
